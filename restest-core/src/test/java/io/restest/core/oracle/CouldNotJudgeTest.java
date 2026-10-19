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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.event.RunEvent;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouldNotJudgeTest {

    private static final CouldNotJudge NO_SHAPE = new CouldNotJudge(
            WfcFault.SCHEMA_INVALID_RESPONSE, "the shape", WhyNotJudged.NO_SHAPE_DECLARED);

    @Test
    @DisplayName("a check that could not judge a reply says what it would have looked at")
    void what_it_would_have_looked_at_is_required() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CouldNotJudge(
                WfcFault.SCHEMA_INVALID_RESPONSE, " ", WhyNotJudged.NO_SHAPE_DECLARED));
    }

    @Test
    @DisplayName("every reason has a word of its own for the report and a phrase for the screen")
    void every_reason_has_its_own_word_and_phrase() {
        assertThat(WhyNotJudged.values()).allSatisfy(why -> {
            assertThat(why.word()).matches("[a-z][A-Za-z0-9]*");
            assertThat(why.inWords()).isNotBlank();
        });
        assertThat(Arrays.stream(WhyNotJudged.values()).map(WhyNotJudged::word))
                .doesNotHaveDuplicates();
        assertThat(WhyNotJudged.NO_SHAPE_DECLARED.word()).isEqualTo("noShapeDeclared");
        assertThat(WhyNotJudged.NO_SHAPE_DECLARED.inWords())
                .isEqualTo("the document gives no shape");
    }

    @Test
    @DisplayName("a reply not judged is announced with every check that could not judge it, kept "
            + "as it was announced")
    void the_announcement_keeps_what_it_was_given() {
        List<CouldNotJudge> checks = new ArrayList<>(List.of(NO_SHAPE));

        RunEvent.ReplyNotJudged announced = new RunEvent.ReplyNotJudged(Instant.EPOCH,
                interaction(), checks);
        checks.clear();

        assertThat(announced.checks()).containsExactly(NO_SHAPE);
    }

    @Test
    @DisplayName("a reply every check could judge is not announced as one that could not be")
    void an_announcement_names_at_least_one_check() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunEvent.ReplyNotJudged(
                Instant.EPOCH, interaction(), List.of()));
    }

    private static Interaction interaction() {
        return Interaction.answered(TestCase.of(OperationId.of("GET /pets"), List.of()),
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets"),
                HttpResponseRecord.of(200), Instant.EPOCH, Duration.ofMillis(9));
    }
}
