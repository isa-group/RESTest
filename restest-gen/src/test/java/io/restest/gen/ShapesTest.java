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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether a value is of the kind a shape declares, as against whether it could be sent there.
 *
 * <p>The two questions differ in one thing, a closed list of accepted values: a word not on the list
 * of accepted words is still a word. The change that sends a value of the wrong kind asks the first;
 * everything that wants a value the API should accept asks the second.
 */
class ShapesTest {

    private static final ApiModel NOTHING_NAMED = ApiModel.of("None", "1.0", List.of());

    @Test
    @DisplayName("a word off the closed list is still a word, though it could not be sent there")
    void the_kind_ignores_the_closed_list() {
        StringSchema status = new StringSchema(SchemaMetadata.none().withEnumeration(List.of(
                JsonValue.of("available"), JsonValue.of("sold"))), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of("lost"), status, 8)).isTrue();
        assertThat(Shapes.couldSatisfy(NOTHING_NAMED, JsonValue.of("lost"), status, 8)).isFalse();
        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of(3), status, 8)).isFalse();
    }

    @Test
    @DisplayName("a whole number is whole however it is written, and a number with a fraction is "
            + "not one")
    void a_whole_number_has_no_fraction() {
        NumberSchema whole = NumberSchema.of(NumberKind.INTEGER);

        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of(3), whole, 8)).isTrue();
        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of(new BigDecimal("1e3")), whole, 8))
                .isTrue();
        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of(new BigDecimal("1.5")), whole, 8))
                .isFalse();
        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of("3"), whole, 8))
                .describedAs("a number written as a word is a word").isFalse();
        assertThat(Shapes.ofTheKind(NOTHING_NAMED, JsonValue.of(new BigDecimal("1.5")),
                NumberSchema.of(NumberKind.NUMBER), 8)).isTrue();
    }

    @Test
    @DisplayName("a choice is any of its kinds, and a name is followed to the shape it names, a "
            + "closed list on the way ignored as well")
    void choices_and_names_are_followed() {
        ChoiceSchema wordOrWhole = ChoiceSchema.of(List.of(StringSchema.of(),
                NumberSchema.of(NumberKind.INTEGER)));
        ObjectSchema pet = ObjectSchema.of(Map.of("name", StringSchema.of()), Set.of());
        StringSchema colour = new StringSchema(SchemaMetadata.none().withEnumeration(List.of(
                JsonValue.of("red"))), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
        ApiModel named = NOTHING_NAMED.withSchemas(Map.of("Pet", pet, "Colour", colour));

        assertThat(Shapes.ofTheKind(named, JsonValue.of("a"), wordOrWhole, 8)).isTrue();
        assertThat(Shapes.ofTheKind(named, JsonValue.of(2), wordOrWhole, 8)).isTrue();
        assertThat(Shapes.ofTheKind(named, JsonValue.TRUE, wordOrWhole, 8)).isFalse();
        assertThat(Shapes.ofTheKind(named, JsonValue.object(Map.of()), SchemaReference.to("Pet"),
                8)).isTrue();
        assertThat(Shapes.ofTheKind(named, JsonValue.array(List.of()), SchemaReference.to("Pet"),
                8)).isFalse();
        assertThat(Shapes.ofTheKind(named, JsonValue.of("blue"), SchemaReference.to("Colour"), 8))
                .describedAs("off the named shape's list, but a word").isTrue();
        assertThat(Shapes.couldSatisfy(named, JsonValue.of("blue"), SchemaReference.to("Colour"),
                8)).isFalse();
    }
}
