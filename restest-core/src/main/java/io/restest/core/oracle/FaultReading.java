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
package io.restest.core.oracle;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How RESTest reads a kind of fault for a developer: how serious it is, what to call it, and where
 * to look.
 *
 * <p>Each kind of fault has a number and a name from a catalogue several testing tools share -
 * {@code F200}, "Schema Violation: Received A Response From API With A Structure/Data That Is Not
 * Matching Its Schema" - which is right for putting one tool's count beside another's and wrong for
 * somebody reading a run at the end of the day. This is what that person reads instead:
 *
 * <ul>
 *   <li>a {@link Severity}: an error when the API failed at its job, a warning when the API and its
 *       document disagree and the document may be the one out of date;
 *   <li>a short title in RESTest's own words - "Reply off schema" - which is also, word for word,
 *       the name of the switch that turns the check off ({@code checks.replyOffSchema});
 *   <li>one line on what to look at, and whether the document may be what needs fixing.
 * </ul>
 *
 * <p>Every kind RESTest reports is listed here, in one place. A kind somebody else's rule reports,
 * and which is not listed, is read as an error under its own name - or its number, if it has no
 * name - with nothing to advise: every fault ended a run as a failure before kinds had a severity,
 * and a rule written to report something is presumably meant to.
 *
 * <p>Kinds are told apart by their number, which is what a fault is within the catalogue a run
 * counts by; {@link FaultCategory} describes it.
 *
 * @param severity whether the API failed at its job or answered something its document does not
 *     describe
 * @param title    what the kind is called on the screen and in the report, in a few plain words
 * @param advice   what to look at, and whether the document may be the one to fix; absent for a
 *     kind RESTest does not know
 */
public record FaultReading(Severity severity, String title, Optional<String> advice) {

    private static final Map<Integer, FaultReading> BY_CODE = Map.of(
            WfcFault.HTTP_STATUS_500.code(), known(Severity.ERROR, "Internal server error",
                    "look in the API's log for what failed while it handled the request; the "
                            + "document is not what to fix"),
            WfcFault.HTTP_OTHER_STATUS_5XX.code(), known(Severity.ERROR, "Other server error",
                    "look at what the API sits behind or calls - a gateway, a database, another "
                            + "service - as well as at its code; the document is not what to fix"),
            WfcFault.SCHEMA_VALIDATION_BYPASS.code(), known(Severity.ERROR,
                    "Invalid data accepted",
                    "make the API refuse what its document forbids; where the document forbids "
                            + "more than the API should, loosen the document instead"),
            WfcFault.SECURITY_CALL_TIMEOUT.code(), known(Severity.ERROR, "No reply in time",
                    "look at what the operation waits on - a database, another service, a lock; "
                            + "an API known to be slow gets longer with engine.readTimeout"),
            WfcFault.SCHEMA_INVALID_RESPONSE.code(), known(Severity.WARNING, "Reply off schema",
                    "the API and its document disagree about the reply's body: compare the "
                            + "places listed with the document, and fix whichever is wrong"),
            WfcFault.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED.code(), known(Severity.WARNING,
                    "Not implemented",
                    "implement the operation, or take it out of the document"),
            RestestFault.UNDECLARED_STATUS_CODE.code(), known(Severity.WARNING,
                    "Undeclared status code",
                    "declare the status code for the operation in the document, or stop the API "
                            + "answering it"),
            RestestFault.UNDECLARED_CONTENT_TYPE.code(), known(Severity.WARNING,
                    "Undeclared content type",
                    "declare the content type for that status code in the document, or have the "
                            + "API send the one declared"));

    public FaultReading {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(advice, "advice");
        if (title.isBlank()) {
            throw new IllegalArgumentException("a kind of fault needs a title somebody can read");
        }
    }

    /**
     * How RESTest reads this kind of fault.
     *
     * @param kind the kind, told apart from the others by its number
     * @return what RESTest calls it, how serious it is and where to look; for a kind it does not
     *     know, an error under the kind's own name, or its number when it has no name, with no
     *     advice
     */
    public static FaultReading of(FaultCategory kind) {
        Objects.requireNonNull(kind, "kind");
        FaultReading reading = BY_CODE.get(kind.code());
        if (reading != null) {
            return reading;
        }
        // A rule somebody else wrote may name its kind badly; that must not stop a report from
        // saying what it found.
        String name = kind.descriptiveName();
        return new FaultReading(Severity.ERROR,
                name == null || name.isBlank() ? "F" + kind.code() : name, Optional.empty());
    }

    private static FaultReading known(Severity severity, String title, String advice) {
        return new FaultReading(severity, title, Optional.of(advice));
    }
}
