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
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The source of values that offers what the API's own author wrote down as a sample. */
class ExampleValueProviderTest {

    private final ExampleValueProvider provider =
            new ExampleValueProvider(Schemas.fixedRandom());

    @Test
    @DisplayName("a sample the shape offers is used, and says it came from a sample")
    void a_sample_on_the_shape_is_used() {
        GeneratedValue value = provider.offer(Schemas.asking(sampled("Davis"))).orElseThrow();

        assertThat(value.value()).isEqualTo(JsonValue.of("Davis"));
        assertThat(value.origin())
                .isEqualTo(ValueOrigin.declared(ValueOrigin.Declared.Statement.EXAMPLE));
    }

    @Test
    @DisplayName("a sample the parameter itself offers wins over one its shape offers")
    void a_parameters_own_sample_wins() {
        ValueRequest request = new ValueRequest(OperationId.of("GET /clusters/{cluster_id}"),
                "cluster_id", ParameterLocation.PATH, sampled("from the shape"),
                List.of(JsonValue.of("cluster-1")));

        assertThat(provider.offer(request).orElseThrow().value())
                .describedAs("OpenAPI says a parameter's own sample takes precedence")
                .isEqualTo(JsonValue.of("cluster-1"));
    }

    @Test
    @DisplayName("over a run, every sample offered gets used rather than only the first")
    void every_sample_is_used_eventually() {
        CanonicalSchema several = sampled("one", "two", "three");
        Set<String> seen = new HashSet<>();

        for (int attempt = 0; attempt < 200; attempt++) {
            seen.add(((JsonValue.JsonString) provider.offer(Schemas.asking(several))
                    .orElseThrow().value()).value());
        }

        assertThat(seen).containsExactlyInAnyOrder("one", "two", "three");
    }

    @Test
    @DisplayName("a shape offering no sample is left to the next source")
    void nothing_to_offer_is_said_by_saying_nothing() {
        assertThat(provider.offer(Schemas.asking(StringSchema.of()))).isEmpty();
    }

    @Test
    @DisplayName("a value restricted to a fixed list is left to the source that walks the list")
    void an_enumerated_value_is_left_alone() {
        CanonicalSchema listed = new StringSchema(
                SchemaMetadata.none()
                        .withEnumeration(List.of(JsonValue.of("true"), JsonValue.of("false")))
                        .withExamples(List.of(JsonValue.of("true"))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(provider.offer(Schemas.asking(listed)))
                .describedAs("a sample here adds nothing the list does not already say, and using "
                        + "it would pin the run to one member of a list meant to be walked")
                .isEmpty();

        ValueRequest withItsOwnSample = new ValueRequest(OperationId.of("GET /widgets"), "download",
                ParameterLocation.QUERY, listed, List.of(JsonValue.of("false")));
        assertThat(provider.offer(withItsOwnSample)).isEmpty();
    }

    @Test
    @DisplayName("the source names itself in a word a report can print")
    void the_source_has_a_name() {
        assertThat(provider.name()).isEqualTo("example");
    }

    private static CanonicalSchema sampled(String... samples) {
        return new StringSchema(
                SchemaMetadata.none().withExamples(
                        List.of(samples).stream().<JsonValue>map(JsonValue::of).toList()),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
