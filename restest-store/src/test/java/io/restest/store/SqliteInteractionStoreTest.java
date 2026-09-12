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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.execution.Payload;
import io.restest.core.model.OperationId;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStoreException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the store promises: whatever a run did is still there afterwards, exactly as it happened, and
 * can be asked about.
 */
class SqliteInteractionStoreTest {

    @TempDir
    Path directory;

    private SqliteInteractionStore store;

    @BeforeEach
    void openAStore() {
        store = SqliteInteractionStore.inMemory();
    }

    @AfterEach
    void closeTheStore() {
        store.close();
    }

    @Test
    @DisplayName("everything about an interaction comes back exactly as it went in")
    void an_interaction_survives_the_round_trip() {
        Interaction stored = Interactions.elaborate();

        store.record(stored);

        assertThat(store.find(InteractionQuery.all()))
                .describedAs("records are values: what comes back should equal what went in, "
                        + "component for component, with no special reading required")
                .containsExactly(stored);
    }

    @Test
    @DisplayName("a repeated header stays repeated, and in the order it arrived")
    void repeated_headers_survive() {
        store.record(Interactions.elaborate());

        Interaction read = store.find(InteractionQuery.all()).get(0);

        assertThat(read.response().orElseThrow().headerValues("Set-Cookie"))
                .containsExactly("a=1", "b=2");
        assertThat(read.request().headerValues("Accept"))
                .containsExactly("application/json", "text/plain");
    }

