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
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restest.core.execution.Header;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Three keys handed over, an API that repeats every one of them back in every way it can, and then
 * everything the run left behind searched for them: not one is there.
 *
 * <p>The keys go in a header, in the query and in a cookie, one of them left in the environment
 * rather than typed; the one in the query also fills a field of a form. The stand-in for the API
 * answers with the keys in its headers, in a cookie it sets, in an address it points to, in the
 * JSON it returns, in a reply that is not text at all, and in an error that the run reports as a
 * fault with a {@code curl} command. Every file the run writes, the stored run included, and
 * everything it printed, are then read byte by byte for any way of writing any of the keys.
 *
 * <p>A search that finds nothing proves nothing unless the thing searched for was there to find, so
 * the test also checks that the API did receive every key, in its place, and that what was written
 * holds the text that stands in for each.
 */
class AKeyIsWrittenNowhereTest {

    private static final String IN_A_HEADER = "Zk9-leakprobe-4f+q/Rw==";
    private static final String IN_THE_QUERY = "Qm7-leakprobe-8x+z/Pt==";
    private static final String IN_A_COOKIE = "Hs2-leakprobe-5c-w_Ls=";
    private static final List<String> KEYS = List.of(IN_A_HEADER, IN_THE_QUERY, IN_A_COOKIE);

    /** The part of every key that no way of writing it as text changes. */
    private static final String CORE = "leakprobe";

    private static final String ECHOING_PETS = """
            openapi: 3.0.3
            info: {title: Echoing pets, version: '1'}
            security:
              - inAHeader: []
                inTheQuery: []
                inACookie: []
            paths:
              /pets:
                get:
                  operationId: listPets
                  responses:
                    '200':
                      description: the pets
                      content: {application/json: {schema: {type: object}}}
                post:
                  operationId: addPet
                  requestBody:
                    required: true
                    content:
                      application/x-www-form-urlencoded:
                        schema:
                          type: object
                          required: [name]
                          properties:
                            name: {type: string, maxLength: 12}
                            token: {type: string}
                  responses: {'201': {description: added}}
              /pets/{petId}:
                get:
                  operationId: getPet
                  parameters:
                    - {name: petId, in: path, required: true, schema: {type: integer, minimum: 1, maximum: 9}}
                    - {name: colour, in: query, required: false, schema: {type: string, maxLength: 8}}
                  responses:
                    '200': {description: the pet, content: {application/json: {schema: {type: object}}}}
              /files/{fileId}:
                get:
                  operationId: getFile
                  parameters:
                    - {name: fileId, in: path, required: true, schema: {type: integer, minimum: 1, maximum: 9}}
                  responses:
                    '200': {description: the file}
            components:
              securitySchemes:
                inAHeader: {type: apiKey, name: X-API-Key, in: header}
                inTheQuery: {type: apiKey, name: token, in: query}
                inACookie: {type: apiKey, name: sid, in: cookie}
            """;

    private static WireMockServer api;

