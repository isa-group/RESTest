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
package io.restest.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An API that answers only a request carrying its key, and the command handed that key: where it
 * goes, what the run says about it, and what the command refuses.
 *
 * <p>The stand-in for the API refuses anything without the key, with 401, the way a real protected
 * API does. So a request that reached it without the key, or with the key in the wrong place, shows
 * up as a refusal, and the tests below count them.
 */
class AuthCommandTest {

    /** A key in Base64's alphabet, so that every way of writing it down differs from it. */
    static final String KEY = "Zk9-leakprobe-4f+q/Rw==";

    /** The part of the key every way of writing it keeps: what a leak is looked for by. */
    static final String CORE = "leakprobe";

    private static final String PETSTORE = """
            openapi: 3.0.3
            info: {title: Keyed pets, version: '1'}
            paths:
              /pets:
                get:
                  operationId: listPets
                  security: [{api_key: []}]
                  responses: {'200': {description: the pets}}
              /pets/{petId}:
                delete:
                  operationId: deletePet
                  parameters:
                    - {name: petId, in: path, required: true, schema: {type: integer, minimum: 1, maximum: 9}}
                    - {name: api_key, in: header, required: false, schema: {type: string}}
                  security: [{petstore_auth: []}]
                  responses: {'204': {description: gone}}
              /health:
                get:
                  operationId: health
                  security: []
                  responses: {'200': {description: well}}
            components:
              securitySchemes:
                api_key: {type: apiKey, name: api_key, in: header}
                petstore_auth:
                  type: oauth2
                  flows: {implicit: {authorizationUrl: 'https://example.com/authorize', scopes: {}}}
            """;

    private static final String TWO_KEYS = """
            openapi: 3.0.3
            info: {title: Two keys, version: '1'}
            paths:
              /reports:
                get:
                  operationId: listReports
                  security: [{token: []}]
                  responses: {'200': {description: the reports}}
              /session:
                get:
                  operationId: whoAmI
                  security: [{session: []}]
                  responses: {'200': {description: you}}
            components:
              securitySchemes:
                token: {type: apiKey, name: token, in: query}
                session: {type: apiKey, name: sid, in: cookie}
            """;

    private static final String WORDS = """
            swagger: '2.0'
            info: {title: Words, version: '1'}
            consumes: [application/x-www-form-urlencoded]
            produces: [application/json]
            paths:
              /words:
                get:
                  operationId: words
                  parameters:
                    - {name: apiKey, in: query, required: true, type: string}
                  responses: {'200': {description: the words}}
              /words/add:
                post:
                  operationId: addWord
                  parameters:
                    - {name: word, in: formData, required: true, type: string}
                    - {name: apiKey, in: formData, required: true, type: string}
                  responses: {'200': {description: added}}
            """;

    private static final String NO_SCHEME = """
            openapi: 3.0.3
            info: {title: Open pets, version: '1'}
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: the pets}}}
            """;

