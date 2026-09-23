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
package io.restest.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventStreamTest {

    @Test
    @DisplayName("every listener hears every event, in the order it happened")
    void events_reach_every_listener_in_order() {
        List<String> first = new CopyOnWriteArrayList<>();
        List<String> second = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> first.add(name(event)));
            events.subscribe(event -> second.add(name(event)));

            events.publish(started());
            events.publish(planned());
            events.publish(completed());
            events.publish(finished());
        }

        assertThat(first).containsExactly("RunStarted", "TestCasePlanned", "InteractionCompleted",
                "RunFinished");
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("announcing something does not wait for the listeners to deal with it")
    void publishing_does_not_wait_for_listeners() {
        CountDownLatch listenerMayProceed = new CountDownLatch(1);
        AtomicLong announcedBy = new AtomicLong();

        EventStream events = new EventStream();
        events.subscribe(event -> {
            try {
                listenerMayProceed.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        IntStream.range(0, 100).forEach(i -> events.publish(planned()));
        announcedBy.set(events.published());

        // The listener is still stuck on the very first event, and a hundred have been announced.
        assertThat(announcedBy.get()).isEqualTo(100);
        assertThat(events.delivered()).isLessThan(100);

        listenerMayProceed.countDown();
        events.close();
        assertThat(events.delivered()).isEqualTo(100);
    }

    @Test
    @DisplayName("a listener that breaks is counted, and the others still hear everything")
    void a_broken_listener_does_not_take_the_rest_down() {
        List<String> heard = new CopyOnWriteArrayList<>();

        EventStream events = new EventStream();
        events.subscribe(event -> {
            throw new IllegalStateException("this report is broken");
        });
        events.subscribe(event -> heard.add(name(event)));

        events.publish(started());
        events.publish(completed());
        events.close();

        assertThat(heard).containsExactly("RunStarted", "InteractionCompleted");
        assertThat(events.listenerFailures()).isEqualTo(2);
        assertThat(events.delivered()).isEqualTo(2);
    }

    @Test
    @DisplayName("what a listener announces while being told something is itself delivered")
    void a_listener_may_announce_something_of_its_own() {
        List<String> heard = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.InteractionCompleted) {
                    events.publish(new RunEvent.FaultFound(Instant.EPOCH, finding()));
                }
            });
            events.subscribe(event -> heard.add(name(event)));

            events.publish(completed());
        }

        assertThat(heard).containsExactly("InteractionCompleted", "FaultFound");
    }

    @Test
    @DisplayName("what a listener announces arrives beside its cause, not behind everything queued")
    void a_listener_s_announcement_arrives_beside_its_cause() {
        List<String> heard = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.InteractionCompleted) {
                    events.publish(new RunEvent.FaultFound(Instant.EPOCH, finding()));
                }
            });
            events.subscribe(event -> heard.add(name(event)));

            // Everything is queued before any of it is delivered, so the fault the first attempt
            // produces has the second attempt and the end of the run already waiting behind it.
            events.publish(completed());
            events.publish(planned());
            events.publish(finished());
        }

        assertThat(heard).containsExactly("InteractionCompleted", "FaultFound", "TestCasePlanned",
                "RunFinished");
    }

    @Test
    @DisplayName("closing waits for what is still queued, so a report is complete when it returns")
    void closing_delivers_what_is_still_queued() {
        List<String> heard = new CopyOnWriteArrayList<>();

        EventStream events = new EventStream();
        events.subscribe(event -> heard.add(name(event)));
        IntStream.range(0, 500).forEach(i -> events.publish(planned()));
        events.close();

        assertThat(heard).hasSize(500);
        assertThat(events.undelivered()).isZero();
        assertThat(events.isClosed()).isTrue();
    }

    @Test
    @DisplayName("a listener whose own library is missing a class is survived like any other")
    void a_listener_missing_a_class_does_not_take_the_rest_down() {
        List<String> heard = new CopyOnWriteArrayList<>();

        EventStream events = new EventStream();
        events.subscribe(event -> {
            throw new NoClassDefFoundError("some/library/Missing");
        });
        events.subscribe(event -> heard.add(name(event)));

        events.publish(started());
        events.publish(completed());
        events.publish(finished());
        events.close();

        assertThat(heard).containsExactly("RunStarted", "InteractionCompleted", "RunFinished");
        assertThat(events.listenerFailures()).isEqualTo(3);
    }

    @Test
    @DisplayName("nothing announced while a run is ending is lost between the two")
    void nothing_is_lost_between_announcing_and_ending() throws InterruptedException {
        List<String> heard = new CopyOnWriteArrayList<>();
        EventStream events = new EventStream();
        events.subscribe(event -> heard.add(name(event)));

        // Several threads announce while another ends the run. Every announcement that was accepted
        // - that is, every one that did not come back saying the run was over - must arrive.
        CountDownLatch go = new CountDownLatch(1);
        AtomicLong accepted = new AtomicLong();
        List<Thread> publishers = IntStream.range(0, 8)
                .mapToObj(i -> Thread.ofVirtual().unstarted(() -> {
                    try {
                        go.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int event = 0; event < 200; event++) {
                        try {
                            events.publish(planned());
                            accepted.incrementAndGet();
                        } catch (IllegalStateException theRunIsOver) {
                            return;
                        }
                    }
                }))
                .toList();
        publishers.forEach(Thread::start);
        go.countDown();
        Thread.ofVirtual().start(events::close).join();
        for (Thread publisher : publishers) {
            publisher.join();
        }

        assertThat(events.undelivered()).describedAs("nothing was left behind").isZero();
        assertThat(heard).hasSize((int) accepted.get());
    }

    @Test
    @DisplayName("a run that has finished neither announces nor takes on new listeners")
    void a_closed_stream_refuses_more() {
        EventStream events = new EventStream();
        events.close();

        assertThatIllegalStateException()
                .isThrownBy(() -> events.publish(started()))
                .withMessageContaining("RunStarted");
        assertThatIllegalStateException()
                .isThrownBy(() -> events.subscribe(event -> { }));
    }

    @Test
    @DisplayName("closing twice is not an error")
    void closing_twice_is_harmless() {
        EventStream events = new EventStream();
        events.close();
        events.close();

        assertThat(events.isClosed()).isTrue();
    }

    @Test
    @DisplayName("a run cannot be said to have taken less time than none")
    void a_run_cannot_take_negative_time() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunEvent.RunFinished(
                Instant.EPOCH, Duration.ofSeconds(-1), EngineStatistics.none()));
    }

    @Test
    @DisplayName("waiting for the listeners returns once every one of them has heard everything")
    void waiting_returns_once_everything_has_been_heard() {
        List<String> heard = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                sleep(Duration.ofMillis(30));
                heard.add(name(event));
            });
            events.publish(started());
            events.publish(planned());
            events.publish(completed());

            assertThat(events.awaitDelivery(Duration.ofSeconds(10))).isTrue();
            // Read before the stream is closed, which would otherwise have waited for all three
            // by itself and proved nothing.
            assertThat(heard).containsExactly("RunStarted", "TestCasePlanned",
                    "InteractionCompleted");
        }
    }

    @Test
    @DisplayName("waiting for the listeners includes what one of them said in reply")
    void waiting_includes_what_a_listener_announced_in_reply() {
        List<String> heard = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            // The case a count of what is still waiting gets wrong: while the reply is being
            // delivered, the rule announces a fault, and for a moment the numbers can come out
            // even with the fault not yet heard by anybody.
            events.subscribe(event -> {
                if (event instanceof RunEvent.InteractionCompleted) {
                    sleep(Duration.ofMillis(30));
                    events.publish(new RunEvent.FaultFound(Instant.EPOCH, finding()));
                }
            });
            events.subscribe(event -> heard.add(name(event)));
            events.publish(completed());

            assertThat(events.awaitDelivery(Duration.ofSeconds(10))).isTrue();
            assertThat(heard).containsExactly("InteractionCompleted", "FaultFound");
        }
    }

    @Test
    @DisplayName("waiting for a listener that never finishes gives up when the time is up")
    void waiting_gives_up_on_a_listener_that_never_finishes() {
        CountDownLatch release = new CountDownLatch(1);
        EventStream events = new EventStream();
        events.subscribe(event -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        events.publish(planned());

        long before = System.nanoTime();
        boolean caughtUp = events.awaitDelivery(Duration.ofMillis(100));
        Duration waited = Duration.ofNanos(System.nanoTime() - before);

        assertThat(caughtUp).isFalse();
        assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(100))
                .isLessThan(Duration.ofSeconds(5));
        release.countDown();
        events.close();
        assertThat(events.undelivered()).isZero();
    }

    @Test
    @DisplayName("waiting no time at all does not wait")
    void waiting_no_time_does_not_wait() {
        CountDownLatch release = new CountDownLatch(1);
        EventStream events = new EventStream();
        events.subscribe(event -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        events.publish(planned());

        long before = System.nanoTime();
        boolean caughtUp = events.awaitDelivery(Duration.ZERO);

        assertThat(caughtUp).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(1));
        assertThat(events.awaitDelivery(Duration.ofSeconds(-1))).isFalse();
        release.countDown();
        events.close();
    }

    @Test
    @DisplayName("a length of time too long to count in nanoseconds is waited for as long as any")
    void a_wait_too_long_to_count_is_still_a_wait() {
        try (EventStream events = new EventStream()) {
            events.publish(planned());

            assertThat(events.awaitDelivery(Duration.ofDays(365L * 1_000))).isTrue();
        }
    }

    @Test
    @DisplayName("a listener cannot wait for the listeners, which would be waiting for itself")
    void a_listener_cannot_wait_for_itself() {
        List<Throwable> refused = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                try {
                    events.awaitDelivery(Duration.ofSeconds(10));
                } catch (IllegalStateException expected) {
                    refused.add(expected);
                }
            });
            events.publish(planned());
        }

        assertThat(refused).singleElement()
                .satisfies(why -> assertThat(why).hasMessageContaining("cannot wait"));
    }

    @Test
    @DisplayName("once the run has ended, waiting for the listeners answers at once")
    void waiting_after_the_end_answers_at_once() {
        EventStream events = new EventStream();
        events.publish(planned());
        events.close();

        long before = System.nanoTime();
        assertThat(events.awaitDelivery(Duration.ofSeconds(10))).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("a stretch of the run is heard in order, and has a name")
    void a_stretch_of_the_run_is_heard_in_order() {
        List<String> heard = new CopyOnWriteArrayList<>();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> heard.add(name(event)));
            events.publish(new RunEvent.PhaseStarted(Instant.EPOCH, "opening lap"));
            events.publish(planned());
            events.publish(new RunEvent.PhaseFinished(Instant.EPOCH, "opening lap", false));
        }

        assertThat(heard).containsExactly("PhaseStarted", "TestCasePlanned", "PhaseFinished");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RunEvent.PhaseStarted(Instant.EPOCH, " "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RunEvent.PhaseFinished(Instant.EPOCH, "", true));
    }

    private static void sleep(Duration length) {
        try {
            Thread.sleep(length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String name(RunEvent event) {
        return event.getClass().getSimpleName();
    }

    private static RunEvent started() {
        return new RunEvent.RunStarted(Instant.EPOCH, "Petstore", "https://api.example");
    }

    private static RunEvent planned() {
        return new RunEvent.TestCasePlanned(Instant.EPOCH, testCase());
    }

    private static RunEvent completed() {
        return new RunEvent.InteractionCompleted(Instant.EPOCH, interaction());
    }

    private static RunEvent finished() {
        return new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(3),
                EngineStatistics.none());
    }

    private static TestCase testCase() {
        return TestCase.of(OperationId.of("GET /pets"), List.of(ParameterValue.of("limit",
                ParameterLocation.QUERY, JsonValue.of(10), new ValueOrigin.Generated("random"))));
    }

    private static Interaction interaction() {
        return Interaction.answered(testCase(),
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets"),
                HttpResponseRecord.of(200), Instant.EPOCH, Duration.ofMillis(12));
    }

    private static Finding finding() {
        return Finding.of(WfcFault.HTTP_STATUS_500, interaction(), "the API answered 500");
    }
}
