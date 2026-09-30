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
package io.restest.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.restest.core.schema.StringSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What a document says a request has to prove, as the model holds it. */
class SecurityTest {

    private static final SecurityRequirement THE_KEY =
            new SecurityRequirement(List.of(Set.of("api_key")));

    @Nested
    @DisplayName("a scheme")
    class Schemes {

        @Test
        @DisplayName("a key travels in a header, the query or a cookie, and nowhere else")
        void a_key_travels_where_a_key_can() {
            assertThat(new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-API-Key").described())
                    .isEqualTo("an API key in the header X-API-Key");
            assertThat(new SecurityScheme.ApiKey(ParameterLocation.QUERY, "api_key").described())
                    .isEqualTo("an API key in the query api_key");
            assertThat(new SecurityScheme.ApiKey(ParameterLocation.COOKIE, "sid").described())
                    .isEqualTo("an API key in the cookie sid");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SecurityScheme.ApiKey(ParameterLocation.PATH, "key"))
                    .withMessageContaining("not in the path");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SecurityScheme.ApiKey(ParameterLocation.BODY, "key"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SecurityScheme.ApiKey(ParameterLocation.HEADER, " "))
                    .withMessageContaining("name");
            assertThatNullPointerException()
                    .isThrownBy(() -> new SecurityScheme.ApiKey(null, "key"));
        }

        @Test
        @DisplayName("an HTTP scheme is named the way HTTP names it, whatever the document's case")
        void an_http_scheme_is_named_in_lower_case() {
            SecurityScheme.Http bearer = new SecurityScheme.Http(" Bearer ", Optional.of("JWT"));

            assertThat(bearer.scheme()).isEqualTo("bearer");
            assertThat(bearer.bearerFormat()).contains("JWT");
            assertThat(bearer.described()).isEqualTo("an HTTP bearer credential");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SecurityScheme.Http("", Optional.empty()));
        }

        @Test
        @DisplayName("the kinds RESTest does not send are kept by name, and a broken one says why")
        void other_kinds_are_kept() {
            assertThat(new SecurityScheme.Other("oauth2").described())
                    .isEqualTo("a scheme of type oauth2");
            assertThat(new SecurityScheme.Unreadable("it names no header").described())
                    .isEqualTo("a scheme that cannot be used: it names no header");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SecurityScheme.Other(" "));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SecurityScheme.Unreadable(""));
        }
    }

    @Nested
    @DisplayName("a requirement")
    class Requirements {

        @Test
        @DisplayName("security: [] asks for nothing, and one empty alternative lets anybody in")
        void asking_for_nothing_and_letting_anybody_in_differ() {
            SecurityRequirement nothing = SecurityRequirement.none();
            SecurityRequirement anybody = new SecurityRequirement(List.of(Set.of()));

            assertThat(nothing.asksForNothing()).isTrue();
            assertThat(nothing.schemesNamed()).isEmpty();
            assertThat(anybody.asksForNothing()).isFalse();
            assertThat(anybody.alternatives()).containsExactly(Set.of());
        }

        @Test
        @DisplayName("alternatives keep the order the document lists them in, and so do the schemes")
        void the_order_of_the_document_is_kept() {
            Set<String> both = new LinkedHashSet<>(List.of("zeta", "alpha"));
            SecurityRequirement requirement = new SecurityRequirement(
                    List.of(both, Set.of("alpha"), Set.of("middle")));

            assertThat(requirement.alternatives().get(0)).containsExactly("zeta", "alpha");
            assertThat(requirement.schemesNamed()).containsExactly("zeta", "alpha", "middle");
        }

        @Test
        @DisplayName("changing what it was built from afterwards changes nothing")
        void it_copies_what_it_is_given() {
            Set<String> alternative = new LinkedHashSet<>(List.of("api_key"));
            List<Set<String>> alternatives = new ArrayList<>(List.of(alternative));
            SecurityRequirement requirement = new SecurityRequirement(alternatives);

            alternative.add("petstore_auth");
            alternatives.add(Set.of("other"));

            assertThat(requirement.alternatives()).containsExactly(Set.of("api_key"));
        }

        @Test
        @DisplayName("a missing scheme name is refused rather than carried")
        void a_null_name_is_refused() {
            Set<String> withANull = new LinkedHashSet<>();
            withANull.add(null);

            assertThatNullPointerException()
                    .isThrownBy(() -> new SecurityRequirement(List.of(withANull)));
            assertThatNullPointerException().isThrownBy(() -> new SecurityRequirement(null));
        }
    }

    @Nested
    @DisplayName("the API and its operations")
    class Inheritance {

        private final Operation says = Operation.of(HttpMethod.GET, "/inventory")
                .withSecurity(SecurityRequirement.none());
        private final Operation silent = Operation.of(HttpMethod.GET, "/pets");

        @Test
        @DisplayName("an operation's own requirement wins, and the API's applies where it says nothing")
        void the_operation_wins_over_the_api() {
            ApiModel api = ApiModel.of("Petstore", "1.0", List.of(says, silent))
                    .withSecurity(Map.of("api_key",
                            new SecurityScheme.ApiKey(ParameterLocation.HEADER, "api_key")),
                            Optional.of(THE_KEY));

            assertThat(api.securityFor(says)).contains(SecurityRequirement.none());
            assertThat(api.securityFor(silent)).contains(THE_KEY);
        }

        @Test
        @DisplayName("where neither says anything there is nothing to prove, and that is not security: []")
        void nothing_said_anywhere_is_not_nothing_asked() {
            ApiModel api = ApiModel.of("Petstore", "1.0", List.of(says, silent));

            assertThat(api.securityFor(silent)).isEmpty();
            assertThat(api.securityFor(says)).contains(SecurityRequirement.none());
            assertThat(api.securitySchemes()).isEmpty();
        }

        @Test
        @DisplayName("what a document says about security survives the model and its operations being rebuilt")
        void security_survives_every_builder() {
            Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
            schemes.put("api_key", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "api_key"));
            ApiModel api = ApiModel.of("Petstore", "1.0", List.of(says))
                    .withSecurity(schemes, Optional.of(THE_KEY))
                    .withSchemas(Map.of())
                    .withIssues(List.of())
                    .withDocument("{}")
                    .withOperations(List.of(silent, says));
            schemes.put("later", new SecurityScheme.Other("oauth2"));

            assertThat(api.securitySchemes()).containsOnlyKeys("api_key");
            assertThat(api.security()).contains(THE_KEY);
            assertThat(api.operations()).containsExactly(silent, says);

            Operation rebuilt = says.withParameters(List.of())
                    .withRequestBody(RequestBodyModel.json(StringSchema.of(), false))
                    .withResponses(List.of())
                    .withServers(List.of())
                    .withId(OperationId.of("getInventory"));
            assertThat(rebuilt.security()).contains(SecurityRequirement.none());
        }

        @Test
        @DisplayName("the constructor a document with nothing to say about security uses says nothing")
        void the_older_constructors_say_nothing() {
            ApiModel api = new ApiModel("Petstore", "1.0", List.of(), List.of(silent), Map.of(),
                    List.of(), Optional.empty());
            Operation operation = new Operation(OperationId.of("listPets"), HttpMethod.GET,
                    "/pets", List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                    Optional.empty(), Optional.empty(), false);

            assertThat(api.securitySchemes()).isEmpty();
            assertThat(api.security()).isEmpty();
            assertThat(operation.security()).isEmpty();
            assertThatNullPointerException().isThrownBy(() -> silent.withSecurity(null));
        }
    }
}
