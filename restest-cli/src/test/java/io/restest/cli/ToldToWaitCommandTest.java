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
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.restest.core.json.JsonValue;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whole runs against an API that asks to be left alone: what reaches the API while it waits, read
 * from the stand-in's own record of every request it received, and what the run says about it.
 *
 * <p>The moments compared are the stand-in's, to the millisecond, with margin on both sides of
 * every quiet stretch looked at: a request already on its way when the API asked for quiet may still
 * arrive, and a slow machine runs late, never early.
 */
class ToldToWaitCommandTest {

    private static final String SHELTER = """
            openapi: 3.0.3
            info: {title: Shelter that limits its callers, version: '1'}
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
              /shelters:
                get: {operationId: listShelters, responses: {'200': {description: every shelter}}}
              /owners:
                get: {operationId: listOwners, responses: {'200': {description: every owner}}}
            """;

    /** Long enough for a wait of a second and what comes after it. */
    private static final String BUDGET = "2500ms";

    /** The shipped plan without the memory of what the API returned, as the switches page has it. */
    private static final String WITHOUT_THE_MEMORY = """
            version: 2
            strategies:
              - name: nominal
                share: 45
                kind: nominal
                sources:
                  - source: enum
                  - weighted: {example: 19, random: 25, dictionaries: 50, default: 6}
              - name: sequences
                share: 15
                kind: sequences
                sources:
                  - source: enum
                  - weighted: {example: 19, random: 25, dictionaries: 50, default: 6}
              - name: mutation
                share: 30
                kind: mutation
                sources:
                  - source: enum
                  - weighted: {example: 19, random: 25, dictionaries: 50, default: 6}
              - name: fuzzing
                share: 10
                kind: fuzzing
                sources:
                  - source: random
            """;

    /** Every other thing that makes what a run sends depend on the API's replies, off. */
    private static final List<String> EVERYTHING_ELSE_OFF = List.of(
            "--set", "mutation.violations=false", "--set", "sequences.readAfterDelete=false",
            "--set", "sequences.deleteTwice=false", "--set", "sequences.writeUnderDeleted=false",
            "--set", "sequences.putTwice=false", "--set", "sequences.safeGet=false",
            "--set", "sequences.createTwice=false");

