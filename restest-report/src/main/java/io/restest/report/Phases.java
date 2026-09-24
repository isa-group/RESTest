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
import io.restest.core.execution.TestCaseId;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What each stretch of a run achieved, worked out once for every report that shows it.
 *
 * <p>A run can begin with a round of its own, before anything is chosen by chance: every operation
 * sent once, each with the request the API is most likely to accept. Whoever reads the results
 * wants to know what that round cost and what it bought - how long it took, how many requests it
 * sent, how many of the API's operations answered with a success - so the run announces where such
 * a stretch begins and ends, and this keeps the tally.
 *
 * <p>A request belongs to the stretch it was sent in, not the one it happened to be answered in. The
 * answer to the last request of a stretch can arrive after the next stretch has begun, or after the
 * time has run out, and it is still counted where it belongs. Only the requests still waiting for an
 * answer are remembered, so however long a run lasts, what this keeps is never more than what is in
 * flight.
 *
 * <p>Kept here, once, for the same reason the count of server failures is: every report showing
 * this has to show the same numbers, and the way to guarantee that is one place that works them
 * out.
 */
final class Phases {

    private static final int LOWEST_SUCCESS = 200;
    private static final int HIGHEST_SUCCESS = 299;

    private final List<Phase> phases = new ArrayList<>();
    private final Map<TestCaseId, Phase> waitingForAnAnswer = new HashMap<>();
    private Phase open;

    /** Takes note of one announcement. Anything that is not about a stretch of the run is ignored. */
    void on(RunEvent event) {
        switch (event) {
            case RunEvent.PhaseStarted started -> {
                // One stretch at a time. One still open when another begins did not get to finish.
                closeWhatIsOpen(started.at(), true);
                open = new Phase(started.phase(), started.at());
                phases.add(open);
            }
            case RunEvent.TestCasePlanned planned -> {
                if (open != null) {
                    open.sent(planned.testCase().operation());
                    waitingForAnAnswer.put(planned.testCase().id(), open);
                }
            }
            case RunEvent.InteractionCompleted completed -> {
                Phase belongsTo = waitingForAnAnswer.remove(completed.interaction().testCase().id());
                if (belongsTo != null) {
                    belongsTo.answered(completed.interaction());
                }
            }
            case RunEvent.PhaseFinished finished -> {
                if (open != null && open.name().equals(finished.phase())) {
                    closeWhatIsOpen(finished.at(), finished.cutShort());
                }
            }
            case RunEvent.RunFinished finished -> {
                // A stretch nobody closed was still going when the run ended, which is the time
                // running out. Nothing more will be answered, so nothing more is waited for.
                closeWhatIsOpen(finished.at(), true);
                waitingForAnAnswer.clear();
            }
            case RunEvent.RunStarted ignored -> {
                // Not about any stretch of the run.
            }
            case RunEvent.OperationSkipped ignored -> {
                // Nor is an operation no stretch of it will try.
            }
            case RunEvent.FaultFound ignored -> {
                // A fault is a judgement about a reply, and the reply has been counted already.
            }
        }
    }

    /** Every stretch the run announced, in the order they began. */
    List<Phase> all() {
        return List.copyOf(phases);
    }

    private void closeWhatIsOpen(Instant at, boolean cutShort) {
        if (open != null) {
            open.finished(at, cutShort);
            open = null;
        }
    }

    /**
     * One stretch of a run, and what happened in it.
     *
     * <p>Written to from the one thread that delivers announcements, so it keeps plain fields.
     */
    static final class Phase {

        private final String name;
        private final Instant startedAt;
        private Instant finishedAt;
        private boolean cutShort;
        private int requests;
        private int noReply;
        private final Set<OperationId> operations = new LinkedHashSet<>();
        private final Set<OperationId> answeredWithASuccess = new LinkedHashSet<>();
        private final SortedMap<String, Integer> repliesByClass = new TreeMap<>();

        private Phase(String name, Instant startedAt) {
            this.name = name;
            this.startedAt = startedAt;
        }

        private void sent(OperationId operation) {
            requests++;
            operations.add(operation);
        }

        private void answered(Interaction interaction) {
            interaction.statusCode().ifPresentOrElse(status -> {
                repliesByClass.merge((status / 100) + "xx", 1, Integer::sum);
                if (status >= LOWEST_SUCCESS && status <= HIGHEST_SUCCESS) {
                    answeredWithASuccess.add(interaction.testCase().operation());
                }
            }, () -> noReply++);
        }

        private void finished(Instant at, boolean wasCutShort) {
            this.finishedAt = at;
            this.cutShort = wasCutShort;
        }

        /** What the run called it. */
        String name() {
            return name;
        }

        Instant startedAt() {
            return startedAt;
        }

        /** When it ended, or when it was last heard of if the report is asked before then. */
        Instant finishedAt() {
            return finishedAt == null ? startedAt : finishedAt;
        }

        Duration elapsed() {
            return Duration.between(startedAt, finishedAt());
        }

        /** Whether it ended before it was done, which is usually the time running out. */
        boolean cutShort() {
            return cutShort;
        }

        /** How many requests were sent during it. */
        int requests() {
            return requests;
        }

        /** How many different operations those requests were for. */
        int operations() {
            return operations.size();
        }

        /** How many different operations answered one of them with a success. */
        int operationsAnsweredWithASuccess() {
            return answeredWithASuccess.size();
        }

        /** How the replies to its requests divide by family of status code, {@code 2xx} first. */
        SortedMap<String, Integer> repliesByClass() {
            return new TreeMap<>(repliesByClass);
        }

        /** How many of its requests got no reply at all. */
        int noReply() {
            return noReply;
        }
    }
}
