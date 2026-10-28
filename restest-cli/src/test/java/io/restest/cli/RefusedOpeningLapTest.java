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

import io.restest.core.event.Unauthorized;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The question of whether the API refused a run's whole opening lap, asked of the rule alone.
 */
class RefusedOpeningLapTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    private static final InstantSource CLOCK = InstantSource.fixed(NOW);

    @Test
    @DisplayName("a lap whose every request was answered 401 is refused, once all of them settled")
    void a_lap_answered_401_throughout_is_refused_once_it_has_settled() {
        RefusedOpeningLap lap = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(lap, 3);
        lap.lapIsOver();
        lap.settled(Optional.of(401), true);
        lap.settled(Optional.of(401), true);

        assertThat(lap.refused()).describedAs("one request of the lap is still on its way")
                .isEmpty();

        lap.settled(Optional.of(401), true);
        assertThat(lap.refused()).contains(new Unauthorized(NOW, 3));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {200, 201, 302, 400, 403, 404, 500})
    @DisplayName("any other answer to a request of the lap rules the stop out")
    void any_other_answer_rules_it_out(int status) {
        RefusedOpeningLap lap = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(lap, 2);
        lap.lapIsOver();
        lap.settled(Optional.of(401), true);
        lap.settled(Optional.of(status), true);

        assertThat(lap.refused()).isEmpty();
    }

    @Test
    @DisplayName("a request of the lap that got no reply rules the stop out")
    void a_lap_request_with_no_reply_rules_it_out() {
        RefusedOpeningLap lap = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(lap, 2);
        lap.lapIsOver();
        lap.settled(Optional.of(401), true);
        lap.settled(Optional.empty(), true);

        assertThat(lap.refused()).isEmpty();
    }

    @Test
    @DisplayName("an answer other than 401 to a request sent after the lap, heard before the "
            + "lap has settled, rules the stop out; one that got no reply does not")
    void what_came_back_after_the_lap_counts_until_the_question_is_asked() {
        RefusedOpeningLap refusedAfter = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(refusedAfter, 2);
        refusedAfter.lapIsOver();
        refusedAfter.settled(Optional.of(401), true);
        refusedAfter.settled(Optional.empty(), false);
        refusedAfter.settled(Optional.of(401), false);
        refusedAfter.settled(Optional.of(401), true);
        assertThat(refusedAfter.refused()).isPresent();

        RefusedOpeningLap acceptedAfter = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(acceptedAfter, 2);
        acceptedAfter.lapIsOver();
        acceptedAfter.settled(Optional.of(401), true);
        acceptedAfter.settled(Optional.of(200), false);
        acceptedAfter.settled(Optional.of(401), true);
        assertThat(acceptedAfter.refused()).isEmpty();
    }

    @Test
    @DisplayName("the question is asked once: a lap nothing answered, then a 401, is never refused")
    void it_is_asked_once() {
        RefusedOpeningLap lap = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(lap, 2);
        lap.lapIsOver();
        lap.settled(Optional.empty(), true);
        lap.settled(Optional.empty(), true);
        assertThat(lap.refused()).isEmpty();

        lap.settled(Optional.of(401), false);
        assertThat(lap.refused())
                .describedAs("a 401 that comes after the answer was given changes nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing is said before the lap is over, a lap that sent nothing is never "
            + "refused, and switched off, nothing ever is")
    void nothing_is_said_too_early_or_when_switched_off() {
        RefusedOpeningLap notOver = RefusedOpeningLap.watching(true, CLOCK);
        sendInTheLap(notOver, 1);
        notOver.settled(Optional.of(401), true);
        assertThat(notOver.refused()).describedAs("the lap is still going").isEmpty();
        notOver.lapIsOver();
        assertThat(notOver.refused()).isPresent();

        RefusedOpeningLap sentNothing = RefusedOpeningLap.watching(true, CLOCK);
        sentNothing.lapIsOver();
        sentNothing.settled(Optional.of(401), false);
        assertThat(sentNothing.refused()).isEmpty();

        RefusedOpeningLap switchedOff = RefusedOpeningLap.watching(false, CLOCK);
        sendInTheLap(switchedOff, 1);
        switchedOff.lapIsOver();
        switchedOff.settled(Optional.of(401), true);
        assertThat(switchedOff.refused()).isEmpty();
    }

    @Test
    @DisplayName("answered on many threads at once, a lap whose last answer is a 200 is never "
            + "seen settled without that 200")
    void the_last_answer_is_never_missed() throws InterruptedException {
        // The deciding thread asks over and over while the answers arrive; whatever moment it asks
        // at, a lap that is settled has been heard in full.
        for (int attempt = 0; attempt < 200; attempt++) {
            RefusedOpeningLap lap = RefusedOpeningLap.watching(true, CLOCK);
            int requests = 16;
            sendInTheLap(lap, requests);
            lap.lapIsOver();
            CountDownLatch go = new CountDownLatch(1);
            Thread[] answering = new Thread[requests];
            for (int i = 0; i < requests; i++) {
                int status = i == requests - 1 ? 200 : 401;
                answering[i] = Thread.ofPlatform().start(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException stopped) {
                        return;
                    }
                    lap.settled(Optional.of(status), true);
                });
            }
            go.countDown();
            Optional<Unauthorized> said = Optional.empty();
            while (said.isEmpty() && anyAlive(answering)) {
                said = lap.refused();
            }
            for (Thread thread : answering) {
                thread.join();
            }
            said = said.isPresent() ? said : lap.refused();
            assertThat(said).describedAs("attempt %d", attempt).isEmpty();
        }
    }

    private static void sendInTheLap(RefusedOpeningLap lap, int requests) {
        for (int i = 0; i < requests; i++) {
            lap.sentInTheLap();
        }
    }

    private static boolean anyAlive(Thread[] threads) {
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return true;
            }
        }
        return false;
    }
}
