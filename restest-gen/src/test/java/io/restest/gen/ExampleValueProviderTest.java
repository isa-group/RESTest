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
    @DisplayName("a sample the shape itself refuses is still offered, as the author wrote it")
    void a_sample_its_own_shape_refuses_is_still_offered() {
        CanonicalSchema tooLongForItself = new StringSchema(
                SchemaMetadata.none().withExamples(List.of(JsonValue.of("a much longer word"))),
                Optional.empty(), Optional.of(4), Optional.empty(), Optional.empty());

        assertThat(provider.offer(Schemas.asking(tooLongForItself)).orElseThrow().value())
                .describedAs("when a concrete sample and an abstract rule disagree there is no "
                        + "telling which the author meant, and the sample is the better evidence")
                .isEqualTo(JsonValue.of("a much longer word"));
    }

    @Test
    @DisplayName("a shape that accepts no value at all is offered no sample")
    void a_shape_that_accepts_nothing_is_offered_nothing() {
        ValueRequest impossible = new ValueRequest(OperationId.of("GET /widgets"), "widgetId",
                ParameterLocation.QUERY, io.restest.core.schema.NothingSchema.of(),
                List.of(JsonValue.of("anything")));

        assertThat(provider.offer(impossible))
                .describedAs("the document contradicts itself, and every other source believes "
                        + "the half that says nothing fits here")
                .isEmpty();
    }

    @Test
    @DisplayName("a sample that writes out as nothing is never put in the path")
    void a_sample_that_is_nothing_never_fills_a_gap_in_the_path() {
        for (JsonValue nothing : List.of(JsonValue.NULL, JsonValue.of(""),
                JsonValue.array(List.of()), JsonValue.object(java.util.Map.of()))) {
            ValueRequest inThePath = new ValueRequest(OperationId.of("GET /owners/{ownerId}"),
                    "ownerId", ParameterLocation.PATH, StringSchema.of(), List.of(nothing));

            assertThat(provider.offer(inThePath))
                    .describedAs("%s in the path would turn a request for one owner into a "
                            + "request for every owner", nothing)
                    .isEmpty();
            assertThat(provider.offer(new ValueRequest(OperationId.of("GET /owners"), "q",
                    ParameterLocation.QUERY, StringSchema.of(), List.of(nothing))))
                    .describedAs("elsewhere %s is an ordinary thing to send", nothing)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("a list that writes out as nothing is never put in the path either")
    void a_list_that_writes_out_as_nothing_never_fills_a_gap_in_the_path() {
        // A list of one empty word is a list with something in it and writes out as nothing, so
        // looking at the shape of the value rather than at what it writes would let this through.
        JsonValue oneEmptyWord = JsonValue.array(JsonValue.of(""));
        JsonValue oneNothing = JsonValue.array(JsonValue.NULL);
        JsonValue twoEmptyWords = JsonValue.array(JsonValue.of(""), JsonValue.of(""));

        assertThat(provider.offer(inThePath(oneEmptyWord))).isEmpty();
        assertThat(provider.offer(inThePath(oneNothing))).isEmpty();
        assertThat(provider.offer(inThePath(twoEmptyWords)))
                .describedAs("two of them write out as the separator between them, which is "
                        + "something - odd, but not a gap closed")
                .isPresent();
    }

    @Test
    @DisplayName("a usable sample is still found when another of them could not fill the path")
    void the_usable_samples_are_the_ones_chosen_among() {
        ValueRequest inThePath = new ValueRequest(OperationId.of("GET /owners/{ownerId}"),
                "ownerId", ParameterLocation.PATH, StringSchema.of(),
                List.of(JsonValue.NULL, JsonValue.of("1")));

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(provider.offer(inThePath).orElseThrow().value())
                    .isEqualTo(JsonValue.of("1"));
        }
    }

    @Test
    @DisplayName("a parameter whose only sample cannot be used falls back on its shape's")
    void an_unusable_sample_does_not_shadow_a_usable_one() {
        ValueRequest request = new ValueRequest(OperationId.of("GET /owners/{ownerId}"), "ownerId",
                ParameterLocation.PATH, sampled("1"), List.of(JsonValue.of("")));

        assertThat(provider.offer(request).orElseThrow().value())
                .describedAs("throwing away the identifier the document wrote down, and inventing "
                        + "one instead, is the outcome reading samples exists to prevent")
                .isEqualTo(JsonValue.of("1"));
    }

    @Test
    @DisplayName("a sample carrying a line break is never sent as a header")
    void a_sample_with_a_line_break_is_never_sent_as_a_header() {
        JsonValue broken = JsonValue.of("one\ntwo");

        assertThat(provider.offer(new ValueRequest(OperationId.of("GET /pets"), "X-Trace",
                ParameterLocation.HEADER, StringSchema.of(), List.of(broken))))
                .describedAs("a line break ends the header and starts another, so the request "
                        + "that went out would not be the request that was recorded")
                .isEmpty();
        assertThat(provider.offer(new ValueRequest(OperationId.of("GET /pets"), "X-Trace",
                ParameterLocation.HEADER, StringSchema.of(),
                List.of(JsonValue.array(JsonValue.of("a"), broken)))))
                .describedAs("the break may be inside one element, which the written value shows "
                        + "and the value's shape does not")
                .isEmpty();
        assertThat(provider.offer(new ValueRequest(OperationId.of("GET /pets"), "q",
                ParameterLocation.QUERY, StringSchema.of(), List.of(broken))))
                .describedAs("a query string is encoded on the way out, so nothing in it ends "
                        + "anything early")
                .isPresent();
    }

    private static ValueRequest inThePath(JsonValue sample) {
        return new ValueRequest(OperationId.of("GET /pets/{ids}"), "ids", ParameterLocation.PATH,
                io.restest.core.schema.ArraySchema.of(StringSchema.of()), List.of(sample));
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