    private static WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
    }

    @AfterAll
    static void stopTheApi() {
        if (api != null) {
            api.stop();
        }
    }

    @BeforeEach
    void refuseEverythingWithoutItsKey() {
        api.resetAll();
        api.stubFor(any(anyUrl()).atPriority(9).willReturn(aResponse().withStatus(401)));
    }

    @Nested
    @DisplayName("where the key goes")
    class WhereItGoes {

        @Test
        @DisplayName("in the header the scheme names, with the operations that ask for it and the one that declares it")
        void in_a_header(@TempDir Path directory) throws IOException {
            api.stubFor(any(anyUrl()).withHeader("api_key", equalTo(KEY)).atPriority(1)
                    .willReturn(aResponse().withStatus(200)));

            int answer = run(Map.of(), "run", document(directory, PETSTORE), "--url",
                    api.baseUrl(), "--budget", "2s", "--seed", "20260930", "--out",
                    directory.resolve("out").toString(), "--auth", KEY);

            assertThat(answer).describedAs("%s%n%s", screen, problems).isBetween(0, 1);
            List<LoggedRequest> pets = api.findAll(getRequestedFor(urlPathEqualTo("/pets")));
            assertThat(pets).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.getHeaders()
                            .getHeader("api_key").values()).containsExactly(KEY));
            List<LoggedRequest> deletions = api.findAll(
                    deleteRequestedFor(urlPathMatching("/pets/.*")));
            assertThat(deletions)
                    .describedAs("the header deletePet declares under the key's name is the key's")
                    .isNotEmpty()
                    .allSatisfy(request -> assertThat(request.getHeaders()
                            .getHeader("api_key").values()).containsExactly(KEY));
            assertThat(api.findAll(getRequestedFor(urlPathEqualTo("/health"))))
                    .describedAs("an operation that says it asks for nothing is not sent the key")
                    .isNotEmpty()
                    .allSatisfy(request -> assertThat(request.containsHeader("api_key")).isFalse());
            assertThat(screen.toString()).contains("  the key given with --auth goes with 2 of "
                    + "them, in the header api_key; what the run writes says REDACTED-AUTH in its "
                    + "place");
        }

        @Test
        @DisplayName("in the query and in a cookie, each for the scheme it names")
        void in_the_query_and_in_a_cookie(@TempDir Path directory) throws IOException {
            String cookie = "Ab3-leakprobe-9x+/Qw==";
            api.stubFor(any(urlPathEqualTo("/reports")).withQueryParam("token", equalTo(KEY))
                    .atPriority(1).willReturn(aResponse().withStatus(200)));
            api.stubFor(any(urlPathEqualTo("/session")).withCookie("sid", equalTo(cookie))
                    .atPriority(1).willReturn(aResponse().withStatus(200)));

            int answer = run(Map.of(), "run", document(directory, TWO_KEYS), "--url",
                    api.baseUrl(), "--budget", "2s", "--seed", "20260930", "--out",
                    directory.resolve("out").toString(), "--auth", "token=" + KEY, "--auth",
                    "session=" + cookie);

            assertThat(answer).describedAs("%s%n%s", screen, problems).isBetween(0, 1);
            assertThat(api.findAll(getRequestedFor(urlPathEqualTo("/reports")))).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.queryParameter("token").values())
                            .containsExactly(KEY));
            assertThat(api.findAll(getRequestedFor(urlPathEqualTo("/session")))).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.getCookies().get("sid").getValue())
                            .describedAs("a cookie goes as it was given, never percent-encoded")
                            .isEqualTo(cookie));
            assertThat(refusals()).isZero();
        }

        @Test
        @DisplayName("given with its place, it goes everywhere and fills the query and form inputs of its name")
        void with_its_place(@TempDir Path directory) throws IOException {
            api.stubFor(any(urlPathEqualTo("/words")).withQueryParam("apiKey", equalTo(KEY))
                    .atPriority(1).willReturn(aResponse().withStatus(200)));
            api.stubFor(any(urlPathEqualTo("/words/add")).withFormParam("apiKey", equalTo(KEY))
                    .atPriority(1).willReturn(aResponse().withStatus(200)));

            int answer = run(Map.of(), "run", document(directory, WORDS), "--url", api.baseUrl(),
                    "--budget", "2s", "--seed", "20260930", "--out",
                    directory.resolve("out").toString(), "--auth", "query:apiKey=" + KEY);

            assertThat(answer).describedAs("%s%n%s", screen, problems).isBetween(0, 1);
            assertThat(api.findAll(getRequestedFor(urlPathEqualTo("/words")))).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.queryParameter("apiKey").values())
                            .describedAs("the key once, and nothing invented beside it")
                            .containsExactly(KEY));
            assertThat(api.findAll(postRequestedFor(urlPathEqualTo("/words/add")))).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.formParameters().get("apiKey")
                            .values()).containsExactly(KEY));
            assertThat(refusals()).isZero();
            assertThat(screen.toString()).contains("  the key given with --auth goes with every "
                    + "one of them, in the query parameter apiKey and the form field apiKey");
        }
    }

    @Nested
    @DisplayName("a key left in the environment")
    class InTheEnvironment {

        @Test
        @DisplayName("is sent as one typed would be, and one typed for the same place wins over it")
        void it_is_sent_and_a_typed_one_wins(@TempDir Path directory) throws IOException {
            api.stubFor(any(anyUrl()).withHeader("api_key", equalTo(KEY)).atPriority(1)
                    .willReturn(aResponse().withStatus(200)));

            int alone = run(Map.of("RESTEST_AUTH", KEY), "run", document(directory, PETSTORE),
                    "--url", api.baseUrl(), "--budget", "1s", "--seed", "20260930", "--out",
                    directory.resolve("alone").toString());
            assertThat(alone).describedAs("%s%n%s", screen, problems).isBetween(0, 1);
            assertThat(api.findAll(getRequestedFor(urlPathEqualTo("/pets")))).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.getHeader("api_key")).isEqualTo(KEY));
            assertThat(screen.toString()).contains("  the key in RESTEST_AUTH goes with 2 of them");

            api.resetRequests();
            int overruled = run(Map.of("RESTEST_AUTH", "api_key=the-one-left-behind"), "run",
                    document(directory, PETSTORE), "--url", api.baseUrl(), "--budget", "1s",
                    "--seed", "20260930", "--out", directory.resolve("overruled").toString(),
                    "--auth", KEY);
            assertThat(overruled).isBetween(0, 1);
            assertThat(api.findAll(anyRequestedFor(anyUrl())))
                    .noneSatisfy(request -> assertThat(request.getHeader("api_key"))
                            .isEqualTo("the-one-left-behind"));
        }

        @Test
        @DisplayName("that cannot be used is left out with a warning, and the run goes on")
        void one_that_cannot_be_used_only_warns(@TempDir Path directory) throws IOException {
            api.stubFor(any(anyUrl()).atPriority(1).willReturn(aResponse().withStatus(200)));

            int answer = run(Map.of("RESTEST_AUTH", KEY), "run", document(directory, NO_SCHEME),
                    "--url", api.baseUrl(), "--budget", "1s", "--seed", "20260930", "--out",
                    directory.resolve("out").toString());

            assertThat(answer).describedAs("%s%n%s", screen, problems).isBetween(0, 1);
            assertThat(problems.toString()).contains("restest: the key in RESTEST_AUTH is a key "
                    + "on its own").contains("the run goes on without it").doesNotContain(CORE);
            assertThat(api.findAll(getRequestedFor(urlPathEqualTo("/pets")))).isNotEmpty();
        }

        @Test
        @DisplayName("is never printed with the settings, which it is not one of")
        void it_is_not_a_setting() {
            int answer = run(Map.of("RESTEST_AUTH", KEY), "run", "--print-settings");

            assertThat(answer).isZero();
            assertThat(screen.toString()).isNotEmpty().doesNotContain(CORE);
        }
    }

    @Nested
    @DisplayName("what the run says before it sends anything")
    class BeforeTheFirstRequest {

        @Test
        @DisplayName("a key the document asks for and nobody gave is named, with the option that gives it")
        void a_missing_key_is_named(@TempDir Path directory) throws IOException {
            int answer = run(Map.of(), "run", document(directory, PETSTORE), "--url",
                    "http://127.0.0.1:1", "--budget", "1ms", "--seed", "20260930", "--out",
                    directory.resolve("out").toString());

            assertThat(answer).describedAs("a key not given is not a mistake").isNotEqualTo(2);
            assertThat(screen.toString().lines().limit(4)).containsExactly(
                    "RESTest testing Keyed pets at http://127.0.0.1:1", "",
                    "3 of 3 operations can be tested, seed 20260930, budget 1ms",
                    "  1 of them asks for an API key that was not given (api_key, in the header "
                            + "api_key): --auth <key> gives it");
        }

        @Test
        @DisplayName("a document with several keys says which to name, and one asked for nowhere says it would go everywhere")
        void the_option_named_fits_the_document(@TempDir Path directory) throws IOException {
            String nowhere = """
                    openapi: 3.0.3
                    info: {title: Recipes, version: '1'}
                    paths:
                      /recipes:
                        get: {operationId: recipes, responses: {'200': {description: some}}}
                      /open:
                        get: {operationId: open, security: [], responses: {'200': {description: open}}}
                    components:
                      securitySchemes:
                        api key: {type: apiKey, name: X-Recipe-Key, in: header}
                        other: {type: apiKey, name: other, in: query}
                    """;

            run(Map.of(), "run", document(directory, TWO_KEYS), "--url", "http://127.0.0.1:1",
                    "--budget", "1ms", "--seed", "20260930", "--out",
                    directory.resolve("two").toString());
            assertThat(screen.toString()).contains("  1 of them asks for an API key that was not "
                    + "given (token, in the query parameter token): --auth token=<key> gives it")
                    .contains("  1 of them asks for an API key that was not given (session, in "
                            + "the cookie sid): --auth session=<key> gives it");

            screen.getBuffer().setLength(0);
            run(Map.of(), "run", document(directory, nowhere), "--url", "http://127.0.0.1:1",
                    "--budget", "1ms", "--seed", "20260930", "--out",
                    directory.resolve("nowhere").toString());
            assertThat(screen.toString()).contains("  the document declares an API key it asks "
                    + "for on no operation (api key, in the header X-Recipe-Key): --auth "
                    + "'api key=<key>' sends it with 1 of them");
        }
    }

    @Nested
    @DisplayName("what the command refuses")
    class Refusals {

        @Test
        @DisplayName("a key that cannot be placed ends the command before anything is sent, and is never repeated")
        void a_key_that_cannot_be_placed_is_refused(@TempDir Path directory) throws IOException {
            String pets = document(directory, PETSTORE);
            String two = document(directory, TWO_KEYS);
            String none = document(directory, NO_SCHEME);
            List<List<String>> refused = List.of(
                    List.of(two, "--auth", KEY),
                    List.of(none, "--auth", KEY),
                    List.of(pets, "--auth", "petstore_auth=" + KEY),
                    List.of(pets, "--auth", "header:=" + KEY),
                    List.of(pets, "--auth", KEY + "\n"),
                    List.of(pets, "--auth", ""),
                    List.of(pets, "--auth", KEY, "--auth", "api_key=" + KEY),
                    List.of(pets, "--auth", KEY, "--set", "engine.followRedirects=true"));

            for (List<String> arguments : refused) {
                api.resetRequests();
                screen.getBuffer().setLength(0);
                problems.getBuffer().setLength(0);
                String[] typed = new String[arguments.size() + 7];
                typed[0] = "run";
                for (int i = 0; i < arguments.size(); i++) {
                    typed[i + 1] = arguments.get(i);
                }
                String[] rest = {"--url", api.baseUrl(), "--budget", "1s", "--out",
                    directory.resolve("out").toString()};
                System.arraycopy(rest, 0, typed, arguments.size() + 1, rest.length);

                int answer = run(Map.of(), typed);

                assertThat(answer).describedAs("%s: %s", arguments.subList(1, arguments.size()),
                        problems).isEqualTo(2);
                assertThat(problems.toString()).startsWith("restest: ");
                assertThat(screen.toString() + problems).doesNotContain(CORE);
                assertThat(api.findAll(anyRequestedFor(anyUrl()))).isEmpty();
            }
        }

        @Test
        @DisplayName("a mistyped option names itself and does not repeat what followed it")
        void a_mistyped_option_does_not_repeat_the_key(@TempDir Path directory)
                throws IOException {
            int answer = run(Map.of(), "run", document(directory, PETSTORE), "--auht", KEY,
                    "--url", "http://127.0.0.1:1");
            int glued = run(Map.of(), "run", document(directory, PETSTORE), "--auht=" + KEY);

            assertThat(answer).isEqualTo(2);
            assertThat(glued).isEqualTo(2);
            assertThat(problems.toString()).contains("Unknown option: --auht")
                    .contains("not repeated here in case one is a key")
                    .contains("--auth")
                    .doesNotContain(CORE);
        }
    }

    /** How many requests the stand-in refused, which a request carrying its key never is. */
    private static long refusals() {
        return api.getAllServeEvents().stream()
                .filter(event -> event.getResponse().getStatus() == 401)
                .count();
    }

    static String document(Path directory, String text) throws IOException {
        Path file = Files.createTempFile(directory, "openapi", ".yaml");
        Files.writeString(file, text);
        return file.toString();
    }

    private int run(Map<String, String> environment, String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);
        try {
            return Restest.run(arguments, out, err, environment);
        } finally {
            out.flush();
            err.flush();
        }
    }
}
