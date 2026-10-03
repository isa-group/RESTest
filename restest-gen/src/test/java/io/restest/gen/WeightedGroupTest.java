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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Asking every source and choosing among the answers, rather than taking the first.
 *
 * <p>The other way of putting sources together, and the reason a plan can say a parameter should
 * sometimes get the document's sample and sometimes an invented value instead of the same one for
 * a whole run.
 */
class WeightedGroupTest {

    private static final ValueRequest ASKING = ValueRequest.of(OperationId.of("getPet"), "name",
            ParameterLocation.QUERY, StringSchema.of());

    @Test
    @DisplayName("over many draws, each source is chosen about as often as its weight says")
    void the_weights_decide_how_often_each_is_chosen() {
        WeightedGroup group = new WeightedGroup(List.of(
                new WeightedGroup.Weighted(always("often"), 80),
                new WeightedGroup.Weighted(always("seldom"), 20)),
                new SplittableRandom(4242L));

        Map<String, Long> counted = drawn(group, 4000);

        assertThat(counted.get("often")).isBetween(3000L, 3400L);
        assertThat(counted.get("seldom")).isBetween(600L, 1000L);
    }

    @Test
    @DisplayName("a source with nothing to say does not get its share: it goes to the others, and "
            + "the group still answers")
    void a_silent_source_does_not_get_its_share() {
        WeightedGroup group = new WeightedGroup(List.of(
                new WeightedGroup.Weighted(silent(), 90),
                new WeightedGroup.Weighted(always("the only one talking"), 10)),
                new SplittableRandom(4242L));

        Map<String, Long> counted = drawn(group, 200);

        assertThat(counted)
                .describedAs("a plan naming a list nobody handed over, or the API's own replies "
                        + "before the API has answered anything, still behaves sensibly")
                .containsOnlyKeys("the only one talking");
        assertThat(counted.get("the only one talking")).isEqualTo(200L);
    }

    @Test
    @DisplayName("a group whose sources all have nothing to say answers nothing, so the next step "
            + "of the plan gets its turn")
    void a_group_that_knows_nothing_stands_aside() {
        WeightedGroup group = new WeightedGroup(List.of(
                new WeightedGroup.Weighted(silent(), 50),
                new WeightedGroup.Weighted(silent(), 50)),
                new SplittableRandom(4242L));

        assertThat(group.offer(ASKING)).isEmpty();
    }

    @Test
    @DisplayName("a source that breaks is one that had nothing to say, and the rest still answer")
    void a_broken_source_does_not_end_the_run() {
        WeightedGroup group = new WeightedGroup(List.of(
                new WeightedGroup.Weighted(broken(), 90),
                new WeightedGroup.Weighted(always("still here"), 10)),
                new SplittableRandom(4242L));

        assertThat(group.offer(ASKING).orElseThrow().value())
                .describedAs("sources are what somebody else is invited to write, and one of them "
                        + "throwing must not end a run that was otherwise going fine")
                .isEqualTo(JsonValue.of("still here"));
    }

    @Test
    @DisplayName("a group needs something in it, and a source in one needs a weight worth having")
    void what_a_group_refuses_to_be() {
        assertThatThrownBy(() -> new WeightedGroup(List.of(), new SplittableRandom(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing in it");
        assertThatThrownBy(() -> new WeightedGroup.Weighted(always("x"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would never be chosen");
    }

    @Test
    @DisplayName("what a report prints for a value a group chose names the sources it chose among")
    void the_group_says_what_it_is_made_of() {
        WeightedGroup group = new WeightedGroup(List.of(
                new WeightedGroup.Weighted(always("a"), 50),
                new WeightedGroup.Weighted(always("b"), 50)),
                new SplittableRandom(1L));

        assertThat(group.name()).isEqualTo("a or b");
    }

    private static Map<String, Long> drawn(WeightedGroup group, int times) {
        return java.util.stream.IntStream.range(0, times)
                .mapToObj(at -> group.offer(ASKING).orElseThrow().value())
                .map(value -> ((JsonValue.JsonString) value).value())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    /** A source that always answers with the same value, named after it. */
    private static ValueProvider always(String value) {
        return new ValueProvider() {
            @Override
            public Optional<GeneratedValue> offer(ValueRequest request) {
                return Optional.of(GeneratedValue.generatedBy(JsonValue.of(value), value));
            }

            @Override
            public String name() {
                return value;
            }
        };
    }

    private static ValueProvider silent() {
        return request -> Optional.empty();
    }

    private static ValueProvider broken() {
        return request -> {
            throw new IllegalStateException("a source with a bug in it");
        };
    }
}
