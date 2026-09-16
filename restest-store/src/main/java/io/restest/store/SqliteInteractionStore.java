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
import io.restest.core.json.InteractionDocument;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.core.store.InteractionStoreException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
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
 * what a live dashboard or an impatient person with {@code sqlite3} will want. Interactions reach the
 * file in groups rather than one at a time, because one at a time cost a third of a run's time. A
 * group is handed over when the next interaction arrives and either the group has grown to a few
 * hundred or a quarter of a second has passed since the first of them.
 *
 * <p>What that means for somebody watching from outside, said exactly, because the obvious reading is
 * wrong: they are always at most one interaction behind. Against a busy API that is a quarter of a
 * second. Against an API answering once a minute it is a minute, because nothing is handed over
 * until the next answer arrives - and when the run stops, the last few wait for the store to be
 * closed. Anybody asking this store a question is never behind at all, because a question hands over
 * whatever is waiting before it answers.
 *
 * <p>What that costs is worth saying plainly. Closing the store hands over everything still waiting.
 * A program stopped where it stands - killed outright rather than closed - loses the group that had
 * not been handed over, and nothing before it. If the file cannot be written to at all, say because
 * the disk is full, the group being handed over is lost and this store says so loudly rather than
 * carrying on quietly; what was handed over earlier is unharmed. No interaction is ever left
 * half-written: the file either has it or it does not.
 *
 * <p>While a run is in progress the file is accompanied by two working files beside it, named after
 * it and ending in {@code -wal} and {@code -shm}; closing the store folds them back in and deletes
 * them. A run that is interrupted before the store is closed leaves all three, and all three together
 * are the evidence - copying only the main file out of an interrupted run would leave the most recent
 * interactions behind.
 */
public final class SqliteInteractionStore implements InteractionStore {

    /**
     * Stamped into every file this version writes. A later version that changes the shape of the
     * table raises it, and this one then says plainly that the run was written by something newer
     * rather than failing later with a complaint about a missing column.
     */
    private static final int LAYOUT = 1;

    /**
     * How big a block the file is written in, chosen once when the file is created.
     *
     * <p>Four times the usual default. A run's interactions are around a kilobyte and a half each, so
     * the usual block holds two of them and wastes the rest; bigger blocks waste proportionally less
     * and made a measured run's file an eighth smaller.
     */
    private static final int PAGE_SIZE = 16_384;

    /**
     * How many interactions may be waiting to reach the file at once.
     *
     * <p>A bound on memory: whatever is waiting is held until it is handed over. Also the reason this
     * is fast - handing interactions over one at a time is what cost a third of a run.
     */
    static final int MOST_INTERACTIONS_WAITING = 256;

    /**
     * And how long any of them may wait once another interaction arrives to notice.
     *
     * <p>This is what keeps somebody reading the run from outside close behind it. Without it, a run
     * against a slow API would hold hundreds of interactions back for as long as it took to collect
     * a full group, which against an API answering once a second is minutes.
     */
    static final Duration LONGEST_ANY_INTERACTION_WAITS = Duration.ofMillis(250);

    private static final List<String> SCHEMA = List.of(
            """
            CREATE TABLE IF NOT EXISTS interaction (
                id            TEXT PRIMARY KEY,
                test_case     TEXT    NOT NULL,
                operation     TEXT    NOT NULL,
                method        TEXT    NOT NULL,
                url           TEXT    NOT NULL,
                status_code   INTEGER,
                outcome       TEXT    NOT NULL,
                sent_at       TEXT    NOT NULL,
                sent_at_nanos INTEGER NOT NULL,
                elapsed_nanos INTEGER NOT NULL,
                document      TEXT    NOT NULL
            )""",
            "CREATE INDEX IF NOT EXISTS interaction_by_operation ON interaction (operation)",
            "CREATE INDEX IF NOT EXISTS interaction_by_status ON interaction (status_code)",
            "CREATE INDEX IF NOT EXISTS interaction_by_outcome ON interaction (outcome)",
            "CREATE INDEX IF NOT EXISTS interaction_in_order ON interaction (sent_at_nanos)");