    @BeforeAll
    static void startAnApiThatRepeatsEverything() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        String json = "{\"key\":\"" + IN_A_HEADER + "\",\"token\":\""
                + IN_THE_QUERY.replace("/", "\\/") + "\",\"sid\":\"" + IN_A_COOKIE + "\"}";
        api.stubFor(get(urlPathEqualTo("/pets")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Echo", IN_A_HEADER)
                .withHeader("Set-Cookie", "sid=" + IN_A_COOKIE + "; Path=/")
                .withHeader("Link", "</pets?token=" + URLEncoder.encode(IN_THE_QUERY,
                        StandardCharsets.UTF_8) + ">; rel=next")
                .withBody(json)));
        api.stubFor(post(urlPathEqualTo("/pets")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", "/pets/1?token=" + URLEncoder.encode(IN_THE_QUERY,
                        StandardCharsets.UTF_8))
                .withBody("{\"id\":1,\"echo\":\"" + IN_THE_QUERY + "\"}")));
        // A fault, so that the report writes one out in full, with its curl command.
        api.stubFor(get(urlPathMatching("/pets/[0-9]+")).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "text/plain")
                .withBody("java.lang.IllegalStateException: bad key " + IN_A_HEADER + " for "
                        + IN_A_COOKIE)));
        byte[] notText = new byte[64];
        for (int i = 0; i < notText.length; i++) {
            notText[i] = (byte) (0x80 + i);
        }
        byte[] withAKey = new byte[notText.length * 2 + IN_THE_QUERY.length()];
        System.arraycopy(notText, 0, withAKey, 0, notText.length);
        System.arraycopy(IN_THE_QUERY.getBytes(StandardCharsets.US_ASCII), 0, withAKey,
                notText.length, IN_THE_QUERY.length());
        System.arraycopy(notText, 0, withAKey, notText.length + IN_THE_QUERY.length(),
                notText.length);
        api.stubFor(get(urlPathMatching("/files/[0-9]+")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withBody(withAKey)));
    }

    @AfterAll
    static void stop() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    @DisplayName("nothing the run writes or prints holds any of the keys, in any way of writing them")
    void no_key_is_written_anywhere(@TempDir Path directory) throws IOException {
        Path document = directory.resolve("openapi.yaml");
        Files.writeString(document, ECHOING_PETS);
        Path out = directory.resolve("out");
        StringWriter screen = new StringWriter();
        StringWriter problems = new StringWriter();

        int answer;
        try (PrintWriter printed = new PrintWriter(screen);
                PrintWriter complained = new PrintWriter(problems)) {
            answer = Restest.run(new String[] {"run", document.toString(), "--url",
                api.baseUrl(), "--budget", "3s", "--seed", "20260930", "--out", out.toString(),
                "--store", "--auth", "inAHeader=" + IN_A_HEADER, "--auth",
                "inTheQuery=" + IN_THE_QUERY}, printed, complained,
                    Map.of("RESTEST_AUTH", "inACookie=" + IN_A_COOKIE));
        }

        assertThat(answer).describedAs("the planted fault is found: %s%n%s", screen, problems)
                .isEqualTo(1);

        // What was searched for was there to find.
        List<LoggedRequest> received = api.findAll(anyRequestedFor(anyUrl()));
        assertThat(received).isNotEmpty().allSatisfy(request -> {
            assertThat(request.getHeader("X-API-Key")).isEqualTo(IN_A_HEADER);
            assertThat(request.queryParameter("token").values()).containsExactly(IN_THE_QUERY);
            assertThat(request.getCookies().get("sid").getValue()).isEqualTo(IN_A_COOKIE);
        });
        assertThat(received).filteredOn(request -> request.getMethod().getName().equals("POST"))
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.formParameters().get("token").values())
                        .describedAs("the query's key fills the form field of its name")
                        .containsExactly(IN_THE_QUERY));

        // And none of it is in anything the run left behind.
        List<Path> written;
        try (Stream<Path> files = Files.walk(out)) {
            written = files.filter(Files::isRegularFile).toList();
        }
        assertThat(written).extracting(path -> path.getFileName().toString())
                .contains("report.json", "run.sqlite");
        List<String> sought = spellingsOfTheKeys();
        for (Path file : written) {
            String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            assertThat(sought).allSatisfy(spelling -> assertThat(whereFound(bytes, spelling))
                    .describedAs("%s holds %s", file.getFileName(), spelling)
                    .isEmpty());
        }
        assertThat(sought).allSatisfy(spelling -> {
            assertThat(whereFound(screen.toString(), spelling)).describedAs("the screen").isEmpty();
            assertThat(whereFound(problems.toString(), spelling)).describedAs("the errors")
                    .isEmpty();
        });

        // What stands in for each key is where each key went.
        String report = Files.readString(out.resolve("report.json"));
        assertThat(report).contains("REDACTED-AUTH.inAHeader")
                .contains("token=REDACTED-AUTH.inTheQuery")
                .contains("sid=REDACTED-AUTH.inACookie");
        assertThat(screen.toString()).contains("-H 'X-API-Key: REDACTED-AUTH.inAHeader'");

        List<Interaction> stored;
        try (InteractionStore store = SqliteInteractionStore.at(out.resolve("run.sqlite"))) {
            stored = store.find(InteractionQuery.all());
        }
        assertThat(stored).isNotEmpty().allSatisfy(interaction -> {
            assertThat(interaction.request().headers())
                    .contains(Header.of("X-API-Key", "REDACTED-AUTH.inAHeader"));
            assertThat(interaction.request().url()).contains("token=REDACTED-AUTH.inTheQuery");
            assertThat(interaction.request().headerValues("Cookie"))
                    .anySatisfy(cookie -> assertThat(cookie).contains("sid=REDACTED-AUTH.inACookie"));
            for (byte[] body : bodiesOf(interaction)) {
                assertThat(new String(body, StandardCharsets.ISO_8859_1)).doesNotContain(CORE);
            }
        });
        assertThat(stored).filteredOn(interaction -> interaction.request().method().name()
                        .equals("POST"))
                .isNotEmpty()
                .allSatisfy(interaction -> assertThat(new String(interaction.request().body()
                        .orElseThrow().content(), StandardCharsets.UTF_8))
                        .contains("token=REDACTED-AUTH.inTheQuery"));

        // Nothing was invented for the places a key fills, and no change to a request picked one.
        assertThat(stored).extracting(Interaction::testCase).allSatisfy(testCase -> {
            assertThat(testCase.parameterValues()).extracting(ParameterValue::name)
                    .doesNotContain("X-API-Key", "token", "sid");
            testCase.body().map(body -> body.value()).ifPresent(value -> {
                if (value instanceof JsonValue.JsonObject object) {
                    assertThat(object.members()).doesNotContainKey("token");
                }
            });
            testCase.mutation().ifPresent(change -> assertThat(change.path())
                    .doesNotContain("token").doesNotContain("X-API-Key").doesNotContain("sid"));
        });
        assertThat(stored).extracting(Interaction::testCase)
                .describedAs("changes to accepted requests were made, and still left the keys alone")
                .anySatisfy(testCase -> assertThat(testCase.mutation()).isPresent());
    }

    /**
     * Every way the keys could have been written down: as text, whichever way it is escaped - and
     * each way keeps the part the keys share - and in Base64, which is how a body that is not text
     * is kept, starting at each of the three places a key can fall within Base64's groups of three
     * bytes.
     */
    private static List<String> spellingsOfTheKeys() {
        List<String> spellings = new ArrayList<>(KEYS);
        spellings.add(CORE);
        for (String key : KEYS) {
            byte[] bytes = key.getBytes(StandardCharsets.US_ASCII);
            for (int offset = 0; offset < 3; offset++) {
                byte[] shifted = new byte[offset + bytes.length];
                System.arraycopy(bytes, 0, shifted, offset, bytes.length);
                String encoded = Base64.getEncoder().encodeToString(shifted);
                int from = offset == 0 ? 0 : 4;
                int to = shifted.length % 3 == 0 ? encoded.length() : encoded.length() - 4;
                if (to - from >= 8) {
                    spellings.add(encoded.substring(from, to));
                }
            }
        }
        return spellings;
    }

    /** What surrounds the first place the text holds what is sought, or nothing if it does not. */
    private static String whereFound(String text, String sought) {
        int at = text.indexOf(sought);
        if (at < 0) {
            return "";
        }
        return text.substring(Math.max(0, at - 120), Math.min(text.length(), at + sought.length()
                + 120)).replaceAll("[^\\x20-\\x7E]", ".");
    }

    private static List<byte[]> bodiesOf(Interaction interaction) {
        List<byte[]> bodies = new ArrayList<>();
        interaction.request().body().ifPresent(body -> bodies.add(body.content()));
        interaction.response().flatMap(response -> response.body())
                .ifPresent(body -> bodies.add(body.content()));
        return bodies;
    }
}
