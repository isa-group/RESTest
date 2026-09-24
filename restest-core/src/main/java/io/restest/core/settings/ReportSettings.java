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
 * can read, both made almost entirely of copies. These numbers are where that stops, and the last of
 * them does the same for the list of operations a run could not test, which a large description can
 * make long. Every one of them is a limit on what is *quoted*: nothing here changes what is counted,
 * and a fault or an operation left out of the quoting is still counted exactly and still named.
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
 * @param skippedOperationsShownOnTheConsole how many of the operations a run could not test are
 *     named on the screen, each with its reason, before the rest are only counted. The report file
 *     names every one of them, however many that is
 */
public record ReportSettings(
        int writeUpsPerOperationAndKind,
        int writeUpsInTotal,
        long mostBodyBytesKept,
        int faultsShownOnTheConsole,
        int skippedOperationsShownOnTheConsole) {

    private static final ReportSettings DEFAULTS =
            new ReportSettings(5, 1_000, 24L * 1024, 50, 5);

    public ReportSettings {
        atLeastNone(writeUpsPerOperationAndKind, "writeUpsPerOperationAndKind");
        atLeastNone(writeUpsInTotal, "writeUpsInTotal");
        atLeastNone(faultsShownOnTheConsole, "faultsShownOnTheConsole");
        atLeastNone(skippedOperationsShownOnTheConsole, "skippedOperationsShownOnTheConsole");
        if (mostBodyBytesKept < 0) {
            throw new IllegalArgumentException("mostBodyBytesKept cannot be negative; zero quotes "
                    + "no bodies at all, which is the least this can do: " + mostBodyBytesKept);
        }
    }

    /**
     * Zero is allowed everywhere here, and means "quote none of these".
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