    private static final String INSERT = """
            INSERT OR REPLACE INTO interaction
                (id, test_case, operation, method, url, status_code, outcome, sent_at,
                 sent_at_nanos, elapsed_nanos, document)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Oldest first.
     *
     * <p>Ordered by the moment the request went out, counted as a number rather than compared as
     * written text. Written out, an instant prints however many decimals it needs - {@code 12:00:00Z}
     * and {@code 12:00:00.5Z} and {@code 12:00:00.123456Z} - and comparing those as text puts them in
     * an order that has nothing to do with time. The row number settles the rare tie.
     */
    private static final String ORDER = " ORDER BY sent_at_nanos, rowid";

    private final ReentrantLock lock = new ReentrantLock();
    private final String describedAs;
    private final Connection connection;

    /** Made once and used for every interaction, rather than built again for each one. */
    private final PreparedStatement insert;

    /** How many interactions have been written but not yet handed over. Guarded by the lock. */
    private int waiting;

    /** When the oldest of them stops being allowed to wait. Guarded by the lock. */
    private long saveDueAt;

    private boolean closed;

    private SqliteInteractionStore(String url, String describedAs) {
        this.describedAs = describedAs;
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl(url);
        Connection opened = null;
        try {
            opened = source.getConnection();
            configure(opened);
            prepare(opened, describedAs);
            // From here on there is always a group of interactions collecting, and handing one over
            // is what puts it in the file. Every setting above has to be in place before this: some
            // of them are refused once a group is open.
            opened.setAutoCommit(false);
            this.insert = opened.prepareStatement(INSERT);
            this.connection = opened;
        } catch (SQLException | RuntimeException e) {
            // Without this the failed connection would be left open and unreachable, and a program
            // that opens one store per API under test would run out of files it is allowed to hold.
            closeQuietly(opened);
            if (e instanceof InteractionStoreException already) {
                throw already;
            }
            throw new InteractionStoreException("The run's evidence could not be opened at "
                    + describedAs + ": " + e.getMessage(), e);
        }
    }

    /**
     * The settings the file is written with, issued one at a time and in this order on purpose.
     *
     * <p>How big a block the file is written in can only be decided while the file is still empty,
     * and the mode that lets a run be read while it is being written is the first thing that puts
     * anything in the file - so the block size has to be settled before it.
     *
     * <p>They are issued here rather than handed to the database driver because the driver applies
     * what it is given in no particular order, so a block size given that way is accepted and then
     * quietly thrown away. There is no complaint and no way to tell from the outside except by
     * reading the finished file.
     *
     * <p>A file that already holds something keeps the block size it was made with, whatever is asked
     * for here. That is how a run started by an older version of RESTest goes on being added to.
     */
    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA page_size = " + PAGE_SIZE);
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    private static void prepare(Connection connection, String describedAs) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int written;
            try (ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
                written = rows.next() ? rows.getInt(1) : 0;
            }
            if (written > LAYOUT) {
                throw new InteractionStoreException("The run at " + describedAs + " was written by "
                        + "a newer version of RESTest (its layout is " + written + ", this version "
                        + "knows " + LAYOUT + "), so it cannot be read here");
            }
            for (String schema : SCHEMA) {
                statement.execute(schema);
            }
            statement.execute("PRAGMA user_version = " + LAYOUT);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException beyondHelp) {
                // Already failing; the original failure is the one worth reporting.
            }
        }
    }

    /**
     * The moment a request went out, as a plain count of nanoseconds, so that rows can be put in the
     * order they happened. Clamped rather than refused for a date far outside any real run: a
     * nonsensical timestamp is not a reason to lose the interaction it belongs to.
     */
    private static long instantAsNumber(java.time.Instant instant) {
        try {
            return Math.addExact(
                    Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
        } catch (ArithmeticException farFuture) {
            return instant.isAfter(java.time.Instant.EPOCH) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /**
     * A store kept in the given file, created if it is not there and added to if it is.
     *
     * <p>How big a block the file is written in is decided when the file is created and never
     * changes, so a run started by an older version of RESTest goes on using the blocks it was made
     * with.
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
        String document = JsonText.write(InteractionDocument.of(interaction));
        lock.lock();
        try {
            ensureOpen();
            // The same statement is used for every interaction, so every value is set every time.
            // One left out would quietly keep whatever the interaction before it put there.
            insert.setString(1, interaction.id().value());
            insert.setString(2, interaction.testCase().id().value());
            insert.setString(3, interaction.testCase().operation().value());
            insert.setString(4, interaction.request().method().name());
            insert.setString(5, interaction.request().url());
            Optional<Integer> status = interaction.statusCode();
            if (status.isPresent()) {
                insert.setInt(6, status.get());
            } else {
                insert.setNull(6, java.sql.Types.INTEGER);
            }
            insert.setString(7, outcomeOf(interaction));
            insert.setString(8, interaction.sentAt().toString());
            insert.setLong(9, instantAsNumber(interaction.sentAt()));
            insert.setLong(10, interaction.elapsed().toNanos());
            insert.setString(11, document);
            insert.executeUpdate();
            waiting++;
            if (waiting == 1) {
                // The clock starts at the first of a group, not at the last hand-over, so no single
                // interaction can wait longer than it is allowed to however slowly they arrive.
                saveDueAt = System.nanoTime() + LONGEST_ANY_INTERACTION_WAITS.toNanos();
            }
            // Subtracted rather than compared, which is what makes it right when the clock's own
            // count wraps round.
            if (waiting >= MOST_INTERACTIONS_WAITING || System.nanoTime() - saveDueAt >= 0) {
                save();
            }
        } catch (SQLException e) {
            throw failure("write an interaction to", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hands over everything written since the last time, so that a second program looking at the run
     * can see it and the next question is answered out of a file that agrees with the answer.
     *
     * <p>Called even when nothing is waiting: a question asked earlier leaves the file being held at
     * the moment it was asked, and this is what lets go of it.
     */
    private void save() {
        int lost = waiting;
        // Zeroed before the attempt, so a failure cannot leave the count wrong or make every later
        // interaction retry the same doomed hand-over.
        waiting = 0;
        try {
            connection.commit();
        } catch (SQLException cannotSave) {
            rollbackQuietly();
            throw new InteractionStoreException("Could not save " + lost + " interaction(s) to the "
                    + "run's evidence at " + describedAs + ": " + cannotSave.getMessage(),
                    cannotSave);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException beyondHelp) {
            // Already failing; the original failure is the one worth reporting.
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new InteractionStoreException(
                    "This store is closed, so " + describedAs + " can no longer be used");
        }
    }

    @Override
    public List<Interaction> find(InteractionQuery query) {
        Objects.requireNonNull(query, "query");
        List<Object> values = new ArrayList<>();
        String sql = "SELECT id, document FROM interaction" + where(query, values) + ORDER
                + query.limit().map(limit -> " LIMIT " + limit).orElse("");
        // The rows are fetched while holding the lock and turned back into interactions afterwards.
        // Rebuilding a hundred thousand interactions takes a while, and nothing should have to wait
        // to record what an API just answered because somebody is reading the run at the same time.
        List<String[]> rowsRead = new ArrayList<>();
        lock.lock();
        try (PreparedStatement select = statement(sql)) {
            bind(select, values);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    rowsRead.add(new String[] {rows.getString(1), rows.getString(2)});
                }
            }
        } catch (SQLException e) {
            throw failure("read interactions from", e);
        } finally {
            lock.unlock();
        }
        return rowsRead.stream().map(row -> read(row[0], row[1])).toList();
    }

    /**
     * One stored row as an interaction again, saying which row it was if it cannot be read. Without
     * the identifier, a single damaged interaction in a run of thousands is a complaint nobody can
     * act on.
     */
    private static Interaction read(String id, String document) {
        try {
            return InteractionDocument.toInteraction(JsonText.read(document));
        } catch (JsonException e) {
            throw new InteractionStoreException(
                    "The interaction recorded as " + id + " could not be read back: "
                            + e.getMessage(), e);
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
                        ? Optional.of(read(id.value(), rows.getString(1)))
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
            // Letting go of the file would throw away whatever is still waiting, without a word.
            // Every run would lose its last few interactions; this is the line that prevents it.
            save();
            connection.close();
        } catch (SQLException e) {
            throw failure("close", e);
        } finally {
            // Belt and braces: if saving threw, the file is still released rather than held by a
            // program that has finished with it.
            closeQuietly(connection);
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

    /**
     * A statement ready to ask the file something, with everything waiting handed over first. Every
     * question in this class comes through here, so none of them can be answered out of a file that
     * does not yet have what this store has been told.
     */
    private PreparedStatement statement(String sql) throws SQLException {
        ensureOpen();
        save();
        return connection.prepareStatement(sql);
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
