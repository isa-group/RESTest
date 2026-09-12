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
package io.restest.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.model.OperationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InteractionQueryTest {

    @Test
    @DisplayName("a query with nothing in it asks for everything")
    void the_empty_query_asks_for_everything() {
        InteractionQuery all = InteractionQuery.all();

        assertThat(all.isUnfiltered()).isTrue();
        assertThat(all.operation()).isEmpty();
        assertThat(all.limit()).isEmpty();
    }

    @Test
    @DisplayName("the named questions are the ones people actually ask")
    void the_factories_build_the_questions_they_name() {
        assertThat(InteractionQuery.serverErrors().statusClass()).contains(5);
        assertThat(InteractionQuery.withStatus(404).statusCode()).contains(404);
        assertThat(InteractionQuery.neverAnswered().outcome())
                .contains(InteractionQuery.Outcome.FAILED);
        assertThat(InteractionQuery.forOperation(OperationId.of("GET /pets")).operation())
                .contains(OperationId.of("GET /pets"));
    }

    @Test
    @DisplayName("parts of a question combine, and adding one leaves the original alone")
    void queries_combine_without_changing_each_other() {
        InteractionQuery errors = InteractionQuery.serverErrors();
        InteractionQuery errorsFromOne = errors.andOperation(OperationId.of("GET /pets"))
                .limitedTo(10);

        assertThat(errorsFromOne.statusClass()).contains(5);
        assertThat(errorsFromOne.operation()).contains(OperationId.of("GET /pets"));
        assertThat(errorsFromOne.limit()).contains(10);
        assertThat(errorsFromOne.isUnfiltered()).isFalse();
        assertThat(errors.operation())
                .describedAs("a query is a value: keeping one and adding to it must not change it")
                .isEmpty();
    }

    @Test
    @DisplayName("a question nothing could answer is refused where it is written, not later")
    void impossible_questions_are_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> InteractionQuery.withStatus(99))
                .withMessageContaining("between 100 and 599");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> InteractionQuery.all().andStatusClass(7))
                .withMessageContaining("first digit");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> InteractionQuery.all().limitedTo(0))
                .withMessageContaining("asking for none");
    }
}
