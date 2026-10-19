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
import io.restest.core.model.OperationId;
import io.restest.core.oracle.CouldNotJudge;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many replies a run could not check, in which ways, on which operations, and why.
 *
 * <p>A run that found nothing wrong has said nothing about the replies it could not look at: the
 * shape of a body the document gives no shape for, say, was never compared with anything. Counted
 * here, a run can say so beside its verdict, so that "no faults found" is never read as "everything
 * was checked and found fine".
 *
 * <p>Counted by check and reason together: the shape of 300 replies because the document gives
 * none is one line, the shape of 12 because the body was cut short another, and the content type of
 * 300 because the document names none a third. Replies, not operations, are what is counted, since
 * each is a reply nothing was said about; how many operations they came from is said beside them,
 * because that is what a person goes and fixes.
 *
 * <p>Kept here, once, rather than in each report that prints it, as {@link ServerErrors} is: every
 * report that says this has to say the same numbers in the same order.
 */
final class NotChecked {

    /**
     * The order lines are said in: most replies first, then by the number of the kind of fault the
     * check reports, then by the reason, so that two runs counting the same say it the same way.
     */
    private static final Comparator<Line> ORDER = Comparator.comparingLong(Line::replies).reversed()
            .thenComparingInt(line -> line.check().kind().code())
            .thenComparing(line -> line.check().why());

    /**
     * How many replies each check could not judge for each reason, on each operation. Counted in
     * place, one slot each, because some APIs give a reason for nearly every reply of a run.
     */
    private final Map<CouldNotJudge, Map<OperationId, long[]>> byCheck = new HashMap<>();

    /**
     * One check and one reason, with how many replies it could not judge for that reason, on each
     * operation.
     *
     * @param check      the check, what it looks at, and why it could not
     * @param operations how many replies on each operation, most first and then by name
     */
    record Line(CouldNotJudge check, Map<OperationId, Long> operations) {

        /** How many replies, over every operation. */
        long replies() {
            return operations.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    /** Takes note of a reply some checks could not judge. */
    void note(RunEvent.ReplyNotJudged notJudged) {
        OperationId operation = notJudged.interaction().testCase().operation();
        notJudged.checks().forEach(check -> byCheck
                .computeIfAbsent(check, ignored -> new HashMap<>())
                .computeIfAbsent(operation, ignored -> new long[1])[0]++);
    }

    /** Whether every reply was checked in every way it could have been. */
    boolean none() {
        return byCheck.isEmpty();
    }

    /** Every check and reason, in the order to say them. */
    List<Line> lines() {
        return byCheck.entrySet().stream()
                .map(entry -> new Line(entry.getKey(), mostFirst(entry.getValue())))
                .sorted(ORDER)
                .toList();
    }

    private static Map<OperationId, Long> mostFirst(Map<OperationId, long[]> counts) {
        Map<OperationId, Long> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<OperationId, long[]> entry) ->
                                entry.getValue()[0]).reversed()
                        .thenComparing(entry -> entry.getKey().value()))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()[0]));
        return ordered;
    }
}
