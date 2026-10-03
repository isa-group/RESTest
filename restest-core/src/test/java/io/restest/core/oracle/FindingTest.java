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

import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindingTest {

    @Test
    @DisplayName("a finding knows its operation and its attempt without repeating either")
    void a_finding_answers_for_its_own_evidence() {
        Interaction interaction = interaction();

        Finding finding = Finding.of(WfcFault.HTTP_STATUS_500, interaction,
                "the API answered 500");

        assertThat(finding.operation()).isEqualTo(OperationId.of("GET /pets"));
        assertThat(finding.interactionId()).isEqualTo(interaction.id());
        assertThat(finding.label()).isEqualTo("F100:HTTP Status 500");
        assertThat(finding.details()).isEmpty();
        assertThat(finding.context()).isEmpty();
    }

    @Test
    @DisplayName("the particular disagreements can be listed, and cannot be changed afterwards")
    void details_are_carried_and_copied() {
        List<String> details = new ArrayList<>(List.of("/id: string found, integer expected"));

        Finding finding = Finding.of(WfcFault.SCHEMA_INVALID_RESPONSE, interaction(),
                "the body does not match its shape").withDetails(details);
        details.add("something added afterwards");

        assertThat(finding.details()).containsExactly("/id: string found, integer expected");
    }

    @Test
    @DisplayName("a finding can be told apart from others of its kind by one word")
    void a_context_tells_two_findings_of_one_kind_apart() {
        Finding finding = Finding.of(WfcFault.HTTP_STATUS_500, interaction(), "it fell over")
                .withContext("on delete");

        assertThat(finding.context()).contains("on delete");
    }

    @Test
    @DisplayName("a finding that does not say what is wrong is refused, not reported")
    void a_blank_summary_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Finding.of(WfcFault.HTTP_STATUS_500, interaction(), "  "))
                .withMessageContaining("nobody can act on");
    }

    @Test
    @DisplayName("a context that says nothing is refused, because it looks like it says something")
    void a_blank_context_is_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Finding
                        .of(WfcFault.HTTP_STATUS_500, interaction(), "it fell over")
                        .withContext(" "))
                .withMessageContaining("only looks like");
    }

    private static Interaction interaction() {
        TestCase testCase = TestCase.of(OperationId.of("GET /pets"),
                List.of(ParameterValue.of("limit", ParameterLocation.QUERY, JsonValue.of(10),
                        new ValueOrigin.Generated("random"))));
        return Interaction.answered(testCase,
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets"),
                HttpResponseRecord.of(500), Instant.EPOCH, Duration.ofMillis(9));
    }
}
