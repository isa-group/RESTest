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
package io.restest.cli;

/**
 * The number {@code restest} leaves behind when it finishes, and what each one means.
 *
 * <p>Whatever ran the command - a person, a build server, a script looping over several APIs - reads
 * this number before it reads anything else, and usually instead of reading anything else. So the
 * five values here are a promise to those callers rather than an implementation detail: changing
 * what one of them means breaks somebody's pipeline silently, which is why they are written down in
 * one place with the reasoning attached.
 *
 * <p>The important distinction is between *the API is wrong* and *RESTest is wrong*. Finding a fault
 * is the tool working, and it answers 1 so that a build goes red when the software under test is
 * broken. Not being able to test anything at all, or breaking internally, are different answers,
 * because reacting to them by opening a bug report about the API would waste somebody's afternoon.
 *
 * <p>An operation that had to be skipped is none of these. Documents are written by people about
 * software that has changed since, so skipping part of one is ordinary; the run says what it
 * skipped and carries on.
 *
 * <p>Two more numbers are Java's rather than RESTest's, and are kept rather than replaced: the ones
 * a program stopped from outside ends with, which every shell and every build server already reads
 * as "interrupted". A run stopped that way writes what it found before it ends, and says it was cut
 * short; ending with 0 or 1 afterwards would let a run that was stopped half-way pass for one that
 * finished.
 */
final class ExitCode {

    /** The run finished and found nothing wrong. */
    static final int NO_FAULTS = 0;

    /** The run finished and found at least one fault. The API is what is wrong, not the tool. */
    static final int FAULTS_FOUND = 1;

    /**
     * The command line was wrong. This is the number the command-line framework itself uses for a
     * mistyped option, kept rather than replaced so that both kinds of "you asked for something
     * impossible" read the same.
     */
    static final int BAD_COMMAND_LINE = 2;

    /**
     * There was nothing to test: no operation in the document could be tried, or there is nowhere to
     * send the requests. Nothing was sent, so any report would be empty - and an empty report that
     * exits successfully reads as "this API is fine", which would be a lie.
     */
    static final int NOTHING_TO_TEST = 3;

    /**
     * RESTest itself went wrong. Something failed unexpectedly, part of the reporting broke, a
     * request was lost by the part of the tool that sends it, or an exchange had to be kept without
     * its details, so what was printed may be missing things. A wrong answer presented as a right one
     * is the single failure a testing tool cannot afford, so this is deliberately not reported as
     * either 0 or 1.
     */
    static final int TOOL_FAILED = 4;

    /**
     * The run was stopped from outside with Ctrl-C, and wrote what it had found until then. This is
     * the number Java ends such a program with, and the one it answers when the order to stop was
     * not a signal at all - in a test, or in a program running RESTest inside itself.
     */
    static final int INTERRUPTED = 130;

    /**
     * The run was stopped from outside with {@code kill} or {@code docker stop}, and wrote what it
     * had found until then. Java ends the program with this number itself; RESTest never answers it,
     * because nothing but the signal tells the two apart.
     */
    static final int TERMINATED = 143;

    private ExitCode() {
    }

    /**
     * Which of the above a finished run deserves.
     *
     * <p>Kept as one decision in one place rather than spread through the command, because it is the
     * part of RESTest other people's scripts depend on, and because the order the questions are
     * asked in is the whole of the meaning. "Was the run stopped from outside" comes first, because
     * a program stopped that way ends with Java's number whatever this says. "Did anything break on
     * our side" comes next, before "did we actually test anything", which comes before "was anything
     * wrong with the API" - so a broken report can never be reported as a clean bill of health, and
     * neither can a run that sent nothing.
     *
     * @param outcome what the loop did with the time
     * @param whatFailedOnOurSide how many things RESTest itself got wrong: listeners that threw
     *     while being told something, and judgements a rule failed to make. Added together on
     *     purpose - both mean some of this run was not done, and the answer to "did anything break
     *     on our side" is the same either way. Which of the two it was belongs in the words printed
     *     alongside, not in the number a script reads
     * @param eventsNeverHeard how many announcements never reached the listeners
     * @param keptWithoutTheirDetails how many exchanges had to be kept without their details,
     *     because a key could not be picked out of them: every report and the stored run have those
     *     exchanges with next to nothing in them
     * @param faults how many faults were reported
     * @return the number the command should answer with
     */
    static int of(RunLoop.Outcome outcome, long whatFailedOnOurSide, long eventsNeverHeard,
            long keptWithoutTheirDetails, int faults) {
        if (outcome != null && outcome.cutShort()) {
            return INTERRUPTED;
        }
        if (whatFailedOnOurSide > 0 || eventsNeverHeard > 0 || keptWithoutTheirDetails > 0) {
            return TOOL_FAILED;
        }
        // Before asking whether anything was tested: a run in which RESTest lost every request it
        // sent tested nothing, and the reason is RESTest, not the address it was pointed at.
        if (outcome != null && outcome.lost() > 0) {
            return TOOL_FAILED;
        }
        if (outcome == null || outcome.sent() == 0 || outcome.nothingAnswered()) {
            return NOTHING_TO_TEST;
        }
        return faults > 0 ? FAULTS_FOUND : NO_FAULTS;
    }
}
