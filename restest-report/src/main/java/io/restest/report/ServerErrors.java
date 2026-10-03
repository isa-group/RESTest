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

import io.restest.core.execution.Interaction;
import io.restest.core.model.OperationId;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How many operations made the API fall over.
 *
 * <p>This is the number benchmarks rank testing tools on, and it is counted in a particular way that
 * is worth stating rather than leaving in the arithmetic. It counts **operations**, not replies: a
 * run spends its whole budget, so one broken operation asked ten thousand times produces ten thousand
 * broken replies, and counting those would measure how long the run was rather than how much of the
 * API is broken. And it counts what the API **did**, not what any rule of ours made of it, because
 * which rules run changes between releases and a reply nobody judged still happened.
 *
 * <p>Two numbers rather than one. The shared fault catalogue names the status 500 as a fault of its
 * own, and the rest of the 5xx family as nothing in particular: a 503 is an API saying it will not
 * serve you now, which is not the same as an API falling over while trying. Both are worth knowing
 * and they are not the same claim.
 *
 * <p>Kept here, once, rather than in each report that prints it. Every report showing this has to
 * show the same number, and the way to guarantee that is for there to be one place where it is
 * worked out. The alternative was tried in this same codebase: three copies of how to read a status
 * code, two of which had drifted apart by the time anybody looked.
 */
final class ServerErrors {

    /**
     * The one status code the shared fault catalogue names as a fault in its own right.
     */
    private static final int FELL_OVER = 500;

    private static final int LOWEST_5XX = 500;
    private static final int HIGHEST_5XX = 599;

    private final Set<OperationId> fellOver = new LinkedHashSet<>();
    private final Set<OperationId> refusedOrFellOver = new LinkedHashSet<>();

    /**
     * Takes note of one finished attempt.
     *
     * <p>A reply the tool could not finish reading still counts, as long as its status line arrived:
     * the status is the first thing an API sends and is complete long before the body it precedes,
     * so "the reply broke off halfway" and "we never learned what it said" are different facts.
     *
     * <p>The upper end of the range is not decoration. Nothing in the model stops an API answering
     * 999, real ones do it, and the catalogue carries a fault for exactly that. A code above the
     * family is not a server error and must not be counted as one.
     */
    void note(Interaction interaction) {
        interaction.statusCode()
                .filter(status -> status >= LOWEST_5XX && status <= HIGHEST_5XX)
                .ifPresent(status -> {
                    refusedOrFellOver.add(interaction.testCase().operation());
                    if (status == FELL_OVER) {
                        fellOver.add(interaction.testCase().operation());
                    }
                });
    }

    /** How many operations answered 500 at least once. */
    int operationsAnswering500() {
        return fellOver.size();
    }

    /** How many operations answered anything in the 5xx family at least once. */
    int operationsAnsweringAny5xx() {
        return refusedOrFellOver.size();
    }

    /** Whether the API got through the whole run without a single server error. */
    boolean none() {
        return refusedOrFellOver.isEmpty();
    }
}
