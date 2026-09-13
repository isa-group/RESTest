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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.exec.OkHttpEngine;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.gen.RequestBuilder;
import io.restest.oracles.OracleListener;
import io.restest.report.ConsoleReport;
import io.restest.report.JsonReport;
import io.restest.spec.SwaggerSpecificationParser;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole tool, end to end, against an API that misbehaves on purpose.
 *
 * <p>Every other test in this project checks one part on its own. This one puts all of them
 * together, which is the only way to find out whether they actually fit: a specification is read,
 * requests are invented from it, they are sent to a real HTTP server, what comes back is judged,
 * and what was judged wrong is reported, stored and turned into a command a person can run.
 *
 * <p>The API is a stand-in that answers exactly as this test tells it to, so there is no network,
 * no waiting, and no chance of the test failing because somebody's real server had a bad day. It is
 * wrong in two different ways on purpose - one operation falls over, one lies about the shape of
 * what it returns - and a third operation is perfectly well behaved, so that "found two faults" is
 * not the same sentence as "complained about everything".
 *
 * <p>This is the shape the {@code restest run} command will take. The command itself is the next
 * increment; what it will do is what happens below.
 */
class WholeRunTest {

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();

        // Falls over. Nobody can defend this one.
        api.stubFor(get(urlMatching("/pets")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"could not reach the database\"}")));

        // Answers, but not with what it said it would: the document promises a whole number.
        api.stubFor(get(urlMatching("/pets/[0-9]+")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": \"seven\", \"name\": \"Rex\"}")));

        // Behaves perfectly.
        api.stubFor(get(urlMatching("/shelters")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"total\": 3}")));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("a whole run reads a specification, tests an API, and reports what is wrong")
    void a_whole_run_finds_and_reports_what_is_wrong(@TempDir Path directory) throws IOException {
        String baseUrl = api.baseUrl();
        ApiModel model = new SwaggerSpecificationParser().parse("pet-shelter.yaml");
        assertThat(model.isComplete()).describedAs("the specification reads without complaint")
                .isTrue();

        StringBuilder screen = new StringBuilder();
        Path reportFile = directory.resolve("report.json");
        JsonReport json = JsonReport.to(reportFile);
        Path runFile = directory.resolve("run.sqlite");

        Instant startedAt = Instant.now();
        // Closed in reverse: the announcements are drained first, so everything listening to them
        // has finished with the store and the engine before either of those is shut.
        try (InteractionStore store = SqliteInteractionStore.at(runFile);
                HttpEngine engine = new OkHttpEngine();
                EventStream events = new EventStream()) {

            events.subscribe(event -> {
                if (event instanceof RunEvent.InteractionCompleted completed) {
                    store.record(completed.interaction());
                }
            });
            events.subscribe(OracleListener.standard(model, events));
            events.subscribe(ConsoleReport.to(screen));
            events.subscribe(json);

            events.publish(new RunEvent.RunStarted(startedAt, model.title(), baseUrl));

            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260913L);
            for (Operation operation : generator.testableOperations()) {
                TestCase testCase = generator.generate(operation).orElseThrow();
                events.publish(new RunEvent.TestCasePlanned(Instant.now(), testCase));
                Interaction attempt = engine.send(testCase,
                        RequestBuilder.build(operation, testCase, baseUrl));
                events.publish(new RunEvent.InteractionCompleted(Instant.now(), attempt));
            }

            events.publish(new RunEvent.RunFinished(Instant.now(),
                    Duration.between(startedAt, Instant.now()), engine.statistics()));
            assertThat(events.listenerFailures())
                    .describedAs("nothing listening to the run broke").isZero();
        }

        String printed = screen.toString();
        assertThat(printed)
                .describedAs("the API that fell over is reported by its catalogue number")
                .contains("F100")
                .contains("HTTP Status 500")
                .contains("listPets - GET " + baseUrl + "/pets");
        assertThat(printed)
                .describedAs("the API that lied about its own shape is reported too")
                .contains("F101")
                .contains("getPet - GET " + baseUrl + "/pets/")
                .contains("/id: string found, integer expected");
        assertThat(printed)
                .describedAs("the operation that behaved is not complained about")
                .doesNotContain("listShelters");
        assertThat(printed).contains("2 faults:");

        JsonValue.JsonObject report =
                (JsonValue.JsonObject) JsonText.read(Files.readString(reportFile));
        assertThat(codesIn(report)).containsExactlyInAnyOrder(100, 101);

        assertThat(store(runFile)).describedAs("every attempt is kept, faulty or not").isEqualTo(3);
    }

    @Test
    @DisplayName("the curl command printed beside a fault really does reproduce it")
    void the_printed_command_reproduces_the_fault(@TempDir Path directory) throws Exception {
        Assumptions.assumeTrue(curlIsInstalled(), "curl is not installed on this machine");

        String baseUrl = api.baseUrl();
        ApiModel model = new SwaggerSpecificationParser().parse("pet-shelter.yaml");
        StringBuilder screen = new StringBuilder();

        try (HttpEngine engine = new OkHttpEngine(); EventStream events = new EventStream()) {
            events.subscribe(OracleListener.standard(model, events));
            events.subscribe(ConsoleReport.to(screen));

            Operation listPets = model.operations().stream()
                    .filter(operation -> operation.path().equals("/pets"))
                    .findFirst().orElseThrow();
            TestCase testCase = new RandomTestCaseGenerator(model, 1L).generate(listPets)
                    .orElseThrow();
            events.publish(new RunEvent.InteractionCompleted(Instant.now(),
                    engine.send(testCase, RequestBuilder.build(listPets, testCase, baseUrl))));
        }

        String command = screen.toString().lines()
                .map(String::trim)
                .filter(line -> line.startsWith("curl "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no command was printed beside the fault"));

        Path script = directory.resolve("reproduce.sh");
        Files.writeString(script, command + System.lineSeparator());
        Process shell = new ProcessBuilder("sh", script.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(shell.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(shell.waitFor(30, TimeUnit.SECONDS))
                .describedAs("the command finished").isTrue();

        assertThat(output)
                .describedAs("running what was printed produces the same failure: %s", command)
                .contains("500");
    }

    private static List<Integer> codesIn(JsonValue.JsonObject report) {
        JsonValue.JsonArray findings =
                (JsonValue.JsonArray) report.member("findings").orElseThrow();
        return findings.elements().stream()
                .map(JsonValue.JsonObject.class::cast)
                .map(finding -> (JsonValue.JsonObject) finding.member("category").orElseThrow())
                .map(category -> (JsonValue.JsonNumber) category.member("code").orElseThrow())
                .map(code -> code.value().intValueExact())
                .toList();
    }

    private static long store(Path runFile) {
        try (InteractionStore store = SqliteInteractionStore.at(runFile)) {
            return store.count(InteractionQuery.all());
        }
    }

    private static boolean curlIsInstalled() {
        try {
            return new ProcessBuilder("curl", "--version")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(30, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException notThere) {
            return false;
        }
    }
}
