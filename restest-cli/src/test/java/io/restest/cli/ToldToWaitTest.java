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
package io.restest.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.event.Waited;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.settings.LengthOfTime;
import io.restest.core.settings.ScheduleSettings;
import io.restest.core.settings.Settings;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the API has asked the run to leave alone, and for how long, asked of the rule alone.
 */
class ToldToWaitTest {

    private static final Instant START = Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant DEADLINE = START.plus(Duration.ofHours(1));

    private static final OperationId CREATE = OperationId.synthesised(HttpMethod.POST, "/tans");
    private static final OperationId READ = OperationId.synthesised(HttpMethod.GET, "/tans/{id}");
    private static final OperationId LIST = OperationId.synthesised(HttpMethod.GET, "/tans");

    /** Waits of 1 s, 2 s and 4 s, then 4 s again. */
    private static final ScheduleSettings SETTINGS = settings(true, Duration.ofSeconds(1),
            Duration.ofSeconds(4));

    private final Clock clock = new Clock(START);

    @Test
    @DisplayName("a 429 that does not say for how long leaves its operation alone for the first guess, and no other")
    void a_429_without_a_header_waits_the_first_guess() {
        ToldToWait waits = watching(SETTINGS);

        assertThat(waits.settled(CREATE, reply(429, START))).isEmpty();

        assertThat(waits.mayBeSent(CREATE)).isFalse();
        assertThat(waits.mayBeSent(READ)).isTrue();
        assertThat(waits.wholeApiWaitsUntil()).isEmpty();
        clock.at(START.plusMillis(999));
        assertThat(waits.mayBeSent(CREATE)).isFalse();
        clock.at(START.plusSeconds(1));
        assertThat(waits.mayBeSent(CREATE)).isTrue();
    }

