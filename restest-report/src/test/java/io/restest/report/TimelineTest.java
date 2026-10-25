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
package io.restest.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.event.RunEvent;
import io.restest.core.execution.Interaction;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimelineTest {

    private static final Instant START = Instant.parse("2026-10-25T10:00:00Z");

    private final Timeline timeline = new Timeline(60);

    @Test
    @DisplayName("a minute is cut into sixty slices of a second")
    void a_minute_is_sixty_seconds() {
        ran(Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(timeline.budget()).isEqualTo(Duration.ofMinutes(1));
        assertThat(timeline.interval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(timeline.intervals()).hasSize(60);
        assertThat(timeline.startedAt()).isEqualTo(START);
    }

    @Test
    @DisplayName("a slice is rounded up to a whole millisecond, so ten seconds are sixty slices of "
            + "167 ms, the last ending after the time does")
    void a_slice_is_a_whole_number_of_milliseconds() {
        ran(Duration.ofSeconds(10), Duration.ofSeconds(10));

        assertThat(timeline.interval()).isEqualTo(Duration.ofMillis(167));
        assertThat(timeline.intervals()).hasSize(60);
    }

    @Test
    @DisplayName("a slice is never shorter than a millisecond, so thirty milliseconds are thirty "
            + "slices")
    void a_slice_is_at_least_a_millisecond() {
        ran(Duration.ofMillis(30), Duration.ofMillis(30));

        assertThat(timeline.interval()).isEqualTo(Duration.ofMillis(1));
        assertThat(timeline.intervals()).hasSize(30);
    }

    @Test
    @DisplayName("a run given centuries is cut like any other, and counts its replies")
    void a_run_given_centuries_is_cut_like_any_other() {
        Duration aThousandYears = Duration.ofDays(365L * 1_000);
        timeline.on(started(aThousandYears));
        completed(Runs.sentAt(START.plusSeconds(1), 200));
        timeline.on(finishedAfter(Duration.ofMinutes(1)));

        assertThat(timeline.interval()).isEqualTo(Duration.ofMillis(
                Math.ceilDiv(aThousandYears.toMillis(), 60)));
        assertThat(timeline.intervals()).containsExactly(new TreeMap<>(Map.of("2xx", 1)));
    }

    @Test
    @DisplayName("a timeline told to cut the time into four slices cuts it into four")
    void the_number_of_slices_is_what_it_is_told() {
        Timeline four = new Timeline(4);
        four.on(started(Duration.ofMinutes(1)));
        four.on(finishedAfter(Duration.ofMinutes(1)));

        assertThat(four.interval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(four.intervals()).hasSize(4);
    }

    @Test
    @DisplayName("a reply counts in the slice its request was sent in, by family, and one answered "
            + "after the time was up still counts where it was asked")
    void a_reply_counts_where_its_request_was_sent() {
        timeline.on(started(Duration.ofMinutes(1)));
        completed(Runs.sentAt(START.plusMillis(500), 200));
        completed(Runs.sentAt(START.plusMillis(700), 404));
        completed(Runs.sentAt(START.plusMillis(900), 201));
        completed(Runs.sentAt(START.plusSeconds(30).plusMillis(200), 500));
        // Sent with half a second to go, and given up on five seconds later, after the time was up.
        completed(Runs.unansweredSentAt(START.plusSeconds(59).plusMillis(500),
                Duration.ofSeconds(5)));
        // Sent just before a slice ended, and answered in the next.
        completed(Runs.sentAt(START.plusMillis(990), Duration.ofMillis(210), 204));
        timeline.on(finishedAfter(Duration.ofSeconds(65)));

        List<SortedMap<String, Integer>> slices = timeline.intervals();
        assertThat(slices).hasSize(60);
        assertThat(slices.get(0)).containsExactly(Map.entry("2xx", 3), Map.entry("4xx", 1));
        assertThat(slices.get(30)).containsExactly(Map.entry("5xx", 1));
        assertThat(slices.get(59)).containsExactly(Map.entry("noReply", 1));
        assertThat(slices.get(1))
                .describedAs("a slice nothing came back in is shown, and is empty")
                .isEmpty();
    }

    @Test
    @DisplayName("a slice holds its beginning and not its end; a request sent before the start "
            + "counts in the first slice, and one sent once the time was up in the last")
    void the_edges_of_a_slice() {
        timeline.on(started(Duration.ofMinutes(1)));
        completed(Runs.sentAt(START.plusSeconds(1), 200));
        completed(Runs.sentAt(START.minusMillis(5), 201));
        completed(Runs.sentAt(START.plusSeconds(60), 202));
        completed(Runs.sentAt(START.plusSeconds(75), 203));
        timeline.on(finishedAfter(Duration.ofSeconds(80)));

        List<SortedMap<String, Integer>> slices = timeline.intervals();
        assertThat(slices.get(0)).containsExactly(Map.entry("2xx", 1));
        assertThat(slices.get(1)).containsExactly(Map.entry("2xx", 1));
        assertThat(slices.get(59)).containsExactly(Map.entry("2xx", 2));
    }

    @Test
    @DisplayName("a run that ends early shows the slices that began before it ended, and none of "
            + "the time it did not use")
    void a_run_that_ends_early_shows_only_what_it_used() {
        ran(Duration.ofMinutes(1), Duration.ofSeconds(15));

        assertThat(timeline.intervals())
                .describedAs("the slice beginning at 15 s began as the run ended, not before")
                .hasSize(15);

        Timeline cutShort = new Timeline(60);
        cutShort.on(started(Duration.ofMinutes(1)));
        cutShort.on(finishedAfter(Duration.ofMillis(15_001)));
        assertThat(cutShort.intervals()).hasSize(16);
    }

    @Test
    @DisplayName("the slices shown reach the last one something came back in, even one the run "
            + "said it had ended before")
    void the_slices_reach_the_last_reply() {
        timeline.on(started(Duration.ofMinutes(1)));
        completed(Runs.sentAt(START.plusSeconds(8), 200));
        timeline.on(finishedAfter(Duration.ofSeconds(5)));

        assertThat(timeline.intervals()).hasSize(9);
    }

    @Test
    @DisplayName("a reply heard before the run said it had begun is in no slice")
    void a_reply_before_the_start_is_in_no_slice() {
        completed(Runs.sentAt(START.plusSeconds(1), 200));
        timeline.on(started(Duration.ofMinutes(1)));
        timeline.on(finishedAfter(Duration.ofMinutes(1)));

        assertThat(timeline.intervals()).hasSize(60)
                .allSatisfy(slice -> assertThat(slice).isEmpty());
    }

    @Test
    @DisplayName("a run that says twice that it has begun keeps the first, so the slices already "
            + "counted stay where they are")
    void a_second_start_is_ignored() {
        timeline.on(started(Duration.ofMinutes(1)));
        timeline.on(Runs.started(START.plusSeconds(30), Duration.ofSeconds(10)));
        timeline.on(finishedAfter(Duration.ofMinutes(1)));

        assertThat(timeline.startedAt()).isEqualTo(START);
        assertThat(timeline.interval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("a run that never said it began has no time to cut: no slices, a time of nothing, "
            + "and a start worked out from its end")
    void a_run_that_never_began_has_no_slices() {
        completed(Runs.sentAt(START.plusSeconds(1), 200));
        timeline.on(finishedAfter(Duration.ofSeconds(12)));

        assertThat(timeline.budget()).isEqualTo(Duration.ZERO);
        assertThat(timeline.interval()).isEqualTo(Duration.ZERO);
        assertThat(timeline.intervals()).isEmpty();
        assertThat(timeline.startedAt()).isEqualTo(START);
    }

    /** A run of this much time that ended after this long, with nothing sent. */
    private void ran(Duration budget, Duration took) {
        timeline.on(started(budget));
        timeline.on(finishedAfter(took));
    }

    private static RunEvent.RunStarted started(Duration budget) {
        return Runs.started(START, budget);
    }

    private static RunEvent.RunFinished finishedAfter(Duration took) {
        return new RunEvent.RunFinished(START.plus(took), took, Runs.engine());
    }

    /** Announced when it was over, as a run announces it: once the reply came or was given up on. */
    private void completed(Interaction interaction) {
        timeline.on(new RunEvent.InteractionCompleted(
                interaction.sentAt().plus(interaction.elapsed()), interaction));
    }
}
