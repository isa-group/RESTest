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
package io.restest.core.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a choice between shapes refuses to be, and what it does not let a caller change afterwards. */
class ChoiceSchemaTest {

    @Test
    @DisplayName("a choice between no shapes at all describes nothing, and is refused")
    void a_choice_between_nothing_is_refused() {
        assertThatThrownBy(() -> ChoiceSchema.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one shape");
    }

    @Test
    @DisplayName("a choice between one shape is allowed: a document may write one, and does")
    void a_choice_between_one_shape_is_allowed() {
        assertThat(ChoiceSchema.of(List.of(StringSchema.of())).alternatives()).hasSize(1);
    }

    @Test
    @DisplayName("the shapes on offer cannot be changed after the fact, by anybody")
    void the_shapes_are_copied_and_unmodifiable() {
        List<CanonicalSchema> given = new ArrayList<>(List.of(StringSchema.of()));
        ChoiceSchema choice = ChoiceSchema.of(given);

        given.add(NumberSchema.of(NumberKind.INTEGER));

        assertThat(choice.alternatives()).hasSize(1);
        assertThatThrownBy(() -> choice.alternatives().add(StringSchema.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the facts stated about the value regardless of its shape are kept")
    void the_metadata_is_kept() {
        ChoiceSchema choice = new ChoiceSchema(
                SchemaMetadata.none().withDescription("either a number or a name"),
                List.of(StringSchema.of()));

        assertThat(choice.metadata().description()).contains("either a number or a name");
    }
}
