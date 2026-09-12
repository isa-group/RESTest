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
package io.restest.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValueOriginTest {

    private static final List<ValueOrigin> EVERY_KIND = List.of(
            ValueOrigin.DECLARED,
            new ValueOrigin.Generated("random"),
            new ValueOrigin.Derived(InteractionId.of("i-1"), "response body field 'id'"));

    @Test
    @DisplayName("every kind of origin can be told apart without a default case")
    void the_hierarchy_is_exhaustive() {
        assertThat(EVERY_KIND).map(ValueOriginTest::describe)
                .containsExactly("declared", "generated: random", "derived from i-1");
    }

    @Test
    @DisplayName("a declared origin has no components: every instance is equal")
    void declared_is_a_single_value() {
        assertThat(new ValueOrigin.Declared()).isEqualTo(ValueOrigin.DECLARED);
    }

    @Test
    @DisplayName("a generated value must name what generated it")
    void generated_requires_a_source() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ValueOrigin.Generated(" "))
                .withMessageContaining("name what generated it");
    }

    @Test
    @DisplayName("a derived value must say what was taken from the interaction it depends on")
    void derived_requires_a_description() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ValueOrigin.Derived(InteractionId.of("i-1"), " "))
                .withMessageContaining("i-1");
    }

    @Test
    @DisplayName("a derived value names the interaction it was read from")
    void derived_points_at_an_interaction_identifier() {
        InteractionId sourceInteraction = InteractionId.generate();

        ValueOrigin.Derived derived = new ValueOrigin.Derived(sourceInteraction, "the created id");

        assertThat(derived.from()).isEqualTo(sourceInteraction);
    }

    /**
     * Compiling is the assertion: a switch with no default over a sealed interface stops compiling
     * the day a fourth origin is added and not handled, which is what keeps a report's "values by
     * source" breakdown honest.
     */
    private static String describe(ValueOrigin origin) {
        return switch (origin) {
            case ValueOrigin.Declared ignored -> "declared";
            case ValueOrigin.Generated generated -> "generated: " + generated.source();
            case ValueOrigin.Derived derived -> "derived from " + derived.from();
        };
    }
}
