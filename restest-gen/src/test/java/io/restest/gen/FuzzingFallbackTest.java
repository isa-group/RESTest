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

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.StringSchema;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Why the plan that pushes at the API ends with invention, and what happens without it.
 *
 * <p>The list of awkward values RESTest ships is filed by the kind of value wanted - text, a whole
 * number, and so on - with one catch-all entry for anything else. A parameter whose shape has no
 * kind to look up reaches that catch-all alone, and the catch-all is {@code null}, which cannot be
 * written into a gap in a path.
 *
 * <p>So for a required path parameter that may be a number <em>or</em> a name, the list has nothing
 * to offer, and without something after it the whole request is abandoned: the operation sends
 * nothing for its entire share of the run while still being counted among those under test. The
 * line that prevents this looks like belt and braces and is not, which is why it has a test of its
 * own rather than a comment.
 */
class FuzzingFallbackTest {

    /** A pet is fetched by a number or by a name, which is the shape M2.1b added support for. */
    private static final Operation GET_PET = Operation.of(HttpMethod.GET, "/pets/{petId}",
            List.of(Parameter.of("petId", ParameterLocation.PATH, true,
                    ChoiceSchema.of(List.of(
                            NumberSchema.of(NumberKind.INTEGER), StringSchema.of())))));

    @Test
    @DisplayName("the shipped list of awkward values has nothing it could send for a parameter "
            + "that may be a number or a name, in a path")
    void the_list_of_awkward_values_cannot_answer_here() {
        Dictionary pushing = Dictionaries.fuzzing().orElseThrow();

        List<io.restest.core.json.JsonValue> offered = pushing.valuesFor(
                io.restest.core.gen.ValueRequest.of(GET_PET.id(), "petId",
                        ParameterLocation.PATH, GET_PET.parameters().get(0).schema()));

        assertThat(offered.stream()
                .filter(value -> RequestBuilder.canBeSentFrom(value, ParameterLocation.PATH))
                .toList())
                .describedAs("its one catch-all value is null, and a null written into a path "
                        + "closes the gap instead of filling it")
                .isEmpty();
    }

    @Test
    @DisplayName("so the strategy that pushes still sends something, because invention follows "
            + "the list rather than standing beside it")
    void invention_after_the_list_keeps_the_operation_under_test() throws IOException {
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(GET_PET));
        RandomTestCaseGenerator pushing = new RandomTestCaseGenerator(pets, 4242L,
                List.of(Dictionaries.fuzzing().orElseThrow()),
                Campaigns.shipped().withTheShareOfPushingSetTo(100));

        for (int draw = 0; draw < 50; draw++) {
            assertThat(pushing.generate(GET_PET))
                    .describedAs("take the invention away and this is empty every time, and the "
                            + "operation sends nothing for the whole run while still being "
                            + "counted among those under test")
                    .isPresent();
        }
    }
}
