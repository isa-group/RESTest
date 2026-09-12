/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.store;

import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.core.store.InteractionStoreException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * Keeps everything a run did in a single SQLite file, and answers questions about it afterwards.
 *
 * <p>SQLite is a database that lives in one ordinary file. That matters here for three reasons: a
 * finished run is one file that can be copied, attached to a bug report or kept as evidence; asking
 * it a question does not mean reading the whole run back into memory, so a run of a hundred thousand
 * requests can still be asked "which of these returned a server error" instantly; and the file can be
 * opened by tools that have never heard of RESTest - the {@code sqlite3} command, a spreadsheet, a
 * notebook - so nobody is locked in to our reports.
 *
 * <p>Each interaction is one row. The handful of facts anybody filters on - which operation, which
 * status code, how it ended - are their own columns, with an index each. The interaction itself sits
 * beside them as a JSON document, which is why adding a field to what RESTest records does not mean a
 * new column and a migration for every run ever stored, and why the stored run stays readable by
 * anything that reads JSON.
 *
 * <p>The file is written in a mode that lets somebody read a run while it is still going on, which is
 * what a live dashboard or an impatient person with {@code sqlite3} will want.
 */
public final class SqliteInteractionStore implements InteractionStore {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS interaction (
                id            TEXT PRIMARY KEY,
                test_case     TEXT    NOT NULL,
                operation     TEXT    NOT NULL,
                method        TEXT    NOT NULL,
                url           TEXT    NOT NULL,
                status_code   INTEGER,
                outcome       TEXT    NOT NULL,
                sent_at       TEXT    NOT NULL,
                elapsed_nanos INTEGER NOT NULL,
                document      TEXT    NOT NULL
            );
            CREATE INDEX IF NOT EXISTS interaction_by_operation ON interaction (operation);
            CREATE INDEX IF NOT EXISTS interaction_by_status ON interaction (status_code);
            CREATE INDEX IF NOT EXISTS interaction_by_outcome ON interaction (outcome);
            """;

    private static final String INSERT = """
            INSERT OR REPLACE INTO interaction
                (id, test_case, operation, method, url, status_code, outcome, sent_at,
                 elapsed_nanos, document)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Oldest first, and the row number settles the order of two requests sent in the same instant. */
    private static final String ORDER = " ORDER BY sent_at, rowid";

    private final ReentrantLock lock = new ReentrantLock();
    private final String describedAs;
    private final Connection connection;
    private boolean closed;

    private SqliteInteractionStore(String url, String describedAs) {
        this.describedAs = describedAs;
        SQLiteConfig settings = new SQLiteConfig();
        // Lets a reader look at the run while it is still being written.
        settings.setJournalMode(SQLiteConfig.JournalMode.WAL);
        settings.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        SQLiteDataSource source = new SQLiteDataSource(settings);
        source.setUrl(url);
        try {
            this.connection = source.getConnection();
            try (Statement schema = connection.createStatement()) {
                for (String statement : SCHEMA.split(";")) {
                    if (!statement.isBlank()) {
                        schema.execute(statement);
                    }
                }
            }
        } catch (SQLException e) {
            throw new InteractionStoreException("The run's evidence could not be opened at "
                    + describedAs + ": " + e.getMessage(), e);
        }
    }

    /**
     * A store kept in the given file, created if it is not there and added to if it is.
     *
     * @param file where the run is kept. Its directory must exist
     * @return the store
     */
    public static SqliteInteractionStore at(Path file) {
        Objects.requireNonNull(file, "file");
        return new SqliteInteractionStore("jdbc:sqlite:" + file.toAbsolutePath(), file.toString());
    }

    /**
     * A store that exists only while the program runs and leaves nothing behind.
     *
     * <p>For a test, or for a program using RESTest as a library that wants to ask questions about a
     * run without keeping it. Everything else behaves identically, which is the point: nothing has to
     * be written differently to be tried this way first.
     *
     * @return the store
     */
    public static SqliteInteractionStore inMemory() {
        return new SqliteInteractionStore("jdbc:sqlite::memory:", "memory");
    }

