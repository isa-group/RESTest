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

import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import java.util.List;

/**
 * A rule that, when it cannot judge a reply, says why rather than only saying nothing.
 *
 * <p>Most rules can always tell: whether a reply is a 500 is never in doubt. A few cannot always:
 * the one that compares a body with the shape the document gives has nothing to compare it with
 * when the document gives none. Those rules say so here, and {@link OracleListener} announces it,
 * naming the rule by the kind of fault it reports and what it looks at, which the rule says once
 * here rather than in every verdict.
 *
 * <p>This interface is kept inside this module rather than added to {@link Oracle}: the rules
 * RESTest ships are the only ones that say this today, and a rule from elsewhere stays one method to
 * write. The rules that implement it are public, and so are the methods it gives them, but what
 * they return can only be read here. Such a rule judges by {@link #verdict} alone, and its
 * {@link Oracle#judge} is the faults of that verdict, so the two can never disagree.
 */
interface SaysWhyNot extends Oracle {

    /** The kind of fault this rule reports, which tells it apart from the other rules. */
    FaultCategory kind();

    /**
     * What this rule looks at, as a report writes it before "of 12 replies": {@code the shape},
     * {@code the status code}.
     */
    String what();

    /** What this rule makes of one attempt: what it found wrong, or why it could not judge it. */
    Verdict verdict(Interaction interaction, ApiModel api);

    @Override
    default List<Finding> judge(Interaction interaction, ApiModel api) {
        return verdict(interaction, api).findings();
    }
}
