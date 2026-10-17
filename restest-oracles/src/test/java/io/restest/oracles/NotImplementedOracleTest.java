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

import io.restest.core.execution.Intent;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.ResponseModel;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import io.restest.core.schema.AnySchema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotImplementedOracleTest {

    /** Declares only a success. */
    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

    /** Declares, beside a success, that it may not be implemented. */
    private static final Operation LIST_VETS = Operation.of(HttpMethod.GET, "/vets")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of()),
                    ResponseModel.empty("501")));

    private static final ApiModel API = ApiModel.of("Pets", "1.0.0", List.of(LIST_PETS, LIST_VETS));

    private final NotImplementedOracle oracle = new NotImplementedOracle();

    @Test
    @DisplayName("a 501 to an operation the document declares is reported")
    void a_501_to_a_declared_operation_is_reported() {
        List<Finding> found = oracle.judge(
                Attempts.answered(LIST_PETS.id(), "/pets", 501, "text/plain", "Not Implemented"),
                API);

        assertThat(found).hasSize(1);
        Finding finding = found.get(0);
        assertThat(finding.category()).isEqualTo(WfcFault.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED);
        assertThat(finding.operation()).isEqualTo(LIST_PETS.id());
        assertThat(finding.summary()).isEqualTo(
                "the API answered 501, not implemented, to an operation its document declares");
        assertThat(finding.details()).isEmpty();
    }

    @Test
    @DisplayName("a 501 is reported even where the document lists 501 among the replies: it still "
            + "promised an operation the API does not do")
    void a_501_the_document_lists_is_reported() {
        assertThat(oracle.judge(Attempts.answered(LIST_VETS.id(), "/vets", 501, "text/plain",
                "Not Implemented"), API)).hasSize(1);
    }

    @Test
    @DisplayName("a 501 is reported whatever was sent, a request broken on purpose included")
    void a_501_to_a_request_broken_on_purpose_is_reported() {
        assertThat(oracle.judge(Attempts.answered(Attempts.changed(LIST_PETS.id(),
                Intent.REFUSAL_EXPECTED, "wrongType", "sent a word where a number goes"),
                "/pets", 501), API)).hasSize(1);
    }

    @Test
    @DisplayName("a 501 is reported from its status line, though only part of the body was kept")
    void a_501_with_part_of_its_body_is_reported() {
        assertThat(oracle.judge(Attempts.answeredWithPartOfTheBody(LIST_PETS.id(), "/pets", 501,
                "text/html", "<html>", 5000), API)).hasSize(1);
    }

    @Test
    @DisplayName("a 501 to something the document does not declare is the API saying, correctly, "
            + "that it does not do it")
    void a_501_to_an_undeclared_operation_is_left_alone() {
        assertThat(oracle.judge(Attempts.answered(OperationId.of("PATCH /pets"), "/pets", 501,
                "text/plain", "Not Implemented"), API)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 500, 502, 503})
    @DisplayName("anything but 501 is left alone")
    void anything_else_is_left_alone(int status) {
        assertThat(oracle.judge(Attempts.answered(LIST_PETS.id(), "/pets", status,
                "application/json", "{}"), API)).isEmpty();
    }

    @Test
    @DisplayName("a reply that broke off, and a request that got none, are left alone")
    void no_well_formed_reply_is_left_alone() {
        assertThat(oracle.judge(Attempts.brokenOff(LIST_PETS.id(), "/pets", 501), API)).isEmpty();
        assertThat(oracle.judge(Attempts.neverAnswered(LIST_PETS.id(), "/pets"), API)).isEmpty();
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("not-implemented");
        assertThat(oracle.description()).contains("501");
    }
}