    @Test
    @DisplayName("a reply too big to keep still remembers how big it was")
    void a_truncated_body_keeps_its_real_length() {
        store.record(Interactions.elaborate());

        Payload body = store.find(InteractionQuery.all()).get(0)
                .response().orElseThrow().body().orElseThrow();

        assertThat(body.truncated()).isTrue();
        assertThat(body.deliveredLength()).isEqualTo(4096);
        assertThat(new String(body.content(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"w-1\"");
    }

    @Test
    @DisplayName("a reply that is not text at all comes back byte for byte")
    void a_binary_body_is_not_mangled() {
        Interaction stored = Interactions.withBinaryReply();

        store.record(stored);

        assertThat(store.find(InteractionQuery.all())).containsExactly(stored);
    }

    @Test
    @DisplayName("where every value came from is still there, which is what explains a run later")
    void the_provenance_of_every_value_survives() {
        store.record(Interactions.elaborate());

        Interaction read = store.find(InteractionQuery.all()).get(0);

        assertThat(read.testCase().parameterValues()).hasSize(3);
        assertThat(read.testCase().parameterValues().get(2).origin())
                .isInstanceOf(io.restest.core.execution.ValueOrigin.Derived.class);
        assertThat(read.testCase().body()).isPresent();
        assertThat(read.testCase()).isEqualTo(Interactions.elaborate().testCase());
    }

    @Test
    @DisplayName("an attempt that never got an answer is stored as one, not lost")
    void a_failed_attempt_survives() {
        Interaction failed = Interactions.failed("GET /widgets");

        store.record(failed);

        Interaction read = store.find(InteractionQuery.all()).get(0);
        assertThat(read).isEqualTo(failed);
        assertThat(read.outcome()).isInstanceOf(InteractionOutcome.TransportFailure.class);
    }

    @Test
    @DisplayName("a reply that stopped halfway keeps its status, its headers and the part that came")
    void a_malformed_reply_survives() {
        Interaction malformed = Interactions.malformed("GET /widgets", 200);

        store.record(malformed);

        Interaction read = store.find(InteractionQuery.all()).get(0);
        assertThat(read).isEqualTo(malformed);
        InteractionOutcome.MalformedResponse outcome =
                (InteractionOutcome.MalformedResponse) read.outcome();
        assertThat(outcome.statusLine().orElseThrow().statusCode()).isEqualTo(200);
        assertThat(outcome.partial().orElseThrow().deliveredLength()).isEqualTo(100);
    }

    @Test
    @DisplayName("interactions come back oldest first, whatever order they were written in")
    void interactions_come_back_in_the_order_they_happened() {
        Interaction later = Interactions.answered("GET /b", 200,
                Interactions.NOON.plus(Duration.ofSeconds(10)));
        Interaction earlier = Interactions.answered("GET /a", 200, Interactions.NOON);

        store.record(later);
        store.record(earlier);

        assertThat(store.find(InteractionQuery.all()))
                .containsExactly(earlier, later);
    }

    @Test
    @DisplayName("every question the query API can ask")
    void the_queries_answer_what_they_claim() {
        store.record(Interactions.answered("GET /widgets", 200));
        store.record(Interactions.answered("GET /widgets", 500));
        store.record(Interactions.answered("POST /widgets", 201));
        store.record(Interactions.answered("POST /widgets", 503));
        store.record(Interactions.failed("GET /gone"));
        store.record(Interactions.malformed("GET /half", 200));

        assertThat(store.count(InteractionQuery.all())).isEqualTo(6);
        assertThat(store.count(InteractionQuery.forOperation(OperationId.of("GET /widgets"))))
                .isEqualTo(2);
        assertThat(store.count(InteractionQuery.withStatus(503))).isEqualTo(1);
        assertThat(store.count(InteractionQuery.serverErrors()))
                .describedAs("500 and 503, and nothing that merely failed to connect")
                .isEqualTo(2);
        assertThat(store.count(InteractionQuery.neverAnswered())).isEqualTo(1);
        assertThat(store.count(InteractionQuery.all()
                .andOutcome(InteractionQuery.Outcome.MALFORMED))).isEqualTo(1);
        assertThat(store.count(InteractionQuery.forOperation(OperationId.of("POST /widgets"))
                .andStatusClass(5))).isEqualTo(1);
        assertThat(store.count(InteractionQuery.forOperation(OperationId.of("GET /nothing"))))
                .isZero();
    }

    @Test
    @DisplayName("a limit is about how many you want back, not about how many there are")
    void a_limit_caps_the_answer_but_not_the_count() {
        IntStream.range(0, 10).forEach(i -> store.record(Interactions.answered("GET /widgets", 200,
                Interactions.NOON.plusSeconds(i))));

        assertThat(store.find(InteractionQuery.all().limitedTo(3))).hasSize(3);
        assertThat(store.count(InteractionQuery.all().limitedTo(3))).isEqualTo(10);
    }

    @Test
    @DisplayName("one interaction can be found again by its identifier")
    void an_interaction_can_be_found_by_id() {
        Interaction stored = Interactions.elaborate();
        store.record(stored);

        assertThat(store.byId(stored.id())).contains(stored);
        assertThat(store.byId(InteractionId.of("never-happened"))).isEmpty();
    }

    @Test
    @DisplayName("recording the same interaction twice leaves one, not two")
    void recording_the_same_interaction_twice_is_harmless() {
        Interaction stored = Interactions.elaborate();

        store.record(stored);
        store.record(stored);

        assertThat(store.count(InteractionQuery.all())).isEqualTo(1);
    }

    @Test
    @DisplayName("a run closed and reopened is all still there, which is the point of storing it")
    void a_stored_run_outlives_the_program_that_made_it() {
        Path file = directory.resolve("run.sqlite");
        Interaction stored = Interactions.elaborate();

        try (SqliteInteractionStore writing = SqliteInteractionStore.at(file)) {
            writing.record(stored);
            writing.record(Interactions.failed("GET /gone"));
        }

        try (SqliteInteractionStore reading = SqliteInteractionStore.at(file)) {
            assertThat(reading.count(InteractionQuery.all())).isEqualTo(2);
            assertThat(reading.byId(stored.id())).contains(stored);
        }
    }

    @Test
    @DisplayName("two runs open at once do not see each other")
    void two_stores_keep_separate_runs() {
        try (SqliteInteractionStore other = SqliteInteractionStore.at(directory.resolve("b.db"))) {
            store.record(Interactions.answered("GET /a", 200));
            other.record(Interactions.answered("GET /b", 200));
            other.record(Interactions.answered("GET /c", 200));

            assertThat(store.count(InteractionQuery.all())).isEqualTo(1);
            assertThat(other.count(InteractionQuery.all())).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("recording from many threads at once loses nothing")
    void recording_from_many_threads_loses_nothing() throws InterruptedException {
        int writers = 8;
        int each = 25;
        CountDownLatch done = new CountDownLatch(writers);

        for (int writer = 0; writer < writers; writer++) {
            Thread.ofVirtual().start(() -> {
                try {
                    for (int i = 0; i < each; i++) {
                        store.record(Interactions.answered("GET /widgets", 200));
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(store.count(InteractionQuery.all())).isEqualTo((long) writers * each);
    }

    @Test
    @DisplayName("a closed store says so instead of failing obscurely, and closing twice is fine")
    void a_closed_store_refuses_to_be_used() {
        SqliteInteractionStore closed = SqliteInteractionStore.at(directory.resolve("closed.db"));
        closed.close();
        closed.close();

        assertThatExceptionOfType(InteractionStoreException.class)
                .isThrownBy(() -> closed.count(InteractionQuery.all()))
                .withMessageContaining("closed");
    }

    @Test
    @DisplayName("a store that cannot be opened says where and why")
    void an_unusable_location_is_reported() {
        Path insideAFile = directory.resolve("not-a-directory").resolve("run.db");

        assertThatExceptionOfType(InteractionStoreException.class)
                .isThrownBy(() -> SqliteInteractionStore.at(insideAFile))
                .withMessageContaining("run.db");
    }

    @Test
    @DisplayName("the file is an ordinary SQLite database, readable without RESTest")
    void the_stored_run_is_readable_by_anything_that_reads_sqlite() throws Exception {
        Path file = directory.resolve("run.sqlite");
        try (SqliteInteractionStore writing = SqliteInteractionStore.at(file)) {
            writing.record(Interactions.elaborate());
        }

        assertThat(Files.readAllBytes(file))
                .describedAs("every SQLite file starts by saying so")
                .startsWith("SQLite format 3".getBytes(StandardCharsets.US_ASCII));

        List<String> documents = readColumn(file, "SELECT document FROM interaction");
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0))
                .describedAs("the interaction is stored as JSON, which is what makes a run "
                        + "readable by tools that never heard of RESTest")
                .contains("\"operation\":\"POST /widgets\"")
                .contains("\"status\":201")
                .contains("\"name\":\"a widget\"");
        assertThat(readColumn(file, "SELECT operation FROM interaction"))
                .describedAs("the facts people filter on are plain columns, so SQL can be used "
                        + "directly against a stored run")
                .containsExactly("POST /widgets");
    }

    private static List<String> readColumn(Path file, String sql) throws Exception {
        org.sqlite.SQLiteDataSource source = new org.sqlite.SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        try (var connection = source.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            List<String> values = new java.util.ArrayList<>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        }
    }
}
