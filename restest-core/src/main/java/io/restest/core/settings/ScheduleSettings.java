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
 * The first four numbers are the brakes on that, and on how long it waits. The first says how many
 * requests may be waiting for an answer at once, so the tool cannot run so far ahead that it is
 * inventing requests nobody will have time to send. The second says how many announcements - "this
 * request went out", "this reply came back" - may be waiting to reach the reports before the run
 * pauses to let them catch up, which is what stops a long run against a fast API ending in an
 * out-of-memory failure rather than a report. The third says how long, after the time is up, to
 * keep waiting for answers to requests that had already gone out. The fourth says the same for a
 * run somebody stops from outside - with Ctrl-C, say - which waits much less, because whoever
 * stopped it is waiting too.
 *
 * <p>Two more say how a run stopped from outside ends: how long it may spend writing what it found
 * once it has stopped waiting for its answers, and how long it then gives its directory to say what
 * was and was not written, when writing did not finish in time. Two others let a run be given a
 * size rather than a length: told to stop after so many requests, it ends when they have been
 * answered, however fast or slow the machine it runs on.
 *
 * <p>The ones about the opening lap are about how a run begins. Before anything is chosen by
 * chance, a run can send every operation once, each with the request the API is most likely to
 * accept: it first asks for the lists of what is there, then creates, then asks for single things,
 * then changes them, and deletes last. Each of those steps waits for the answers to the one before,
 * so that an identifier one step has just been handed is there for the next. That first round is
 * what gets an API's operations answered in the first seconds of a run rather than whenever chance
 * gets round to them. It can be switched off, and how long a step waits is bounded, so one request
 * the API never answers cannot hold the start of a run up.
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
 * @param interruptGrace how long a run stopped from outside waits, from the moment it is stopped,
 *     for answers to requests that had already gone out. What has not come back by then counts as
 *     never answered, and the run goes on to write what it found. Short, because a run stopped that
 *     way has only a few seconds before it is ended outright - ten, when a container is stopped
 * @param openingLap whether a run begins by sending every operation once, each with the request
 *     most likely to be accepted, before anything is chosen by chance
 * @param openingLapPatience how long one step of that first round waits for the answers to the step
 *     before it, and for what they said to be taken in, before going on anyway. Zero never waits
 * @param stopAfterRequests whether a run stops once it has sent a set number of requests, before
 *     its budget is spent. Off, the budget alone says when a run ends. On, a run of a known size
 *     can be asked for, which ends as soon as the API has answered rather than when a clock says,
 *     and sends the same requests on a slow machine as on a fast one
 * @param requestsBeforeStopping how many requests that is. The run still waits for the answers to
 *     the last of them, as it does at the end of its budget
 * @param interruptWritingGrace how long a run stopped from outside is given to write what it
 *     found, once it has stopped waiting for its answers, before the program is let end without
 *     it. Writing takes a fraction of a second; this is how long to wait for writing that has got
 *     stuck before saying so, since whoever stopped the run is about to end it outright: a
 *     container is given ten seconds in all
 * @param interruptLookingGrace how long such a run, when it ran out of time to write, gives its
 *     directory to say what is in it before saying that the directory did not answer. A directory
 *     answers at once, unless it is the reason the writing ran out of time
 */
public record ScheduleSettings(
        int workAheadFactor,
        int announcementsAllowedToPileUp,
        Duration stragglerGrace,
        Duration interruptGrace,
        boolean openingLap,
        Duration openingLapPatience,
        boolean stopAfterRequests,
        int requestsBeforeStopping,
        Duration interruptWritingGrace,
        Duration interruptLookingGrace) {

    private static final ScheduleSettings DEFAULTS = new ScheduleSettings(2, 1_000,
            Duration.ofSeconds(10), Duration.ofSeconds(2), true, Duration.ofSeconds(2),
            false, 1_000, Duration.ofSeconds(5), Duration.ofSeconds(1));

    public ScheduleSettings {
        Objects.requireNonNull(stragglerGrace, "stragglerGrace");
        Objects.requireNonNull(interruptGrace, "interruptGrace");
        Objects.requireNonNull(openingLapPatience, "openingLapPatience");
        Objects.requireNonNull(interruptWritingGrace, "interruptWritingGrace");
        Objects.requireNonNull(interruptLookingGrace, "interruptLookingGrace");
        if (requestsBeforeStopping < 1) {
            throw new IllegalArgumentException("requestsBeforeStopping must be at least 1, since a "
                    + "run that stops before its first request tests nothing: "
                    + requestsBeforeStopping);
        }
        if (interruptWritingGrace.isNegative()) {
            throw new IllegalArgumentException("interruptWritingGrace cannot be negative; zero "
                    + "means a run stopped from outside is given no time to write what it found: "
                    + interruptWritingGrace);
        }
        if (interruptLookingGrace.isNegative()) {
            throw new IllegalArgumentException("interruptLookingGrace cannot be negative; zero "
                    + "means the directory is never asked what it holds: " + interruptLookingGrace);
        }
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
        if (interruptGrace.isNegative()) {
            throw new IllegalArgumentException("interruptGrace cannot be negative; zero means a run "
                    + "stopped from outside stops waiting at once: " + interruptGrace);
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
