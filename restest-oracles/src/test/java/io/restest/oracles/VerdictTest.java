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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import io.restest.core.oracle.WhyNotJudged;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerdictTest {

    private static final Finding FELL_OVER = Finding.of(WfcFault.HTTP_STATUS_500,
            Attempts.answered(OperationId.of("GET /pets"), "/pets", 500, "application/json", "{}"),
            "the API answered 500");

    @Test
    @DisplayName("a rule that found something wrong with a reply has judged it, so cannot also say "
            + "it could not")
    void a_verdict_with_faults_has_no_reason_it_could_not_judge() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Verdict(List.of(FELL_OVER),
                Optional.of(WhyNotJudged.NO_SHAPE_DECLARED)));
    }

    @Test
    @DisplayName("a verdict is the faults found, or the reason the rule could not judge, or neither")
    void a_verdict_is_faults_or_a_reason_or_neither() {
        assertThat(Verdict.of(FELL_OVER).findings()).containsExactly(FELL_OVER);
        assertThat(Verdict.of(FELL_OVER).notJudged()).isEmpty();
        assertThat(Verdict.couldNot(WhyNotJudged.CUT_SHORT).findings()).isEmpty();
        assertThat(Verdict.couldNot(WhyNotJudged.CUT_SHORT).notJudged())
                .contains(WhyNotJudged.CUT_SHORT);
        assertThat(Verdict.NOTHING.findings()).isEmpty();
        assertThat(Verdict.NOTHING.notJudged()).isEmpty();
    }
}