    @Test
    @DisplayName("the guess doubles each time the operation is told again, up to the ceiling")
    void the_guess_doubles_up_to_the_ceiling() {
        ToldToWait waits = watching(SETTINGS);
        List<Duration> lengths = new ArrayList<>();
        Instant at = START;
        for (int i = 0; i < 5; i++) {
            clock.at(at);
            waits.settled(CREATE, reply(429, at));
            lengths.add(Duration.between(at, waits.soonestEnd().orElseThrow()));
            at = waits.soonestEnd().orElseThrow();
        }

        assertThat(lengths).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(4), Duration.ofSeconds(4), Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("an ordinary answer to a request sent after the wait brings the guess back; a stale one does not")
    void only_an_answer_sent_after_the_wait_brings_the_guess_back() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));
        Instant afterTheFirst = START.plusSeconds(1);
        clock.at(afterTheFirst);
        waits.settled(CREATE, reply(429, afterTheFirst));
        Instant afterTheSecond = afterTheFirst.plusSeconds(2);
        clock.at(afterTheSecond);

        // Sent before the second wait was over, and arriving after it: it had not heard of it.
        waits.settled(CREATE, reply(201, afterTheFirst.plusMillis(500)));
        waits.settled(CREATE, reply(429, afterTheSecond));
        assertThat(waits.soonestEnd()).contains(afterTheSecond.plusSeconds(4));

        Instant afterTheThird = afterTheSecond.plusSeconds(4);
        clock.at(afterTheThird);
        waits.settled(CREATE, reply(201, afterTheThird));
        waits.settled(CREATE, reply(429, afterTheThird));
        assertThat(waits.soonestEnd()).contains(afterTheThird.plusSeconds(1));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {200, 400, 404, 500, 503})
    @DisplayName("any answer that does not ask to wait, sent after the wait, brings the guess back to "
            + "the start")
    void any_answer_without_a_wait_brings_the_guess_back(int status) {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));
        Instant over = START.plusSeconds(1);
        clock.at(over);

        waits.settled(CREATE, reply(status, over));
        waits.settled(CREATE, reply(429, over));

        assertThat(waits.soonestEnd()).contains(over.plusSeconds(1));
    }

    @Test
    @DisplayName("a 503 that says how long to wait is a wait, and brings no guess back")
    void a_503_with_a_header_brings_no_guess_back() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));
        Instant over = START.plusSeconds(1);
        clock.at(over);
        waits.settled(CREATE, reply(503, over, "Retry-After", "0"));

        waits.settled(CREATE, reply(429, over));

        assertThat(waits.soonestEnd()).contains(over.plusSeconds(2));
    }

    @Test
    @DisplayName("a request that got no reply says nothing, and brings no guess back")
    void no_reply_says_nothing() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));
        clock.at(START.plusSeconds(1));
        waits.settled(CREATE, Interaction.transportFailure(testCase(CREATE), request(),
                "connection reset", START.plusSeconds(1), Duration.ZERO));
        waits.settled(CREATE, reply(429, START.plusSeconds(1)));

        assertThat(waits.soonestEnd()).contains(START.plusSeconds(3));
    }

    @Test
    @DisplayName("a 429 that says for how long is obeyed, and leaves the guess where it was")
    void a_stated_wait_is_obeyed_and_leaves_the_guess() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START, "Retry-After", "10"));
        assertThat(waits.soonestEnd()).contains(START.plusSeconds(10));

        Instant later = START.plusSeconds(10);
        clock.at(later);
        waits.settled(CREATE, reply(429, later));
        assertThat(waits.soonestEnd()).contains(later.plusSeconds(1));
    }

    @Test
    @DisplayName("a 503 waits only when it says for how long; otherwise it is a server error like any other")
    void a_503_waits_only_with_a_header() {
        ToldToWait waits = watching(SETTINGS);

        waits.settled(CREATE, reply(503, START));
        assertThat(waits.mayBeSent(CREATE)).isTrue();
        waits.settled(CREATE, reply(503, START, "Retry-After", "soon"));
        assertThat(waits.mayBeSent(CREATE)).isTrue();

        waits.settled(CREATE, reply(503, START, "Retry-After", "5"));
        assertThat(waits.mayBeSent(CREATE)).isFalse();
        assertThat(waits.soonestEnd()).contains(START.plusSeconds(5));
    }

    @Test
    @DisplayName("a refusal of a request sent before the wait began only ever moves its end later")
    void a_refusal_sent_before_the_wait_moves_its_end_later_only() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START, "Retry-After", "5"));
        Instant sentBefore = START.minusMillis(10);

        waits.settled(CREATE, reply(429, sentBefore));
        waits.settled(CREATE, reply(429, sentBefore, "Retry-After", "2"));
        assertThat(waits.soonestEnd()).contains(START.plusSeconds(5));

        waits.settled(CREATE, reply(429, sentBefore, "Retry-After", "8"));
        assertThat(waits.soonestEnd()).contains(START.plusSeconds(8));
        assertThat(summary(waits).operations()).singleElement()
                .extracting(Waited.ForOperation::times).isEqualTo(1L);
    }

    @Test
    @DisplayName("a second operation told while another waits makes the whole API wait, and asks the engine to hold")
    void a_second_operation_makes_the_whole_api_wait() {
        ToldToWait waits = watching(SETTINGS);
        assertThat(waits.settled(CREATE, reply(429, START))).isEmpty();

        clock.at(START.plusMillis(200));
        Optional<Duration> hold = waits.settled(READ, reply(429, START.plusMillis(100)));

        assertThat(hold).contains(Duration.ofSeconds(1));
        assertThat(waits.wholeApiWaitsUntil()).contains(START.plusMillis(1200));
        assertThat(waits.mayBeSent(LIST))
                .describedAs("whether the whole API waits is a question of its own").isTrue();
    }

    @Test
    @DisplayName("the whole API's own wait doubles when it is told again after its wait")
    void the_whole_api_doubles_its_own_wait() {
        ToldToWait waits = watching(settings(true, Duration.ofSeconds(1), Duration.ofSeconds(60)));
        waits.settled(CREATE, reply(429, START));
        waits.settled(READ, reply(429, START));
        Instant over = START.plusSeconds(1);
        clock.at(over);

        // Both operations' own waits are over too; one is told again, then the other.
        waits.settled(CREATE, reply(429, over));
        Optional<Duration> hold = waits.settled(READ, reply(429, over));

        assertThat(hold).contains(Duration.ofSeconds(2));
        assertThat(summary(waits).wholeApi().times()).isEqualTo(2);
    }

    @Test
    @DisplayName("a refusal stating a later moment during the whole API's wait moves it, and holds the engine longer")
    void a_later_moment_moves_the_whole_apis_wait() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));
        waits.settled(READ, reply(429, START));

        Optional<Duration> hold = waits.settled(LIST,
                reply(429, START.minusMillis(5), "Retry-After", "30"));

        assertThat(hold).contains(Duration.ofSeconds(30));
        assertThat(waits.wholeApiWaitsUntil()).contains(START.plusSeconds(30));
        assertThat(waits.settled(LIST, reply(429, START.minusMillis(5), "Retry-After", "3")))
                .describedAs("a shorter one changes nothing").isEmpty();
    }

    @Test
    @DisplayName("an ordinary answer while one operation waits makes nobody else wait")
    void an_ordinary_answer_changes_nothing() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));

        assertThat(waits.settled(READ, reply(200, START))).isEmpty();
        assertThat(waits.mayBeSent(READ)).isTrue();
        assertThat(waits.wholeApiWaitsUntil()).isEmpty();
    }

    @Test
    @DisplayName("nothing an API writes in Retry-After makes it fail, and no wait outlasts the run")
    void a_hostile_header_never_fails() {
        ToldToWait waits = watching(SETTINGS);
        for (String value : List.of("100000000000000000", "9223372036854775807",
                "99999999999999999999999", "Fri, 31 Dec 9999 23:59:59 GMT", "-1", "\u0000")) {
            waits.settled(CREATE, reply(429, START.minusSeconds(1), "Retry-After", value));
            waits.settled(READ, reply(503, START.minusSeconds(1), "Retry-After", value));
        }

        assertThat(waits.soonestEnd()).hasValueSatisfying(end ->
                assertThat(end).isBeforeOrEqualTo(DEADLINE));
        assertThat(waits.wholeApiWaitsUntil()).contains(DEADLINE);
    }

    @Test
    @DisplayName("switched off, nothing waits and there is nothing to say")
    void switched_off() {
        ToldToWait waits = watching(settings(false, Duration.ofSeconds(1),
                Duration.ofSeconds(4)));

        assertThat(waits.settled(CREATE, reply(429, START, "Retry-After", "30"))).isEmpty();
        assertThat(waits.settled(READ, reply(429, START))).isEmpty();
        assertThat(waits.mayBeSent(CREATE)).isTrue();
        assertThat(waits.wholeApiWaitsUntil()).isEmpty();
        assertThat(waits.summary()).isEmpty();
    }

    @Test
    @DisplayName("a run never told to wait says so")
    void never_told() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(200, START));

        assertThat(waits.summary()).contains(Waited.never());
    }

    @Test
    @DisplayName("the summary counts every wait, the turns it skipped and the time it took, up to the moment sending stopped")
    void the_summary_counts_up_to_the_stop() {
        ToldToWait waits = watching(SETTINGS);
        waits.settled(CREATE, reply(429, START));
        waits.mayBeSent(CREATE);
        waits.mayBeSent(CREATE);
        Instant second = START.plusSeconds(1);
        clock.at(second);
        waits.settled(CREATE, reply(429, second, "Retry-After", "10"));
        waits.mayBeSent(CREATE);
        clock.at(second.plusSeconds(3));
        waits.stoppedSending();
        // Heard after sending stopped: counted nowhere.
        waits.settled(READ, reply(429, second.plusSeconds(3)));
        waits.idleWhileWaiting(Duration.ofMillis(700));

        Waited said = summary(waits);

        assertThat(said.operations()).containsExactly(new Waited.ForOperation(CREATE, 2, 1,
                Duration.ofSeconds(4), 3));
        assertThat(said.wholeApi()).isEqualTo(new Waited.WholeApi(0, Duration.ZERO));
        assertThat(said.idle()).isEqualTo(Duration.ofMillis(700));
    }

    @Test
    @DisplayName("the soonest wait is the one that ends first, the whole API's included")
    void the_soonest_wait() {
        ToldToWait waits = watching(SETTINGS);
        assertThat(waits.soonestEnd()).isEmpty();
        waits.settled(CREATE, reply(429, START, "Retry-After", "20"));
        waits.settled(READ, reply(429, START, "Retry-After", "5"));

        assertThat(waits.soonestEnd()).contains(START.plusSeconds(5));
        clock.at(START.plusSeconds(6));
        assertThat(waits.soonestEnd()).contains(START.plusSeconds(20));
    }

    @Test
    @DisplayName("many refusals of one moment settled at once begin one wait, whichever thread is first")
    void refusals_settled_at_once_begin_one_wait() throws InterruptedException {
        ToldToWait waits = watching(SETTINGS);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    go.await();
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return;
                }
                waits.settled(CREATE, reply(429, START));
            }));
        }
        go.countDown();
        // The thread that decides asks all the while, as the run's does.
        for (int i = 0; i < 1_000; i++) {
            waits.mayBeSent(CREATE);
            waits.wholeApiWaitsUntil();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(summary(waits).operations()).singleElement()
                .extracting(Waited.ForOperation::times).isEqualTo(1L);
        assertThat(waits.soonestEnd()).contains(START.plusSeconds(1));
        assertThat(waits.wholeApiWaitsUntil()).isEmpty();
    }

    private ToldToWait watching(ScheduleSettings settings) {
        return ToldToWait.watching(settings, DEADLINE, clock);
    }

    private static Waited summary(ToldToWait waits) {
        return waits.summary().orElseThrow();
    }

    private static ScheduleSettings settings(boolean wait, Duration first, Duration longest) {
        return Settings.from(Map.of("schedule.waitWhenToldTo", String.valueOf(wait),
                "schedule.firstGuessedWait", LengthOfTime.written(first),
                "schedule.longestGuessedWait", LengthOfTime.written(longest))).schedule();
    }

    private static Interaction reply(int status, Instant sentAt, String... namesAndValues) {
        return RetryAfterTest.reply(status, sentAt, namesAndValues);
    }

    private static TestCase testCase(OperationId operation) {
        return TestCase.of(operation, List.of());
    }

    private static HttpRequestRecord request() {
        return HttpRequestRecord.of(HttpMethod.POST, "http://localhost/tans");
    }

    /** A clock a test moves by hand. */
    private static final class Clock implements InstantSource {

        private final AtomicReference<Instant> now;

        Clock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void at(Instant moment) {
            now.set(moment);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
