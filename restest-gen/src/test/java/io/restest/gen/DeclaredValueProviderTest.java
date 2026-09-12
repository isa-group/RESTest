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

import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.json.JsonValue;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the specification's own author wrote down is the best value available, so it comes first. */
class DeclaredValueProviderTest {

    private final DeclaredValueProvider provider =
            new DeclaredValueProvider(Schemas.fixedRandom());

    @Test
    @DisplayName("a parameter with a closed list of allowed values gets one of them")
    void an_allowed_value_is_used() {
        StringSchema status = new StringSchema(
                SchemaMetadata.none().withEnumeration(List.of(
                        JsonValue.of("available"), JsonValue.of("pending"), JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        GeneratedValue value = provider.offer(Schemas.asking(status)).orElseThrow();

        assertThat(((JsonValue.JsonString) value.value()).value())
                .isIn("available", "pending", "sold");
        assertThat(value.origin()).isEqualTo(ValueOrigin.DECLARED);
    }

    @Test
    @DisplayName("over a run, the whole list gets exercised rather than only its first member")
    void every_allowed_value_is_used_eventually() {
        StringSchema status = new StringSchema(
                SchemaMetadata.none().withEnumeration(List.of(
                        JsonValue.of("available"), JsonValue.of("pending"), JsonValue.of("sold"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        Set<String> seen = IntStream.range(0, 50)
                .mapToObj(i -> provider.offer(Schemas.asking(status)).orElseThrow())
                .map(value -> ((JsonValue.JsonString) value.value()).value())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(seen).containsExactlyInAnyOrder("available", "pending", "sold");
    }

    @Test
    @DisplayName("a parameter with a stated default gets it")
    void a_default_is_used() {
        NumberSchema size = new NumberSchema(
                SchemaMetadata.none().withDefault(JsonValue.of(20)), NumberKind.INTEGER,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());

        GeneratedValue value = provider.offer(Schemas.asking(size)).orElseThrow();

        assertThat(((JsonValue.JsonNumber) value.value()).value().intValueExact()).isEqualTo(20);
    }

    @Test
    @DisplayName("a closed list is preferred to a default, since it is the stricter statement")
    void an_allowed_value_beats_a_default() {
        StringSchema both = new StringSchema(
                SchemaMetadata.none()
                        .withDefault(JsonValue.of("anything"))
                        .withEnumeration(List.of(JsonValue.of("only-this"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        GeneratedValue value = provider.offer(Schemas.asking(both)).orElseThrow();

        assertThat(((JsonValue.JsonString) value.value()).value()).isEqualTo("only-this");
    }

    @Test
    @DisplayName("a parameter the specification says nothing concrete about is left to others")
    void nothing_is_offered_when_the_document_states_nothing() {
        assertThat(provider.offer(Schemas.asking(StringSchema.of()))).isEmpty();
    }
}
