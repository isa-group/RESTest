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

import io.restest.core.event.RunEvent;
import io.restest.core.execution.Interaction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * How a run went over the time it was given, worked out once for every report that shows it.
 *
 * <p>The totals of a run say how it ended: so many replies of 2xx, so many of 5xx. They cannot say
 * whether the API fell over at the start and recovered, or worked for half an hour and then stopped
 * answering. So the run's time is cut into equal slices - sixty, unless the settings say otherwise -
 * and the replies are counted by family of status code in each, which is what a report draws as a
 * timeline. The stretches with a purpose of their own, such as the round at the start that sends
 * every operation once, are kept by {@link Phases} and marked on the same time.
 *
 * <p>The slices are cut from the time the run was given, not the time it took, because they have to
 * be there before the first reply: counting every reply as it comes, without remembering any of
 * them, needs to know which slice each one goes in. A slice is that time divided by the number of
 * slices, rounded up to a whole millisecond. A run that ends early - stopped from outside, or after
 * the number of requests it was asked for - has fewer slices to show, and only the slices that began
 * before it ended are shown, so it is never drawn as a run that went on sending nothing.
 *
 * <p>A reply belongs to the slice its request was sent in, as a request belongs to the stretch it was
 * sent in. An answer that arrives after the time is up is still counted in the slice it was asked
 * in, and a request sent in the instant after the last slice is counted in the last one. So the
 * slices add up to the counts of the whole run - for a run that said it had begun before its first
 * reply came back, as every run of RESTest's own does. A reply heard before that has no slice to go
 * in, and is in none.
 *
 * <p>A run that never says it began has no time to cut: it shows no slices, and a time of nothing.
 *
 * <p>What it keeps is one small count for each slice - never more than the settings allow, ten
 * thousand at most - however long the run lasts. Written to from the one thread that delivers
 * announcements, so it keeps plain fields.
 */
final class Timeline {

    private final int mostIntervals;

    /** Where every slice is counted from. Empty until the run says it has begun. */
    private Instant startedAt;
    private Duration budget = Duration.ZERO;
    private Duration interval = Duration.ZERO;

    /** What came back in each slice, by family, one count for every slice of the run's time. */
    private final List<SortedMap<String, Integer>> slices = new ArrayList<>();

    /** The last slice something came back in, or none yet. */
    private int lastCounted = -1;

    private Instant endedAt;
    private Duration elapsed = Duration.ZERO;

    /**
     * A timeline for one run.
     *
     * @param mostIntervals how many slices the run's time is cut into, at least one; fewer only
     *     when the time is too short for that many slices of a millisecond
     */
    Timeline(int mostIntervals) {
        this.mostIntervals = mostIntervals;
    }

    /** Takes note of one announcement. Anything that is not about the run's time is ignored. */
    void on(RunEvent event) {
        switch (event) {
            case RunEvent.RunStarted started -> {
                // A run begins once; a second announcement of it would move every slice already
                // counted.
                if (startedAt == null) {
                    begin(started.at(), started.budget());
                }
            }
            case RunEvent.InteractionCompleted completed -> count(completed.interaction());
            case RunEvent.RunFinished finished -> {
                endedAt = finished.at();
                elapsed = finished.elapsed();
            }
            case RunEvent.OperationSkipped ignored -> {
                // Not about the run's time: an operation no request will be sent to.
            }
            case RunEvent.OperationSetAside ignored -> {
                // The same, for one the plan keeps the run away from.
            }
            case RunEvent.DocumentRead ignored -> {
                // Nor is what the document describes, said before anything was sent.
            }
            case RunEvent.DictionaryRead ignored -> {
                // Nor is a list of values, read before anything was sent.
            }
            case RunEvent.DictionaryRefused ignored -> {
                // The same.
            }
            case RunEvent.TestCasePlanned ignored -> {
                // A request decided on is counted once it has been answered, in the slice it was
                // sent in.
            }
            case RunEvent.FaultFound ignored -> {
                // A judgement about a reply already counted.
            }
            case RunEvent.ReplyNotJudged ignored -> {
                // The same.
            }
            case RunEvent.PhaseStarted ignored -> {
                // Kept where the stretches are, and marked on the same time by whoever draws it.
            }
            case RunEvent.PhaseFinished ignored -> {
                // The same.
            }
        }
    }

    private void begin(Instant at, Duration given) {
        // Counted in milliseconds, which any time a run can be given fits in, rather than in
        // nanoseconds, which overflow for a run given centuries. A slice is a whole number of them,
        // and never shorter than one.
        long millis = Math.max(1, given.toMillis());
        long length = Math.ceilDiv(millis, mostIntervals);
        long intervals = Math.ceilDiv(millis, length);
        startedAt = at;
        budget = given;
        interval = Duration.ofMillis(length);
        for (int slice = 0; slice < intervals; slice++) {
            slices.add(new TreeMap<>());
        }
    }

    private void count(Interaction interaction) {
        if (startedAt == null) {
            // Heard before the run said it had begun: there is nothing to count it from.
            return;
        }
        long since = Duration.between(startedAt, interaction.sentAt()).toMillis();
        int slice = (int) Math.min(slices.size() - 1, Math.max(0, since) / interval.toMillis());
        slices.get(slice).merge(StatusClass.of(interaction), 1, Integer::sum);
        lastCounted = Math.max(lastCounted, slice);
    }

    /**
     * When the run began, which is where the first slice begins. Worked out from when it ended and
     * how long it took, for a run that never said it began.
     */
    Instant startedAt() {
        if (startedAt != null) {
            return startedAt;
        }
        return endedAt == null ? Instant.EPOCH : endedAt.minus(elapsed);
    }

    /** How long the run was given, or nothing for a run that never said it began. */
    Duration budget() {
        return budget;
    }

    /** How long each slice is, or nothing for a run that never said it began. */
    Duration interval() {
        return interval;
    }

    /**
     * What came back in each slice, by family, from the first slice to the last one shown: every
     * slice that began before the run ended, and never short of the last one something came back
     * in. A slice nothing came back in is an empty count.
     */
    List<SortedMap<String, Integer>> intervals() {
        if (startedAt == null) {
            return List.of();
        }
        Duration ran = endedAt == null || endedAt.isBefore(startedAt)
                ? Duration.ZERO : Duration.between(startedAt, endedAt);
        // A slice began before the run ended when its start is earlier than the end, to the
        // nanosecond: a part of a millisecond counts as one.
        long ranFor = ran.toMillis() + (ran.toNanosPart() % 1_000_000 == 0 ? 0 : 1);
        int began = (int) Math.min(slices.size(), Math.ceilDiv(ranFor, interval.toMillis()));
        return slices.subList(0, Math.max(began, lastCounted + 1)).stream()
                .<SortedMap<String, Integer>>map(TreeMap::new)
                .toList();
    }
}
