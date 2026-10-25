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

/**
 * How much of what a run found is written out, and how much of it reaches the screen.
 *
 * <p>An API that is broken is broken every time it is asked, so a run of any length finds the same
 * fault over and over. Writing all of them out produces a file nobody can open and a screen nobody
 * can read, both made almost entirely of copies. These numbers are where that stops, and one of them
 * does the same for the list of operations a run could not test, which a large description can make
 * long. Every one of them but {@code timelineIntervals} is a limit on what is *quoted*: nothing here
 * changes what is counted, and a fault or an operation left out of the quoting is still counted
 * exactly and still named. {@code timelineIntervals} says how finely the report cuts the run's time
 * when it shows how the run went.
 *
 * @param writeUpsPerOperationAndKind how many faults of one kind, on one operation, are written out
 *     whole. The first proves the fault is real and can be repeated; the next two show what else
 *     was being sent when it happened. By the fifth, another near-identical copy teaches nobody
 *     anything
 * @param writeUpsInTotal and how many in the whole file, for a description that goes wrong in very
 *     many places
 * @param mostBodyBytesKept how much of any one request or reply body is quoted. This is what makes
 *     counting write-ups a real limit: without it the size of one is whatever the API felt like
 *     sending. What is kept says how long the whole body was, so a trimmed one is never mistaken
 *     for the API having sent less than it did
 * @param faultsShownOnTheConsole how many faults are printed in full before the screen stops being
 *     the right place for them. The count at the end is of all of them, printed or not
 * @param warningsShownOnTheConsole and how many of those may be warnings. A document that leaves out
 *     the ways its API refuses a request makes a warning of nearly every refusal, from the first
 *     requests on, and without this they would fill the screen before the first reply of 500
 *     arrived; with it, errors found later still have room
 * @param skippedOperationsShownOnTheConsole how many of the operations a run could not test are
 *     named on the screen, each with its reason, before the rest are only counted. The report file
 *     names every one of them, however many that is
 * @param detailsShownPerFault how many of a fault's particulars - each place where a reply
 *     disagreed with its shape, say - are printed under it on the screen before the rest are only
 *     counted
 * @param commonestStatusCodesListed how many of the status codes answered most the report lists,
 *     commonest first. Every reply is still counted by the class of its status
 * @param timelineIntervals how many equal stretches the run's time is cut into for showing how the
 *     run went: what came back in each. Sixty shows a run of a minute second by second and a run
 *     of an hour minute by minute, in a few kilobytes of the file. At least one, since a run always
 *     has some time, and at most ten thousand: a slice a run is told it cannot spare is still
 *     written out, so a number typed with a few zeros too many would otherwise fill the file with
 *     empty slices, and the memory with them before a request was sent
 */
public record ReportSettings(
        int writeUpsPerOperationAndKind,
        int writeUpsInTotal,
        long mostBodyBytesKept,
        int faultsShownOnTheConsole,
        int warningsShownOnTheConsole,
        int skippedOperationsShownOnTheConsole,
        int detailsShownPerFault,
        int commonestStatusCodesListed,
        int timelineIntervals) {

    /** The most slices a run's time can be cut into for showing how it went. */
    private static final int MOST_TIMELINE_INTERVALS = 10_000;

    private static final ReportSettings DEFAULTS =
            new ReportSettings(5, 1_000, 24L * 1024, 50, 10, 5, 10, 10, 60);

    public ReportSettings {
        atLeastNone(writeUpsPerOperationAndKind, "writeUpsPerOperationAndKind");
        atLeastNone(writeUpsInTotal, "writeUpsInTotal");
        atLeastNone(faultsShownOnTheConsole, "faultsShownOnTheConsole");
        atLeastNone(warningsShownOnTheConsole, "warningsShownOnTheConsole");
        atLeastNone(skippedOperationsShownOnTheConsole, "skippedOperationsShownOnTheConsole");
        atLeastNone(detailsShownPerFault, "detailsShownPerFault");
        atLeastNone(commonestStatusCodesListed, "commonestStatusCodesListed");
        if (timelineIntervals < 1 || timelineIntervals > MOST_TIMELINE_INTERVALS) {
            throw new IllegalArgumentException("timelineIntervals must be at least 1, since the "
                    + "run's time is always there to be shown, and at most "
                    + MOST_TIMELINE_INTERVALS + ", or the report fills with empty slices: "
                    + timelineIntervals);
        }
        if (mostBodyBytesKept < 0) {
            throw new IllegalArgumentException("mostBodyBytesKept cannot be negative; zero quotes "
                    + "no bodies at all, which is the least this can do: " + mostBodyBytesKept);
        }
    }

    /**
     * Zero is allowed for every limit on what is quoted, and means "quote none of these".
     *
     * <p>Somebody who wants counts and no quotations is asking for something reasonable, and every
     * one of these limits already says out loud when it stopped the report writing.
     */
    private static void atLeastNone(int value, String what) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " cannot be negative; zero writes none of "
                    + "them out, which is the least this can do: " + value);
        }
    }

    /** What a run does when nobody has said otherwise. */
    public static ReportSettings defaults() {
        return DEFAULTS;
    }
}
