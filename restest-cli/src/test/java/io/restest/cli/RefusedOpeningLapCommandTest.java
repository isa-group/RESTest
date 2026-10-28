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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.auth.Secrets;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A whole run against an API that answers 401 to everything: it stops sending once its opening
 * lap is over, says why and what to do about the key, and ends with {@code 5}; and what does not
 * stop it.
 */
class RefusedOpeningLapCommandTest {

    /** The key the tests of keys use, and the part of it a leak is looked for by. */
    private static final String KEY = AuthCommandTest.KEY;
    private static final String CORE = AuthCommandTest.CORE;

    /** Far longer than any run here should take, so that a run that went on is told apart. */
    private static final String BUDGET = "30s";

    private static final String BEHIND_A_KEY = """
            openapi: 3.0.3
            info: {title: Shelter behind a key, version: '1'}
            security: [{shelterKey: []}]
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
              /pets/{petId}:
                get:
                  operationId: getPet
                  parameters:
                    - {name: petId, in: path, required: true, schema: {type: integer, minimum: 1, maximum: 9}}
                  responses: {'200': {description: the pet}}
            components:
              securitySchemes:
                shelterKey: {type: apiKey, name: X-Shelter-Key, in: header}
            """;

    private static final String BEHIND_A_TOKEN = """
            openapi: 3.0.3
            info: {title: Shelter behind a token, version: '1'}
            security: [{bearerAuth: []}]
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
            components:
              securitySchemes:
                bearerAuth: {type: http, scheme: bearer}
            """;

    private static final String NO_SCHEME = """
            openapi: 3.0.3
            info: {title: Shelter that says nothing of keys, version: '1'}
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
              /shelters:
                get: {operationId: listShelters, responses: {'200': {description: every shelter}}}
            """;

    /** One operation behind an API key, the other behind OAuth 2. */
    private static final String A_KEY_AND_A_TOKEN = """
            openapi: 3.0.3
            info: {title: Shelter with two ways in, version: '1'}
            paths:
              /pets:
                get:
                  operationId: listPets
                  security: [{shelterKey: []}]
                  responses: {'200': {description: every pet}}
              /shelters:
                get:
                  operationId: listShelters
                  security: [{oauth: []}]
                  responses: {'200': {description: every shelter}}
            components:
              securitySchemes:
                shelterKey: {type: apiKey, name: X-Shelter-Key, in: header}
                oauth:
                  type: oauth2
                  flows: {implicit: {authorizationUrl: 'https://example.com/authorize', scopes: {}}}
            """;

    /** The one operation asks for an API key and an OAuth 2 token together. */
    private static final String A_KEY_WITH_A_TOKEN = """
            openapi: 3.0.3
            info: {title: Shelter asking for both, version: '1'}
            security: [{shelterKey: [], oauth: []}]
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
            components:
              securitySchemes:
                shelterKey: {type: apiKey, name: X-Shelter-Key, in: header}
                oauth:
                  type: oauth2
                  flows: {implicit: {authorizationUrl: 'https://example.com/authorize', scopes: {}}}
            """;

    private static final String A_CERTIFICATE = """
            openapi: 3.1.0
            info: {title: Shelter behind a certificate, version: '1'}
            security: [{cert: []}]
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
            components:
              securitySchemes:
                cert: {type: mutualTLS}
            """;

    private static WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @TempDir
    Path directory;

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
    void refuseEverything() {
        api.resetAll();
        api.stubFor(any(anyUrl()).atPriority(9).willReturn(aResponse().withStatus(401)));
    }

