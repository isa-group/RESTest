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
package io.restest.oracles;

import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WhyNotJudged;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a rule made of one reply: the faults it found in it, or why it could not judge it.
 *
 * <p>Three outcomes, and the difference between the last two is the point of this type. A rule
 * finds something wrong; or it looked and found nothing, or had nothing to look at - a reply with
 * no body has no shape to compare; or it would have looked and could not, because the document
 * gives no shape, say. Only the last is counted, so that a run can say how much of what came back
 * it could not check. Which rule it was, and what it looks at, the rule says once for all its
 * verdicts - see {@link SaysWhyNot}.
 *
 * @param findings  what the rule found wrong, usually nothing
 * @param notJudged why the rule could not judge the reply, when it could not
 */
record Verdict(List<Finding> findings, Optional<WhyNotJudged> notJudged) {

    /** Nothing found wrong, or nothing there to judge. */
    static final Verdict NOTHING = new Verdict(List.of(), Optional.empty());

    /**
     * One verdict for each reason, made once: a rule gives one of these for every reply it cannot
     * judge, and on some APIs that is nearly every reply of the run.
     */
    private static final Map<WhyNotJudged, Verdict> COULD_NOT = couldNotForEachReason();

    Verdict {
        findings = List.copyOf(findings);
        Objects.requireNonNull(notJudged, "notJudged");
        if (!findings.isEmpty() && notJudged.isPresent()) {
            throw new IllegalArgumentException("a rule that found something wrong with a reply "
                    + "has judged it");
        }
    }

    /** One thing found wrong. */
    static Verdict of(Finding finding) {
        return new Verdict(List.of(finding), Optional.empty());
    }

    /** The rule could not judge the reply, for this reason. */
    static Verdict couldNot(WhyNotJudged why) {
        return COULD_NOT.get(Objects.requireNonNull(why, "why"));
    }

    private static Map<WhyNotJudged, Verdict> couldNotForEachReason() {
        Map<WhyNotJudged, Verdict> each = new EnumMap<>(WhyNotJudged.class);
        for (WhyNotJudged why : WhyNotJudged.values()) {
            each.put(why, new Verdict(List.of(), Optional.of(why)));
        }
        return Collections.unmodifiableMap(each);
    }
}
