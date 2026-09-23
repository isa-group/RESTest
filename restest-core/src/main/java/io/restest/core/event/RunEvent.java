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
package io.restest.core.event;

import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Something that happened during a run, announced so that anyone interested can react.
 *
 * <p>A run does not call the report, the statistics or the stored file directly. It says what it is
 * doing - "this test was planned", "this attempt finished", "this is a fault" - and whoever is
 * listening does something about it. Adding another kind of report therefore means writing a
 * listener, not changing how a run works.
 *
 * <p>The list is closed, which is what lets a listener handle every kind of event and be told by
 * the compiler if a new one is ever added, rather than silently ignoring it.
 */
public sealed interface RunEvent {

    /** When it happened. */
    Instant at();

    /**
     * A run has begun against a particular API.
     *
     * @param at      when it began
     * @param api     the title the specification gives the API
     * @param baseUrl where the requests are being sent
     */
    record RunStarted(Instant at, String api, String baseUrl) implements RunEvent {
        public RunStarted {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(api, "api");
            Objects.requireNonNull(baseUrl, "baseUrl");
        }
    }

    /**
     * One of the API's operations will not be tried in this run, and this is why.
     *
     * <p>A report names it beside what the run found, so that "nothing wrong" is not read as
     * covering an operation nobody asked about. The reason is usually something the tool cannot do
     * yet, such as a file upload, rather than a mistake in the description.
     *
     * <p>Not said of an operation the plan asked the run to leave alone. That one was not skipped;
     * it was never wanted.
     *
     * @param at        when it was said
     * @param operation the operation that will not be tried
     * @param reason    why not, in the words a report prints
     */
    record OperationSkipped(Instant at, OperationId operation, String reason)
            implements RunEvent {
        public OperationSkipped {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("an operation that is skipped is skipped for a "
                        + "reason, which a report prints");
            }
        }
    }

    /** A request has been decided on, but not yet sent. */
    record TestCasePlanned(Instant at, TestCase testCase) implements RunEvent {
        public TestCasePlanned {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(testCase, "testCase");
        }
    }

    /**
     * An attempt is over: the request went out and either a reply came back, or something went
     * wrong on the way. This is the event oracles judge and the stored file records.
     *
     * <p>The request going out and the reply coming back are one event rather than two, because an
     * attempt is only describable once it has finished - and an attempt that was sent and never
     * answered is already one of the things an interaction can be.
     */
    record InteractionCompleted(Instant at, Interaction interaction) implements RunEvent {
        public InteractionCompleted {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(interaction, "interaction");
        }
    }

    /** An oracle has decided something was wrong, and said what. */
    record FaultFound(Instant at, Finding finding) implements RunEvent {
        public FaultFound {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(finding, "finding");
        }
    }

    /**
     * A stretch of the run with a purpose of its own has begun - the round at the start that sends
     * every operation once, for instance.
     *
     * <p>Every request planned from now until the matching {@link PhaseFinished} belongs to it,
     * which is how a report can say what that stretch achieved: how long it took, how many requests
     * it sent, and how many operations it got an answer from.
     *
     * @param at    when it began
     * @param phase what it is called, as a report would print it
     */
    record PhaseStarted(Instant at, String phase) implements RunEvent {
        public PhaseStarted {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(phase, "phase");
            if (phase.isBlank()) {
                throw new IllegalArgumentException("a stretch of the run has a name, which a "
                        + "report prints");
            }
        }
    }

    /**
     * That stretch of the run is over.
     *
     * @param at       when it ended
     * @param phase    what it is called, the same name it began with
     * @param cutShort whether it ended before it had done everything it set out to do - usually
     *                 because the time ran out, though a run stopped any other way ends it the same
     *                 way
     */
    record PhaseFinished(Instant at, String phase, boolean cutShort) implements RunEvent {
        public PhaseFinished {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(phase, "phase");
            if (phase.isBlank()) {
                throw new IllegalArgumentException("a stretch of the run has a name, which a "
                        + "report prints");
            }
        }
    }

    /**
     * The run is over. Carries how long it took and what the engine saw, so that a report can say
     * how much of the time was spent waiting for the API rather than working.
     */
    record RunFinished(Instant at, Duration elapsed, EngineStatistics engine) implements RunEvent {
        public RunFinished {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(elapsed, "elapsed");
            Objects.requireNonNull(engine, "engine");
            if (elapsed.isNegative()) {
                throw new IllegalArgumentException("a run cannot take less time than none: "
                        + elapsed);
            }
        }
    }
}
