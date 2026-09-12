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
    private final EngineActivity activity = new EngineActivity(clock::get);

    @Test
    @DisplayName("an engine nobody has asked for anything has not been idle, it has not started")
    void time_before_the_first_request_is_nobody_s_waste() {
        clock.set(seconds(30));

        EngineStatistics statistics = activity.snapshot(4);

        assertThat(statistics.wallClock()).isEqualTo(Duration.ZERO);
        assertThat(statistics.idle()).isEqualTo(Duration.ZERO);
        assertThat(statistics.requestsSent()).isZero();
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

        assertThat(statistics.wallClock()).isEqualTo(Duration.ofSeconds(4));
        assertThat(statistics.idle()).isEqualTo(Duration.ofSeconds(2));
        assertThat(statistics.idleFraction()).isEqualTo(0.5);
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

        assertThat(taken.wallClock()).isEqualTo(Duration.ofSeconds(1));
        assertThat(activity.snapshot(4).wallClock()).isEqualTo(Duration.ofSeconds(59));
    }

    private static long seconds(long value) {
        return Duration.ofSeconds(value).toNanos();
    }
}
