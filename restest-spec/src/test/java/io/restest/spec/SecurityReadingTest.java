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
package io.restest.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.SecurityRequirement;
import io.restest.core.model.SecurityScheme;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.schema.ObjectSchema;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a document says a request has to prove, read end to end through the parser a run uses: the
 * schemes it declares, and which of them each operation asks for.
 */
class SecurityReadingTest {

    private final SwaggerSpecificationParser parser = new SwaggerSpecificationParser();

    @Nested
    @DisplayName("a document in the current format")
    class Current {

        @Test
        @DisplayName("every kind of scheme is read as what it is")
        void every_kind_of_scheme_is_read(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    paths: {}
                    components:
                      securitySchemes:
                        inAHeader: {type: apiKey, name: X-API-Key, in: header}
                        inTheQuery: {type: apiKey, name: api_key, in: query}
                        inACookie: {type: apiKey, name: sid, in: cookie}
                        bearer: {type: http, scheme: Bearer, bearerFormat: JWT}
                        basic: {type: http, scheme: basic}
                        oauth:
                          type: oauth2
                          flows:
                            implicit:
                              authorizationUrl: https://example.com/authorize
                              scopes: {}
                        connect: {type: openIdConnect, openIdConnectUrl: https://example.com/.well-known}
                    """);

            assertThat(api.securitySchemes()).containsExactly(
                    Map.entry("inAHeader",
                            new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-API-Key")),
                    Map.entry("inTheQuery",
                            new SecurityScheme.ApiKey(ParameterLocation.QUERY, "api_key")),
                    Map.entry("inACookie", new SecurityScheme.ApiKey(ParameterLocation.COOKIE, "sid")),
                    Map.entry("bearer", new SecurityScheme.Http("bearer", Optional.of("JWT"))),
                    Map.entry("basic", new SecurityScheme.Http("basic", Optional.empty())),
                    Map.entry("oauth", new SecurityScheme.Other("oauth2")),
                    Map.entry("connect", new SecurityScheme.Other("openIdConnect")));
            assertThat(api.issues())
                    .describedAs("a scheme RESTest does not send is not a fault of the document")
                    .isEmpty();
        }

        @Test
        @DisplayName("an operation says nothing, asks for nothing, or offers alternatives, and each is kept apart")
        void what_each_operation_asks_for_is_kept(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    security:
                      - key: []
                    paths:
                      /inherits:
                        get: {operationId: inherits, responses: {'200': {description: ok}}}
                      /nothing:
                        get:
                          operationId: nothing
                          security: []
                          responses: {'200': {description: ok}}
                      /either:
                        get:
                          operationId: either
                          security:
                            - {}
                            - key: []
                              other: []
                          responses: {'200': {description: ok}}
                    components:
                      securitySchemes:
                        key: {type: apiKey, name: X-API-Key, in: header}
                        other: {type: apiKey, name: token, in: query}
                    """);

            assertThat(api.security()).contains(new SecurityRequirement(List.of(Set.of("key"))));
            assertThat(operation(api, "inherits").security()).isEmpty();
            assertThat(api.securityFor(operation(api, "inherits")))
                    .contains(new SecurityRequirement(List.of(Set.of("key"))));
            assertThat(operation(api, "nothing").security()).contains(SecurityRequirement.none());
            assertThat(operation(api, "either").security().orElseThrow().alternatives())
                    .containsExactly(Set.of(), Set.of("key", "other"));
            assertThat(api.issues()).isEmpty();
        }