    @Override
    public void record(Interaction interaction) {
        Objects.requireNonNull(interaction, "interaction");
        String document = Json.write(InteractionDocument.of(interaction));
        lock.lock();
        try (PreparedStatement insert = statement(INSERT)) {
            insert.setString(1, interaction.id().value());
            insert.setString(2, interaction.testCase().id().value());
            insert.setString(3, interaction.testCase().operation().value());
            insert.setString(4, interaction.request().method().name());
            insert.setString(5, interaction.request().url());
            Optional<Integer> status = statusOf(interaction);
            if (status.isPresent()) {
                insert.setInt(6, status.get());
            } else {
                insert.setNull(6, java.sql.Types.INTEGER);
            }
            insert.setString(7, outcomeOf(interaction));
            insert.setString(8, interaction.sentAt().toString());
            insert.setLong(9, interaction.elapsed().toNanos());
            insert.setString(10, document);
            insert.executeUpdate();
        } catch (SQLException e) {
            throw failure("write an interaction to", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Interaction> find(InteractionQuery query) {
        Objects.requireNonNull(query, "query");
        List<Object> values = new ArrayList<>();
        String sql = "SELECT document FROM interaction" + where(query, values) + ORDER
                + query.limit().map(limit -> " LIMIT " + limit).orElse("");
        lock.lock();
        try (PreparedStatement select = statement(sql)) {
            bind(select, values);
            try (ResultSet rows = select.executeQuery()) {
                List<Interaction> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(InteractionDocument.toInteraction(Json.read(rows.getString(1))));
                }
                return List.copyOf(found);
            }
        } catch (SQLException e) {
            throw failure("read interactions from", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>A limit on the question is about how many interactions the caller wants handed back, not
     * about how many there are, so counting ignores it: asking "how many server errors, at most ten"
     * answers how many there were.
     */
    @Override
    public long count(InteractionQuery query) {
        Objects.requireNonNull(query, "query");
        List<Object> values = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM interaction" + where(query, values);
        lock.lock();
        try (PreparedStatement select = statement(sql)) {
            bind(select, values);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw failure("count interactions in", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Interaction> byId(InteractionId id) {
        Objects.requireNonNull(id, "id");
        lock.lock();
        try (PreparedStatement select =
                statement("SELECT document FROM interaction WHERE id = ?")) {
            select.setString(1, id.value());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next()
                        ? Optional.of(
                                InteractionDocument.toInteraction(Json.read(rows.getString(1))))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("read an interaction from", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            connection.close();
        } catch (SQLException e) {
            throw failure("close", e);
        } finally {
            lock.unlock();
        }
    }

    /** Where the run is kept, for a report or an error message that has to name it. */
    @Override
    public String toString() {
        return "SqliteInteractionStore[" + describedAs + "]";
    }

    private String where(InteractionQuery query, List<Object> values) {
        List<String> conditions = new ArrayList<>();
        query.operation().ifPresent(operation -> {
            conditions.add("operation = ?");
            values.add(operation.value());
        });
        query.statusCode().ifPresent(status -> {
            conditions.add("status_code = ?");
            values.add(status);
        });
        query.statusClass().ifPresent(firstDigit -> {
            conditions.add("status_code >= ? AND status_code < ?");
            values.add(firstDigit * 100);
            values.add((firstDigit + 1) * 100);
        });
        query.outcome().ifPresent(outcome -> {
            conditions.add("outcome = ?");
            values.add(name(outcome));
        });
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }

    private PreparedStatement statement(String sql) throws SQLException {
        if (closed) {
            throw new InteractionStoreException(
                    "This store is closed, so " + describedAs + " can no longer be used");
        }
        return connection.prepareStatement(sql);
    }

    private static Optional<Integer> statusOf(Interaction interaction) {
        return switch (interaction.outcome()) {
            case InteractionOutcome.Answered answered ->
                    Optional.of(answered.response().statusCode());
            case InteractionOutcome.MalformedResponse malformed ->
                    malformed.statusLine().map(line -> line.statusCode());
            case InteractionOutcome.TransportFailure ignored -> Optional.empty();
        };
    }

    private static String outcomeOf(Interaction interaction) {
        return switch (interaction.outcome()) {
            case InteractionOutcome.Answered ignored -> name(InteractionQuery.Outcome.ANSWERED);
            case InteractionOutcome.MalformedResponse ignored ->
                    name(InteractionQuery.Outcome.MALFORMED);
            case InteractionOutcome.TransportFailure ignored ->
                    name(InteractionQuery.Outcome.FAILED);
        };
    }

    /** The word stored in the row, kept lowercase so the file reads plainly outside RESTest. */
    private static String name(InteractionQuery.Outcome outcome) {
        return outcome.name().toLowerCase(java.util.Locale.ROOT);
    }

    private InteractionStoreException failure(String what, SQLException cause) {
        return new InteractionStoreException(
                "Could not " + what + " the run's evidence at " + describedAs + ": "
                        + cause.getMessage(), cause);
    }
}