    private static final String THE_SEED_LINE = "what it sends depends on the API's own replies";

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
    void answerEverything() {
        api.resetAll();
        api.stubFor(any(anyUrl()).atPriority(9).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("[]")));
    }

    @Test
    @DisplayName("an operation answered 429 with Retry-After is sent nothing for that long, while "
            + "the others go on, and the run says so and ends as it would have")
    void an_operation_told_to_wait_is_left_alone() throws IOException {
        askForQuiet("/pets");

        int answer = run();

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        Instant told = firstRefusal();
        assertThat(arrivedBetween("/pets", told.plusMillis(300), told.plusMillis(700)))
                .describedAs("the second it asked for, apart from what was on its way")
                .isZero();
        assertThat(arrivedBetween("/shelters", told.plusMillis(300), told.plusMillis(700)))
                .describedAs("while the others went on")
                .isPositive();
        JsonValue.JsonObject block = toldToWait();
        assertThat(number(object(block, "wholeApi"), "times")).isZero();
        JsonValue.JsonObject pets = (JsonValue.JsonObject) ((JsonValue.JsonArray) block
                .member("operations").orElseThrow()).elements().getFirst();
        assertThat(((JsonValue.JsonString) pets.member("operation").orElseThrow()).value())
                .isEqualTo("listPets");
        assertThat(number(pets, "times")).isPositive();
        assertThat(number(pets, "stated")).isEqualTo(number(pets, "times"));
        assertThat(number(pets, "turnsSkipped")).isPositive();
        assertThat(screen.toString()).contains("  told to wait: listPets ");
    }

    @Test
    @DisplayName("when a second operation is told to wait while another waits, nothing at all "
            + "reaches the API until the wait is over")
    void the_whole_api_waits() throws IOException {
        askForQuiet("/pets", "/shelters");

        int answer = run();

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        Instant told = firstRefusal();
        assertThat(arrivedBetween(null, told.plusMillis(300), told.plusMillis(700)))
                .describedAs("not even an operation that was never told to wait")
                .isZero();
        assertThat(number(object(toldToWait(), "wholeApi"), "times")).isPositive();
        assertThat(screen.toString()).contains("  told to wait: the whole API ");
    }

    @Test
    @DisplayName("when the whole API asks for quiet to the end of the run, what the engine held "
            + "never reaches it, not even once the run's time is up")
    void what_is_held_at_the_end_is_never_sent() throws IOException {
        askForQuietOf("3600", "/pets", "/shelters");

        int answer = run();

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        Instant told = firstRefusal();
        assertThat(arrivedBetween(null, told.plusMillis(300), Instant.MAX))
                .describedAs("an hour of quiet asked for, the rest of the run and after it")
                .isZero();
        assertThat(number(object(toldToWait(), "wholeApi"), "times")).isEqualTo(1);
    }

    @Test
    @DisplayName("switched off, a 429 is an answer like any other, and the run says nothing of it")
    void switched_off_nothing_waits() throws IOException {
        askForQuiet("/pets");

        int answer = run("--set", "schedule.waitWhenToldTo=false");

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        Instant told = firstRefusal();
        assertThat(arrivedBetween("/pets", told.plusMillis(300), told.plusMillis(700)))
                .isPositive();
        assertThat(ARun.report(out()).member("toldToWait")).contains(JsonValue.NULL);
        assertThat(screen.toString()).doesNotContain("told to wait");
    }

    @Test
    @DisplayName("a run that waits when told to says its seed alone does not repeat it, and one "
            + "with that off as well as everything else that depends on the API's replies does not")
    void the_seed_line_speaks_while_waiting_is_on() throws IOException {
        Path plan = Files.writeString(directory.resolve("plan.yaml"), WITHOUT_THE_MEMORY);
        List<String> rest = new ArrayList<>(List.of("--campaign", plan.toString()));
        rest.addAll(EVERYTHING_ELSE_OFF);

        runFor("1ms", rest.toArray(String[]::new));
        assertThat(screen.toString()).describedAs("%s", problems).contains(THE_SEED_LINE);

        screen.getBuffer().setLength(0);
        rest.addAll(List.of("--set", "schedule.waitWhenToldTo=false"));
        runFor("1ms", rest.toArray(String[]::new));
        assertThat(screen.toString()).describedAs("%s", problems).doesNotContain(THE_SEED_LINE)
                .contains(" operations can be tested, seed 1");
    }

    /** These paths answer 429, asking for a second's quiet. */
    private static void askForQuiet(String... paths) {
        askForQuietOf("1", paths);
    }

    /** These paths answer 429, asking for quiet for so many seconds. */
    private static void askForQuietOf(String seconds, String... paths) {
        for (String path : paths) {
            api.stubFor(get(urlPathEqualTo(path)).atPriority(1).willReturn(aResponse()
                    .withStatus(429).withHeader("Retry-After", seconds)));
        }
    }

    private int run(String... more) throws IOException {
        return runFor(BUDGET, more);
    }

    private int runFor(String budget, String... more) throws IOException {
        Path file = Files.writeString(directory.resolve("openapi.yaml"), SHELTER);
        List<String> arguments = new ArrayList<>(List.of("run", file.toString(), "--url",
                api.baseUrl(), "--budget", budget, "--seed", "1", "--out", out().toString()));
        arguments.addAll(List.of(more));
        PrintWriter outWriter = new PrintWriter(screen);
        PrintWriter errWriter = new PrintWriter(problems);
        try {
            return Restest.run(arguments.toArray(String[]::new), outWriter, errWriter, Map.of());
        } finally {
            outWriter.flush();
            errWriter.flush();
        }
    }

    private Path out() {
        return directory.resolve("out");
    }

    private JsonValue.JsonObject toldToWait() {
        return (JsonValue.JsonObject) ARun.report(out()).member("toldToWait").orElseThrow();
    }

    /** When the first request the stand-in answered 429 reached it. */
    private static Instant firstRefusal() {
        return api.getAllServeEvents().stream()
                .filter(event -> event.getResponse().getStatus() == 429)
                .map(ToldToWaitCommandTest::arrived)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    /** How many requests to this path - or to any, given none - reached the stand-in then. */
    private static long arrivedBetween(String path, Instant from, Instant to) {
        return api.getAllServeEvents().stream()
                .filter(event -> path == null || event.getRequest().getUrl().startsWith(path))
                .map(ToldToWaitCommandTest::arrived)
                .filter(at -> !at.isBefore(from) && at.isBefore(to))
                .count();
    }

    private static Instant arrived(ServeEvent event) {
        return event.getRequest().getLoggedDate().toInstant();
    }

    private static JsonValue.JsonObject object(JsonValue.JsonObject object, String member) {
        return (JsonValue.JsonObject) object.member(member).orElseThrow();
    }

    private static long number(JsonValue.JsonObject object, String member) {
        return ((JsonValue.JsonNumber) object.member(member).orElseThrow()).value()
                .longValueExact();
    }
}