        @Test
        @DisplayName("a security: [] for the whole document reads as saying nothing, because the parser drops it")
        void a_root_that_asks_for_nothing_reads_as_silence(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    security: []
                    paths:
                      /pets:
                        get: {operationId: listPets, responses: {'200': {description: ok}}}
                    components:
                      securitySchemes:
                        key: {type: apiKey, name: X-API-Key, in: header}
                    """);

            // Pinned as it is, not as it should be: the library the parser is built on keeps an
            // operation's own `security: []` but not the document's. What that costs is written
            // where the placing of keys is decided.
            assertThat(api.security()).isEmpty();
            assertThat(api.securityFor(operation(api, "listPets"))).isEmpty();
        }

        @Test
        @DisplayName("a scheme reached through a reference is the scheme it ends at, and a broken chain says so")
        void references_are_followed(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    paths: {}
                    components:
                      securitySchemes:
                        key: {type: apiKey, name: X-API-Key, in: header}
                        alias: {$ref: '#/components/securitySchemes/key'}
                        aliasOfAlias: {$ref: '#/components/securitySchemes/alias'}
                        nowhere: {$ref: '#/components/securitySchemes/missing'}
                        round: {$ref: '#/components/securitySchemes/andRound'}
                        andRound: {$ref: '#/components/securitySchemes/round'}
                    """);

            SecurityScheme key = new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-API-Key");
            assertThat(api.securitySchemes().get("alias")).isEqualTo(key);
            assertThat(api.securitySchemes().get("aliasOfAlias")).isEqualTo(key);
            assertThat(api.securitySchemes().get("nowhere"))
                    .isInstanceOf(SecurityScheme.Unreadable.class);
            assertThat(api.securitySchemes().get("round"))
                    .isInstanceOf(SecurityScheme.Unreadable.class);
            assertThat(api.securitySchemes().get("andRound"))
                    .isInstanceOf(SecurityScheme.Unreadable.class);
        }

        @Test
        @DisplayName("a key with no name, or no place, is kept as unusable and costs no operation")
        void a_broken_key_costs_nothing_else(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    security:
                      - noName: []
                    paths:
                      /pets:
                        get: {operationId: listPets, responses: {'200': {description: ok}}}
                    components:
                      securitySchemes:
                        noName: {type: apiKey, in: header}
                        noPlace: {type: apiKey, name: key}
                        badPlace: {type: apiKey, name: key, in: body}
                        noKind: {name: key, in: header}
                        noHttpScheme: {type: http}
                    """);

            assertThat(api.operations()).extracting(Operation::id)
                    .containsExactly(OperationId.of("listPets"));
            assertThat(api.securitySchemes().get("noName"))
                    .isEqualTo(new SecurityScheme.Unreadable("it is a key that goes in the header "
                            + "but does not say under which name"));
            assertThat(api.securitySchemes().get("noPlace"))
                    .isEqualTo(new SecurityScheme.Unreadable("it is a key that does not say "
                            + "whether it goes in a header, the query or a cookie"));
            assertThat(api.securitySchemes().get("badPlace"))
                    .isInstanceOf(SecurityScheme.Unreadable.class);
            assertThat(api.securitySchemes().get("noKind"))
                    .isInstanceOf(SecurityScheme.Unreadable.class);
            assertThat(api.securitySchemes().get("noHttpScheme"))
                    .isInstanceOf(SecurityScheme.Unreadable.class);
        }

        @Test
        @DisplayName("asking for a scheme the document never declares is said once, where it is asked")
        void an_undeclared_scheme_is_said(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    security:
                      - missing: []
                    paths:
                      /pets:
                        get: {operationId: listPets, responses: {'200': {description: ok}}}
                        post:
                          operationId: addPet
                          security:
                            - alsoMissing: []
                            - key: []
                          responses: {'201': {description: ok}}
                    components:
                      securitySchemes:
                        key: {type: apiKey, name: X-API-Key, in: header}
                    """);

