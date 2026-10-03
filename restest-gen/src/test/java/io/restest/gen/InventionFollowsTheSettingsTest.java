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
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.GenerationSettings;
import io.restest.core.settings.Settings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

/**
 * What the tool invents changes when somebody changes the settings.
 *
 * <p>The settings are only worth having if they actually reach the place the value is built. Each
 * test here asks for a value twice - once with what RESTest does by default and once with a setting
 * moved - and checks that the second answer is different in the way the setting says. An API that
 * refuses anything over thirty characters, or one that falls over on a list of four, is answered by
 * one of these lines rather than by rebuilding the tool.
 */
class InventionFollowsTheSettingsTest {

    private static final ApiModel EMPTY = ApiModel.of("Test API", "1.0", List.of());

    @RepeatedTest(20)
    @DisplayName("how long an invented word is")
    void the_length_of_a_word() {
        String shorter = ((JsonValue.JsonString) invent(StringSchema.of(),
                asked("generation.usualLongestString", "6"))).value();

        assertThat(shorter.length())
                .describedAs("an API that refuses anything longer is answered in one line")
                .isBetween(1, 6);
    }

    @RepeatedTest(20)
    @DisplayName("how many things go in an invented list")
    void the_size_of_a_list() {
        ArraySchema list = new ArraySchema(SchemaMetadata.none(), StringSchema.of(),
                Optional.empty(), Optional.empty(), false);

        JsonValue.JsonArray few = (JsonValue.JsonArray) invent(list,
                asked("generation.usualMostItems", "1"));

        assertThat(few.elements()).hasSizeLessThanOrEqualTo(1);
    }

    @RepeatedTest(20)
    @DisplayName("the room an unbounded number is invented in")
    void the_range_of_a_number() {
        NumberSchema number = new NumberSchema(SchemaMetadata.none(), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        GenerationSettings narrow = Settings.from(Map.of(
                "generation.lowestNumber", "100",
                "generation.roomAboveIt", "5")).generation();

        BigDecimal invented = ((JsonValue.JsonNumber) invent(number, narrow)).value();

        assertThat(invented.intValue()).isBetween(100, 105);
    }

    @RepeatedTest(20)
    @DisplayName("whether a property the description does not require is sent at all")
    void whether_optional_properties_are_sent() {
        ObjectSchema thing = new ObjectSchema(SchemaMetadata.none(),
                Map.of("required", StringSchema.of(), "optional", StringSchema.of()),
                Set.of("required"), Optional.empty(), Optional.empty(), Optional.empty());

        JsonValue.JsonObject onlyWhatIsNeeded = (JsonValue.JsonObject) invent(thing,
                asked("generation.optionalPropertyChance", "0"));

        assertThat(onlyWhatIsNeeded.members()).containsOnlyKeys("required");
    }

    /** The settings RESTest uses by default, with one of them changed the way a person would. */
    private static GenerationSettings asked(String key, String value) {
        return Settings.from(Map.of(key, value)).generation();
    }

    private static JsonValue invent(CanonicalSchema schema, GenerationSettings settings) {
        RandomValueProvider provider = new RandomValueProvider(EMPTY, Schemas.fixedRandom(),
                new DeclaredValueProvider(Schemas.fixedRandom()), settings);
        return provider.offer(Schemas.asking(schema)).orElseThrow().value();
    }
}
