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
package io.restest.core.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineStatisticsTest {

    @Test
    @DisplayName("a run that never started reads as nothing wasted, not everything wasted")
    void an_empty_run_is_not_reported_as_entirely_idle() {
        EngineStatistics none = EngineStatistics.none();

        assertThat(none.requestsSent()).isZero();
        assertThat(none.idleFraction()).isZero();
        assertThat(none.busy()).isEqualTo(Duration.ZERO);
        assertThat(none.meanResponseTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("idle time is reported as a share of the run, and the rest of it is busy")
    void idle_and_busy_split_the_wall_clock() {
        EngineStatistics statistics = new EngineStatistics(10, Duration.ofSeconds(20),
                Duration.ofSeconds(5), Duration.ofSeconds(30), 4, 8);

        assertThat(statistics.idleFraction()).isEqualTo(0.25);
        assertThat(statistics.busy()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("the total response time may exceed the run, because requests overlap")
    void mean_response_time_is_derived_from_the_total() {
        EngineStatistics statistics = new EngineStatistics(4, Duration.ofSeconds(10),
                Duration.ZERO, Duration.ofSeconds(20), 4, 4);

        assertThat(statistics.totalResponseTime()).isGreaterThan(statistics.wallClock());
        assertThat(statistics.meanResponseTime()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("idle time is part of the run, so it cannot exceed it")
    void idle_time_beyond_the_wall_clock_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EngineStatistics(1, Duration.ofSeconds(1),
                        Duration.ofSeconds(2), Duration.ZERO, 1, 1))
                .withMessageContaining("cannot exceed the wall clock");
    }

    @Test
    @DisplayName("negative counts and durations are refused")
    void negative_values_are_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EngineStatistics(-1, Duration.ZERO, Duration.ZERO,
                        Duration.ZERO, 0, 0))
                .withMessageContaining("requestsSent");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EngineStatistics(0, Duration.ofSeconds(-1), Duration.ZERO,
                        Duration.ZERO, 0, 0))
                .withMessageContaining("wallClock");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EngineStatistics(0, Duration.ZERO, Duration.ZERO,
                        Duration.ZERO, -1, 0))
                .withMessageContaining("peakConcurrency");
    }

    @Test
    @DisplayName("the printed form says how much of the run was idle, in plain percent")
    void to_string_states_the_idle_share() {
        EngineStatistics statistics = new EngineStatistics(2, Duration.ofSeconds(10),
                Duration.ofSeconds(1), Duration.ofSeconds(4), 2, 4);

        assertThat(statistics).hasToString(
                "EngineStatistics[2 requests in PT10S, idle 10%, mean response PT2S, "
                        + "peak concurrency 2, limit 4]");
    }
}
