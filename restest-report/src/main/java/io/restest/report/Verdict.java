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

/**
 * What a finished run amounts to, in one word: whether it found errors in the API, found none, or
 * could not say because nothing was tested or the run was stopped.
 *
 * <p>A report writes it where a person or a program looks first, and the number the command ends
 * with is worked out from the same answer, so the two cannot disagree: a run whose report says the
 * API answered nothing does not end as one that found errors in it, however many requests went
 * unanswered. The one thing it cannot know is whether RESTest itself went wrong after the report
 * was written; the command's number says that, and only that, on its own.
 *
 * <p>The questions are asked in an order, and the order is the meaning: a run stopped from outside
 * is said to have been stopped whatever it found until then; a run that sent nothing, or was
 * answered by nothing, tested nothing, so its faults say nothing about the API; only then do errors,
 * and warnings when the run was told they fail it, decide.
 */
public enum Verdict {

    /** Stopped from outside - with Ctrl-C, say - before the time it was given ran out. */
    STOPPED("stopped"),

    /**
     * Not one request went out: none could be built from the document, or the time ran out before
     * the first. The screen says which.
     */
    NOTHING_TESTED("nothingTested"),

    /** Requests went out and not one of them was answered. */
    NOTHING_ANSWERED("nothingAnswered"),

    /** At least one error was found in the API. */
    ERRORS_FOUND("errorsFound"),

    /** No error, but warnings, and the run was told that warnings fail it. */
    FAILED_ON_WARNINGS("failedOnWarnings"),

    /** No error was found. There may have been warnings, which do not fail a run unless told to. */
    NO_ERRORS_FOUND("noErrorsFound");

    private final String word;

    Verdict(String word) {
        this.word = word;
    }

    /** The word a report writes for it. */
    public String word() {
        return word;
    }

    /**
     * The verdict a run deserves.
     *
     * @param stopped whether it was stopped from outside before its time ran out
     * @param sent how many requests went out
     * @param answered how many of them came back with a reply
     * @param errors how many errors were found
     * @param warnings how many warnings were found
     * @param warningsFail whether the run was told that warnings fail it, as errors do
     * @return the verdict
     */
    public static Verdict of(boolean stopped, long sent, long answered, long errors, long warnings,
            boolean warningsFail) {
        if (stopped) {
            return STOPPED;
        }
        if (sent == 0) {
            return NOTHING_TESTED;
        }
        if (answered == 0) {
            return NOTHING_ANSWERED;
        }
        if (errors > 0) {
            return ERRORS_FOUND;
        }
        return warningsFail && warnings > 0 ? FAILED_ON_WARNINGS : NO_ERRORS_FOUND;
    }
}
