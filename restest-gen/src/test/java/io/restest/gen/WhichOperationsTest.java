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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeping a run to some of an API's operations rather than all of them.
 *
 * <p>What somebody reaches for when the API is one they care about and they want it read rather
 * than written to, or when the whole budget should go on the two operations they are working on.
 */
class WhichOperationsTest {

    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets");
    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets");
    private static final Operation DELETE_PET = Operation.of(HttpMethod.DELETE,
            "/pets/{petId}", List.of(petId()));
    private static final Operation SEARCH = Operation.of(HttpMethod.POST, "/pets/search");

    private static io.restest.core.model.Parameter petId() {
        return io.restest.core.model.Parameter.of("petId",
                io.restest.core.model.ParameterLocation.PATH, true,
                io.restest.core.schema.StringSchema.of());
    }

    @Test
    @DisplayName("narrowing nothing keeps every operation there is")
    void everything_keeps_everything() {
        assertThat(WhichOperations.everything().narrowsAnything()).isFalse();
        assertThat(WhichOperations.everything().matches(ADD_PET)).isTrue();
        assertThat(WhichOperations.of(Set.of(), Set.of()))
                .describedAs("a filter that narrows nothing is the same thing as no filter")
                .isSameAs(WhichOperations.everything());
    }

    @Test
    @DisplayName("keeping to the methods HTTP calls safe loses an API that searches with POST, "
            + "which is why the words 'read' and 'write' are never used here")
    void the_safe_methods_are_http_s_own() {
        WhichOperations safeOnly = WhichOperations.of(WhichOperations.SAFE, Set.of());

        assertThat(safeOnly.matches(LIST_PETS)).isTrue();
        assertThat(safeOnly.matches(ADD_PET)).isFalse();
        assertThat(safeOnly.matches(DELETE_PET)).isFalse();
        assertThat(safeOnly.matches(SEARCH))
                .describedAs("an API that searches with POST is perfectly ordinary, and one of "
                        + "the five in the priority corpus does exactly that: keeping to the safe "
                        + "methods loses it, and calling the filter 'read-only' would be a lie")
                .isFalse();
    }

    @Test
    @DisplayName("an operation is named either way a document lets you name it")
    void an_operation_answers_to_both_of_its_names() {
        Operation named = Operation.of(HttpMethod.GET, "/pets/{petId}", List.of(petId()))
                .withId(io.restest.core.model.OperationId.of("getPetById"));

        assertThat(WhichOperations.of(Set.of(), Set.of("getPetById")).matches(named))
                .describedAs("the identifier the document declares")
                .isTrue();
        assertThat(WhichOperations.of(Set.of(), Set.of("GET /pets/{petId}")).matches(named))
                .describedAs("or the method and path, which anybody can read off the document "
                        + "without checking whether an identifier is there at all")
                .isTrue();
        assertThat(WhichOperations.of(Set.of(), Set.of("getPet")).matches(named)).isFalse();
    }

    @Test
    @DisplayName("both halves apply together: named, but with the wrong method, is not kept")
    void the_two_halves_are_both_asked() {
        WhichOperations both = WhichOperations.of(Set.of(HttpMethod.GET), Set.of("POST /pets"));

        assertThat(both.matches(ADD_PET))
                .describedAs("named, but not a GET")
                .isFalse();
        assertThat(both.matches(LIST_PETS))
                .describedAs("a GET, but not named")
                .isFalse();
    }

    @Test
    @DisplayName("an operation named that this API does not have is reported, because a plan "
            + "written against an older document tests less than its author believes")
    void a_name_this_api_does_not_have_is_named_back() {
        ApiModel pets = ApiModel.of("Pets", "1.0", List.of(LIST_PETS, ADD_PET));

        assertThat(WhichOperations.of(Set.of(), List.of("GET /pets", "getOwner", "deleteOwner"))
                .namesNothingMatches(pets))
                .describedAs("in the order they were written, which is how their author will "
                        + "find them in the file")
                .containsExactly("getOwner", "deleteOwner");
        assertThat(WhichOperations.of(Set.of(), List.of("GET /pets")).namesNothingMatches(pets))
                .isEmpty();
    }
}
