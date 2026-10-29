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
package io.restest.exec;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.exec.EngineStatistics;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives the idle-time bookkeeping with a clock the test sets by hand, so that every number below is
 * exact rather than "about right if the machine was not busy".
 */
class EngineActivityTest {

    private final AtomicLong clock = new AtomicLong();

    /** An engine that keeps to no rate: its next turn is the moment it was built, long past. */
    private final EngineActivity activity = new EngineActivity(clock::get, () -> 0);

    /** When the next request may go, as the engine's rate says once a request has gone out. */
    private final AtomicLong turn = new AtomicLong();

    @Test
    @DisplayName("time spent before the first request is idle: that is the whole point of measuring")
    void the_wait_before_the_first_request_is_idle_time() {
        clock.set(seconds(30));

        EngineStatistics statistics = activity.snapshot(4);

        assertThat(statistics.wallClock()).isEqualTo(Duration.ofSeconds(30));
        assertThat(statistics.idle()).isEqualTo(Duration.ofSeconds(30));
        assertThat(statistics.idleFraction()).isEqualTo(1.0);
        assertThat(statistics.requestsSent()).isZero();
    }

    @Test
    @DisplayName("a request that never reached the network still counts as one the engine failed")
    void an_attempt_that_never_went_out_is_still_counted() {
        clock.set(seconds(2));
        activity.requestNeverSent();
        activity.requestNeverSent();

        EngineStatistics statistics = activity.snapshot(4);

        assertThat(statistics.requestsSent()).isEqualTo(2);
        assertThat(statistics.idle()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("time after the last answer counts as idle: the engine had nothing in flight")
    void the_gap_after_a_request_is_idle() {
        clock.set(seconds(1));
        activity.requestStarted();
        clock.set(seconds(3));
        activity.requestFinished(Duration.ofSeconds(2));
        clock.set(seconds(5));

        EngineStatistics statistics = activity.snapshot(4);

        assertThat(statistics.wallClock()).isEqualTo(Duration.ofSeconds(5));
        assertThat(statistics.idle())
                .describedAs("one second before the request, two after it")
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(statistics.busy()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("a gap between two requests is idle, and the time waiting on the API is not")
    void only_the_gaps_between_requests_are_idle() {
        clock.set(seconds(0));
        activity.requestStarted();
        clock.set(seconds(4));
        activity.requestFinished(Duration.ofSeconds(4));
        clock.set(seconds(5));
        activity.requestStarted();
        clock.set(seconds(6));
        activity.requestFinished(Duration.ofSeconds(1));

        EngineStatistics statistics = activity.snapshot(4);

        assertThat(statistics.wallClock()).isEqualTo(Duration.ofSeconds(6));
        assertThat(statistics.idle()).isEqualTo(Duration.ofSeconds(1));
        assertThat(statistics.requestsSent()).isEqualTo(2);
        assertThat(statistics.meanResponseTime()).isEqualTo(Duration.ofSeconds(2, 500_000_000));
    }

    @Test
    @DisplayName("overlapping requests are never idle, and the busiest moment is remembered")
    void overlapping_requests_record_a_peak_and_no_idle_time() {
        clock.set(seconds(0));
        activity.requestStarted();
        activity.requestStarted();
        activity.requestStarted();
        clock.set(seconds(1));
        activity.requestFinished(Duration.ofSeconds(1));
        activity.requestFinished(Duration.ofSeconds(1));
        activity.requestFinished(Duration.ofSeconds(1));

        EngineStatistics statistics = activity.snapshot(8);

        assertThat(statistics.peakConcurrency()).isEqualTo(3);
        assertThat(statistics.idle()).isEqualTo(Duration.ZERO);
        assertThat(statistics.totalResponseTime()).isEqualTo(Duration.ofSeconds(3));
        assertThat(statistics.concurrencyLimit()).isEqualTo(8);
    }

    @Test
    @DisplayName("a snapshot does not change afterwards")
    void a_snapshot_is_a_moment_not_a_view() {
        clock.set(seconds(1));
        activity.requestStarted();
        clock.set(seconds(2));
        activity.requestFinished(Duration.ofSeconds(1));

        EngineStatistics taken = activity.snapshot(4);
        clock.set(seconds(60));

        assertThat(taken.wallClock()).isEqualTo(Duration.ofSeconds(2));
        assertThat(activity.snapshot(4).wallClock()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("with a rate to keep to, the idle time before each turn is the rate's, and only that")
    void the_idle_time_before_a_turn_is_the_rates() {
        EngineActivity kept = new EngineActivity(clock::get, turn::get);
        clock.set(seconds(1));
        turn.set(seconds(11));
        kept.requestStarted();
        clock.set(seconds(3));
        kept.requestFinished(Duration.ofSeconds(2));
        clock.set(seconds(8));

        assertThat(kept.snapshot(4).idleKeepingToTheRate())
                .describedAs("from the answer to now, all of it before the next turn at 11 s; the "
                        + "second before the first request is the tool's")
                .isEqualTo(Duration.ofSeconds(5));

        clock.set(seconds(14));
        turn.set(seconds(24));
        kept.requestStarted();
        clock.set(seconds(15));
        kept.requestFinished(Duration.ofSeconds(1));

        EngineStatistics statistics = kept.snapshot(4);
        assertThat(statistics.idle()).isEqualTo(Duration.ofSeconds(12));
        assertThat(statistics.idleKeepingToTheRate())
                .describedAs("from 3 s to the turn at 11 s; the three seconds after it, the "
                        + "tool's own")
                .isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    @DisplayName("time with a request in flight is not idle, whatever the rate")
    void time_in_flight_is_not_the_rates() {
        EngineActivity kept = new EngineActivity(clock::get, turn::get);
        turn.set(seconds(10));
        kept.requestStarted();
        clock.set(seconds(9));
        kept.requestFinished(Duration.ofSeconds(9));
        clock.set(seconds(20));

        assertThat(kept.snapshot(4).idleKeepingToTheRate())
                .describedAs("one second, from the answer at 9 s to the turn at 10 s")
                .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("an engine that keeps to no rate counts none of its idle time as the rate's")
    void no_rate_no_idle_kept_to_it() {
        activity.requestStarted();
        clock.set(seconds(1));
        activity.requestFinished(Duration.ofSeconds(1));
        clock.set(seconds(30));

        assertThat(activity.snapshot(4).idleKeepingToTheRate()).isZero();
    }

    private static long seconds(long value) {
        return Duration.ofSeconds(value).toNanos();
    }
}