            assertThat(api.operations()).hasSize(2);
            assertThat(api.issues()).extracting(SpecificationIssue::location,
                            SpecificationIssue::message, SpecificationIssue::effect)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("security",
                                    "the document asks for the security scheme 'missing', which "
                                            + "it does not declare",
                                    SpecificationIssue.Effect.DOCUMENT),
                            org.assertj.core.groups.Tuple.tuple("paths./pets.post.security",
                                    "addPet asks for the security scheme 'alsoMissing', which the "
                                            + "document does not declare",
                                    SpecificationIssue.Effect.DOCUMENT));
        }

        @Test
        @DisplayName("an alternative written as nothing at all lets anybody in, and costs no operation")
        void a_null_alternative_is_the_empty_one(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.0.3
                    info: {title: t, version: '1'}
                    paths:
                      /pets:
                        get:
                          operationId: listPets
                          security: [~]
                          responses: {'200': {description: ok}}
                    """);

            assertThat(api.operations()).hasSize(1);
            assertThat(operation(api, "listPets").security())
                    .hasValueSatisfying(requirement -> assertThat(requirement.alternatives())
                            .allSatisfy(alternative -> assertThat(alternative).isEmpty()));
        }

        @Test
        @DisplayName("in the 3.1 format a key can go in a cookie and a client certificate is kept by name")
        void the_3_1_format_is_read_the_same_way(@TempDir Path dir) throws IOException {
            ApiModel api = parse(dir, """
                    openapi: 3.1.0
                    info: {title: t, version: '1'}
                    paths:
                      /pets:
                        get:
                          operationId: listPets
                          security:
                            - session: []
                          responses: {'200': {description: ok}}
                    components:
                      securitySchemes:
                        session: {type: apiKey, name: sid, in: cookie}
                        certificate: {type: mutualTLS}
                    """);

            assertThat(api.securitySchemes()).containsExactly(
                    Map.entry("session", new SecurityScheme.ApiKey(ParameterLocation.COOKIE, "sid")),
                    Map.entry("certificate", new SecurityScheme.Other("mutualTLS")));
            assertThat(operation(api, "listPets").security())
                    .contains(new SecurityRequirement(List.of(Set.of("session"))));
        }
    }

    @Test
    @DisplayName("a Swagger 2.0 document's definitions and requirements are read the same way")
    void a_2_0_document_is_read_the_same_way(@TempDir Path dir) throws IOException {
        ApiModel api = parse(dir, """
                swagger: '2.0'
                info: {title: t, version: '1'}
                host: example.com
                securityDefinitions:
                  key: {type: apiKey, name: X-API-Key, in: header}
                  basic: {type: basic}
                security:
                  - key: []
                paths:
                  /inherits:
                    get: {operationId: inherits, responses: {'200': {description: ok}}}
                  /nothing:
                    get:
                      operationId: nothing
                      security: []
                      responses: {'200': {description: ok}}
                  /either:
                    get:
                      operationId: either
                      security:
                        - {}
                        - key: []
                      responses: {'200': {description: ok}}
                """);

        assertThat(api.securitySchemes()).containsExactly(
                Map.entry("key", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-API-Key")),
                Map.entry("basic", new SecurityScheme.Http("basic", Optional.empty())));
        assertThat(api.security()).contains(new SecurityRequirement(List.of(Set.of("key"))));
        assertThat(operation(api, "inherits").security()).isEmpty();
        assertThat(operation(api, "nothing").security()).contains(SecurityRequirement.none());
        assertThat(operation(api, "either").security().orElseThrow().alternatives())
                .containsExactly(Set.of(), Set.of("key"));
    }

    @Nested
    @DisplayName("the corpus")
    class TheCorpus {

        @Test
        @DisplayName("eight documents declare a key, four in a header and four in the query, and two ask for it nowhere")
        void the_corpus_declares_keys_as_the_record_says() throws IOException {
            List<String> keyed = new ArrayList<>();
            List<ParameterLocation> where = new ArrayList<>();
            List<String> askedForNowhere = new ArrayList<>();
            for (Path file : realDocuments()) {
                ApiModel api = parser.parse(file.toString());
                Map<String, SecurityScheme> keys = new java.util.LinkedHashMap<>();
                api.securitySchemes().forEach((name, scheme) -> {
                    if (scheme instanceof SecurityScheme.ApiKey) {
                        keys.put(name, scheme);
                    }
                });
                if (keys.isEmpty()) {
                    continue;
                }
                String name = file.getParent().getFileName().toString();
                keyed.add(name);
                keys.values().forEach(key -> where.add(((SecurityScheme.ApiKey) key).location()));
                boolean named = api.security().map(root -> !root.schemesNamed().isEmpty())
                        .orElse(false)
                        || api.operations().stream().anyMatch(operation -> operation.security()
                                .map(own -> own.schemesNamed().stream().anyMatch(keys::containsKey))
                                .orElse(false));
                if (!named) {
                    askedForNowhere.add(name);
                }
            }

            assertThat(keyed).containsExactlyInAnyOrder("Amadeus", "BigOven", "BingWebSearch",
                    "DHL", "Graphhopper", "OMDb", "Petstore", "Tumblr");
            assertThat(where).filteredOn(location -> location == ParameterLocation.HEADER)
                    .hasSize(4);
            assertThat(where).filteredOn(location -> location == ParameterLocation.QUERY)
                    .hasSize(4);
            assertThat(askedForNowhere).containsExactlyInAnyOrder("BigOven", "Tumblr");
        }

        @Test
        @DisplayName("the Petstore declares its key as a scheme and as an ordinary header beside OAuth 2")
        void the_petstore_has_its_key_twice() {
            ApiModel api = parser.parse("specifications/community/Petstore/openapi.yaml");

            assertThat(api.securitySchemes()).containsEntry("api_key",
                    new SecurityScheme.ApiKey(ParameterLocation.HEADER, "api_key"));
            assertThat(api.securitySchemes().get("petstore_auth"))
                    .isEqualTo(new SecurityScheme.Other("oauth2"));
            assertThat(operation(api, "getPetById").security())
                    .contains(new SecurityRequirement(List.of(Set.of("api_key"))));
            Operation deletePet = operation(api, "deletePet");
            assertThat(deletePet.security())
                    .contains(new SecurityRequirement(List.of(Set.of("petstore_auth"))));
            assertThat(deletePet.parameter("api_key", ParameterLocation.HEADER))
                    .hasValueSatisfying(header -> assertThat(header.required()).isFalse());
            assertThat(api.issues()).isEmpty();
        }

        @Test
        @DisplayName("DHL names its scheme with a space in it, and LanguageTool asks for its key as ordinary inputs")
        void two_documents_are_awkward_on_purpose() {
            ApiModel dhl = parser.parse("specifications/community/DHL/openapi.yaml");
            ApiModel languageTool = parser.parse("specifications/community/LanguageTool/openapi.json");

            assertThat(dhl.securitySchemes()).containsEntry("API Key",
                    new SecurityScheme.ApiKey(ParameterLocation.HEADER, "DHL-API-Key"));
            assertThat(languageTool.securitySchemes()).isEmpty();
            Operation words = languageTool.operations().stream()
                    .filter(operation -> operation.method() == HttpMethod.GET
                            && operation.path().equals("/words"))
                    .findFirst().orElseThrow();
            assertThat(words.parameter("apiKey", ParameterLocation.QUERY))
                    .map(Parameter::required).contains(true);
            ObjectSchema form = (ObjectSchema) operation(languageTool, "check").requestBody()
                    .orElseThrow().schemaFor("application/x-www-form-urlencoded").orElseThrow();
            assertThat(form.property("apiKey")).isPresent();
        }

        @Test
        @DisplayName("no document of the corpus asks for a scheme it does not declare")
        void no_requirement_names_an_undeclared_scheme() throws IOException {
            for (Path file : realDocuments()) {
                ApiModel api = parser.parse(file.toString());

                assertThat(api.issues())
                        .describedAs("%s", file)
                        .noneSatisfy(issue -> assertThat(issue.message())
                                .contains("which the document does not declare"));
            }
        }
    }

    private static Operation operation(ApiModel api, String id) {
        return api.operation(OperationId.of(id)).orElseThrow(() -> new AssertionError(
                api.title() + " has no operation " + id));
    }

    private ApiModel parse(Path dir, String document) throws IOException {
        Path file = Files.createTempFile(dir, "openapi", ".yaml");
        Files.writeString(file, document);
        return parser.parse(file.toString());
    }

    /** Every real document of the corpus, without the hand-written broken ones. */
    private static List<Path> realDocuments() throws IOException {
        Path root = specificationsRoot();
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().matches("openapi\\.(yaml|yml|json)"))
                    .filter(file -> !file.startsWith(root.resolve("fixtures")))
                    .sorted()
                    .toList();
        }
    }

    private static Path specificationsRoot() {
        URL resource = SecurityReadingTest.class.getClassLoader().getResource("specifications");
        assertThat(resource).isNotNull();
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