    @Test
    @DisplayName("refused its whole opening lap, a run handed no key stops sending, says how the "
            + "document's key is handed over, and ends with 5")
    void a_run_handed_no_key_stops_and_ends_with_five() throws IOException {
        long began = System.nanoTime();
        int answer = run(Map.of(), BEHIND_A_KEY);
        Duration took = Duration.ofNanos(System.nanoTime() - began);

        assertThat(answer).describedAs("%s%s", screen, problems).isEqualTo(5);
        assertThat(ARun.verdictIn(out())).isEqualTo("unauthorized");
        JsonValue.JsonObject stopped = block();
        assertThat(number(stopped, "lapRequests")).describedAs("one request per operation")
                .isEqualTo(2);
        assertThat(((JsonValue.JsonArray) stopped.member("keysHeld").orElseThrow()).elements())
                .describedAs("it held no key").isEmpty();
        assertThat(screen.toString())
                .contains(", stopped: the API answered 401 to every request of the opening lap");
        assertThat(problems.toString())
                .contains("restest: the API answered 401 to each of the 2 requests of the opening "
                        + "lap, so the run stopped sending after ")
                .contains("(schedule.stopWhenTheOpeningLapIsRefused)")
                .contains("It was handed no key, and the document asks for an API key "
                        + "(shelterKey, in the header X-Shelter-Key): --auth <key> gives it.")
                .describedAs("what is said of a run that nothing answered is not said of this one")
                .doesNotContain("answered any of the");
        assertThat(asked())
                .describedAs("nothing more than the lap, and what was already on its way")
                .isLessThanOrEqualTo(2 + 32);
        assertThat(took).describedAs("thirty seconds were given").isLessThan(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("a document that asks for a token in the Authorization header is told how that "
            + "goes, and one that declares no key at all how any header goes")
    void the_advice_follows_what_the_document_asks_for() throws IOException {
        assertThat(run(Map.of(), BEHIND_A_TOKEN)).describedAs("%s", problems).isEqualTo(5);
        assertThat(problems.toString())
                .contains("It was handed no key, and the document asks for a bearer token "
                        + "(bearerAuth): --auth 'header:Authorization=Bearer <token>' gives it. "
                        + "RESTest does not yet sign in by itself to get a token.");

        problems.getBuffer().setLength(0);
        assertThat(run(Map.of(), NO_SCHEME)).describedAs("%s", problems).isEqualTo(5);
        assertThat(problems.toString())
                .contains("It was handed no key, and the document declares none: --auth "
                        + "'header:<name>=<value>' sends one with every request.");
    }

    @Test
    @DisplayName("a run that held a key the API refused names where the key went, never the key, "
            + "anywhere it writes")
    void a_refused_key_is_named_and_never_written() throws IOException {
        int answer = run(Map.of(), BEHIND_A_KEY, "--auth", KEY, "--store");

        assertThat(answer).describedAs("%s%s", screen, problems).isEqualTo(5);
        assertThat(problems.toString()).contains("It held the key given with --auth, in the header "
                + "X-Shelter-Key, and the API refused it: check that it is the right one and that "
                + "it has not expired.");
        JsonValue.JsonArray held = (JsonValue.JsonArray) block().member("keysHeld").orElseThrow();
        assertThat(held.elements()).hasSize(1);
        assertThat(written(held)).contains("\"in\":\"header\"").contains("\"name\":\"X-Shelter-Key\"");

        String report = Files.readString(out().resolve("report.json"));
        assertThat(report)
                .describedAs("the premise: the requests the report quotes carry the key's mask")
                .contains(Secrets.MARKER);
        List<String> written = new ArrayList<>(List.of(screen.toString(), problems.toString(),
                report, Files.readString(out().resolve("report.html")),
                new String(Files.readAllBytes(out().resolve("run.sqlite")),
                        StandardCharsets.ISO_8859_1)));
        assertThat(written).allSatisfy(text -> assertThat(text).doesNotContain(CORE));
    }

    @Test
    @DisplayName("a key held for some operations is named, and what the others ask for is said "
            + "beside it")
    void a_key_held_and_one_missing_are_both_said() throws IOException {
        assertThat(run(Map.of(), A_KEY_AND_A_TOKEN, "--auth", KEY)).describedAs("%s", problems)
                .isEqualTo(5);
        assertThat(problems.toString())
                .contains("It held the key given with --auth, in the header X-Shelter-Key, and the "
                        + "API refused it")
                .contains("The operations no key went with ask for a token for oauth (oauth2): "
                        + "--auth 'header:Authorization=Bearer <token>' gives one you already "
                        + "have. RESTest does not yet sign in by itself to get a token.");
    }

    @Test
    @DisplayName("a key that went with no operation is said never to have been sent, and the run "
            + "is not told it was handed none")
    void a_key_never_sent_is_not_a_key_never_given() throws IOException {
        assertThat(run(Map.of(), A_KEY_WITH_A_TOKEN, "--auth", "shelterKey=" + KEY))
                .describedAs("%s", problems).isEqualTo(5);
        assertThat(problems.toString())
                .contains("The key given with --auth was never sent: ")
                .contains("The operations no key went with ask for ")
                .doesNotContain("It was handed no key");
    }

    @Test
    @DisplayName("a document that asks for a client certificate is told RESTest cannot present "
            + "one, not to send a token")
    void a_certificate_is_not_a_token() throws IOException {
        assertThat(run(Map.of(), A_CERTIFICATE)).describedAs("%s", problems).isEqualTo(5);
        assertThat(problems.toString())
                .contains("It was handed no key, and the document asks for a client certificate "
                        + "(cert, mutualTLS), which RESTest cannot present.")
                .doesNotContain("Bearer")
                .doesNotContain("sign in");
    }

    @Test
    @DisplayName("a key left in the environment is named as the one in the environment")
    void a_key_in_the_environment_is_named_so() throws IOException {
        assertThat(run(Map.of("RESTEST_AUTH", KEY), BEHIND_A_KEY)).isEqualTo(5);
        assertThat(problems.toString()).contains("It held the key in RESTEST_AUTH, in the header "
                + "X-Shelter-Key, and the API refused it");
    }

    @Test
    @DisplayName("switched off, a run the API refuses goes on until its time is up")
    void switched_off_the_run_goes_on() throws IOException {
        int answer = runFor("2s", Map.of(), BEHIND_A_KEY, "--set",
                "schedule.stopWhenTheOpeningLapIsRefused=false");

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        assertThat(ARun.verdictIn(out())).isEqualTo("noErrorsFound");
        assertThat(ARun.report(out()).member("unauthorized")).contains(JsonValue.NULL);
        assertThat(asked()).describedAs("it kept sending").isGreaterThan(2 + 32);
    }

    @Test
    @DisplayName("an API that answers 403 to everything is not stopped: 403 is not a refusal of "
            + "every key")
    void forbidden_is_not_refused() throws IOException {
        api.stubFor(any(anyUrl()).atPriority(1).willReturn(aResponse().withStatus(403)));

        int answer = runFor("2s", Map.of(), BEHIND_A_KEY);

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        assertThat(ARun.report(out()).member("unauthorized")).contains(JsonValue.NULL);
    }

    @Test
    @DisplayName("an operation of the lap that never answers in time, beside 401 for the others, "
            + "keeps the run going, and the run ends as one that found an error")
    void a_request_with_no_reply_keeps_it_going() throws IOException {
        api.stubFor(any(urlPathMatching("/pets/.*")).atPriority(1)
                .willReturn(aResponse().withStatus(200).withFixedDelay(1_500)));

        int answer = runFor("3s", Map.of(), BEHIND_A_KEY, "--set", "engine.readTimeout=200ms");

        assertThat(answer).describedAs("a reply that never came is an error, F312: %s%s", screen,
                problems).isEqualTo(1);
        assertThat(ARun.report(out()).member("unauthorized")).contains(JsonValue.NULL);
        assertThat(asked()).describedAs("it kept sending").isGreaterThan(2 + 32);
    }

    @Test
    @DisplayName("a switch given a word that is not true or false is refused with 2")
    void a_bad_value_is_refused() throws IOException {
        int answer = run(Map.of(), BEHIND_A_KEY, "--set",
                "schedule.stopWhenTheOpeningLapIsRefused=maybe");

        assertThat(answer).isEqualTo(2);
        assertThat(problems.toString()).contains("schedule.stopWhenTheOpeningLapIsRefused");
        assertThat(asked()).describedAs("nothing was sent").isZero();
    }

    private int run(Map<String, String> environment, String document, String... more)
            throws IOException {
        return runFor(BUDGET, environment, document, more);
    }

    private int runFor(String budget, Map<String, String> environment, String document,
            String... more) throws IOException {
        Path file = Files.writeString(directory.resolve("openapi.yaml"), document);
        List<String> arguments = new ArrayList<>(List.of("run", file.toString(), "--url",
                api.baseUrl(), "--budget", budget, "--seed", "1", "--out", out().toString()));
        arguments.addAll(List.of(more));
        api.resetRequests();
        PrintWriter outWriter = new PrintWriter(screen);
        PrintWriter errWriter = new PrintWriter(problems);
        try {
            return Restest.run(arguments.toArray(String[]::new), outWriter, errWriter, environment);
        } finally {
            outWriter.flush();
            errWriter.flush();
        }
    }

    private Path out() {
        return directory.resolve("out");
    }

    private JsonValue.JsonObject block() {
        return (JsonValue.JsonObject) ARun.report(out()).member("unauthorized").orElseThrow();
    }

    private static long number(JsonValue.JsonObject object, String member) {
        return ((JsonValue.JsonNumber) object.member(member).orElseThrow()).value()
                .longValueExact();
    }

    private static String written(JsonValue value) {
        return JsonText.write(value);
    }

    private static int asked() {
        return api.countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount();
    }
}
