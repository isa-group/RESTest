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
package io.restest.core.settings;

import java.time.Duration;
import java.util.Objects;

/**
 * How far ahead of the API a run is allowed to work, how long it waits at the end, and how it
 * begins.
 *
 * <p>A run invents a request, sends it, and moves on to the next one without waiting for the reply.
 * The first three numbers are the brakes on that. The first says how many requests may be waiting
 * for an answer at once, so the tool cannot run so far ahead that it is inventing requests nobody
 * will have time to send. The second says how many announcements - "this request went out", "this
 * reply came back" - may be waiting to reach the reports before the run pauses to let them catch up,
 * which is what stops a long run against a fast API ending in an out-of-memory failure rather than
 * a report. The third says how long, after the time is up, to keep waiting for answers to requests
 * that had already gone out.
 *
 * <p>The last two are about how a run begins. Before anything is chosen by chance, a run can send
 * every operation once, each with the request the API is most likely to accept: it first asks for
 * the lists of what is there, then creates, then asks for single things, then changes them, and
 * deletes last. Each of those steps waits for the answers to the one before, so that an identifier
 * one step has just been handed is there for the next. That first round is what gets an API's
 * operations answered in the first seconds of a run rather than whenever chance gets round to them.
 * It can be switched off, and how long a step waits is bounded, so one request the API never
 * answers cannot hold the start of a run up.
 *
 * @param workAheadFactor how many requests may be waiting for an answer at once, as a multiple of
 *     the most the engine will ever have in flight. Above one, so the engine is never left with a
 *     free slot while the next request is still being invented
 * @param announcementsAllowedToPileUp how many announcements may be waiting to reach the reports
 *     before the run pauses. Pausing is counted as time the tool wasted, and reported
 * @param stragglerGrace how much longer than the engine's own patience to wait, after the deadline,
 *     for answers to requests that had already gone out. The engine gives up on a request by
 *     itself, so this only has to outlast that; it exists so a run cannot hang for ever on an API
 *     that never replies
 * @param openingLap whether a run begins by sending every operation once, each with the request
 *     most likely to be accepted, before anything is chosen by chance
 * @param openingLapPatience how long one step of that first round waits for the answers to the step
 *     before it, and for what they said to be taken in, before going on anyway. Zero never waits
 */
public record ScheduleSettings(
        int workAheadFactor,
        int announcementsAllowedToPileUp,
        Duration stragglerGrace,
        boolean openingLap,
        Duration openingLapPatience) {

    private static final ScheduleSettings DEFAULTS = new ScheduleSettings(2, 1_000,
            Duration.ofSeconds(10), true, Duration.ofSeconds(2));

    public ScheduleSettings {
        Objects.requireNonNull(stragglerGrace, "stragglerGrace");
        Objects.requireNonNull(openingLapPatience, "openingLapPatience");
        if (workAheadFactor < 1) {
            throw new IllegalArgumentException("workAheadFactor must be at least 1, since a run "
                    + "that may not work ahead of the engine at all leaves it idle between "
                    + "requests: " + workAheadFactor);
        }
        if (announcementsAllowedToPileUp < 1) {
            throw new IllegalArgumentException("announcementsAllowedToPileUp must be at least 1, "
                    + "or nothing could ever be announced: " + announcementsAllowedToPileUp);
        }
        if (stragglerGrace.isNegative()) {
            throw new IllegalArgumentException("stragglerGrace cannot be negative; zero means the "
                    + "run stops waiting the moment the time is up: " + stragglerGrace);
        }
        if (openingLapPatience.isNegative()) {
            throw new IllegalArgumentException("openingLapPatience cannot be negative; zero means "
                    + "each step of the opening lap goes on without waiting for the one before: "
                    + openingLapPatience);
        }
    }

    /** What a run does when nobody has said otherwise. */
    public static ScheduleSettings defaults() {
        return DEFAULTS;
    }
}
