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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.json.JsonValue;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The order sources are asked in is the tool's order of preference, so what matters here is that the
 * first one with something to say is the one that answers.
 */
class ValueProviderChainTest {

    private final List<String> asked = new ArrayList<>();

    @Test
    @DisplayName("the first source with an answer is the one that is used")
    void the_first_answer_wins() {
        ValueProviderChain chain = ValueProviderChain.of(
                saying("first", "from the dictionary"),
                saying("second", "invented"));

        GeneratedValue value = chain.offer(Schemas.asking(StringSchema.of())).orElseThrow();

        assertThat(((JsonValue.JsonString) value.value()).value()).isEqualTo("from the dictionary");
        assertThat(asked).describedAs("no point asking anybody else once somebody has answered")
                .containsExactly("first");
    }

    @Test
    @DisplayName("a source with nothing to say lets the next one answer")
    void silence_falls_through_to_the_next_source() {
        ValueProviderChain chain = ValueProviderChain.of(
                silent("knows about dates"),
                silent("knows about e-mail addresses"),
                saying("invention", "a value"));

        assertThat(chain.offer(Schemas.asking(StringSchema.of()))).isPresent();
        assertThat(asked).containsExactly("knows about dates", "knows about e-mail addresses",
                "invention");
    }

    @Test
    @DisplayName("when nobody knows a value, the caller is told so rather than given a guess")
    void a_chain_where_nobody_answers_says_nothing() {
        ValueProviderChain chain = ValueProviderChain.of(silent("one"), silent("two"));

        assertThat(chain.offer(Schemas.asking(StringSchema.of()))).isEmpty();
    }

    @Test
    @DisplayName("an empty chain is not an error, it simply knows nothing")
    void an_empty_chain_knows_nothing() {
        assertThat(ValueProviderChain.of().offer(Schemas.asking(StringSchema.of()))).isEmpty();
    }

    @Test
    @DisplayName("the chain can say who it asks, in order, for a report to show")
    void the_chain_names_its_sources() {
        ValueProviderChain chain = ValueProviderChain.of(silent("declared"), silent("random"));

        assertThat(chain.name()).isEqualTo("declared then random");
        assertThat(chain.providers()).hasSize(2);
    }

    @Test
    @DisplayName("what the document states is preferred to what anybody invents")
    void the_default_order_prefers_what_the_document_says() {
        StringSchema declaredAsPending = new StringSchema(
                SchemaMetadata.none().withDefault(JsonValue.of("pending")),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        ValueProviderChain chain = ValueProviderChain.of(
                new DeclaredValueProvider(Schemas.fixedRandom()),
                new RandomValueProvider(
                        io.restest.core.model.ApiModel.of("Test API", "1.0", List.of()),
                        Schemas.fixedRandom()));

        GeneratedValue value = chain.offer(Schemas.asking(declaredAsPending)).orElseThrow();

        assertThat(((JsonValue.JsonString) value.value()).value()).isEqualTo("pending");
        assertThat(value.origin()).isEqualTo(io.restest.core.execution.ValueOrigin.DECLARED);
    }

    private ValueProvider saying(String who, String what) {
        return named(who, request -> Optional.of(GeneratedValue.declared(JsonValue.of(what))));
    }

    private ValueProvider silent(String who) {
        return named(who, request -> Optional.empty());
    }

    private ValueProvider named(String who, ValueProvider answering) {
        return new ValueProvider() {
            @Override
            public Optional<GeneratedValue> offer(io.restest.core.gen.ValueRequest request) {
                asked.add(who);
                return answering.offer(request);
            }

            @Override
            public String name() {
                return who;
            }
        };
    }
}
