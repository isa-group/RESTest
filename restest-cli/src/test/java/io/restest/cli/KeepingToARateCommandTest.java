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

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.execution.Interaction;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
 * Whole runs given a rate to keep to: what reaches the API, read from the stand-in's own record of
 * every request it received, to the millisecond, and what the run says about the quiet the rate made.
 */
class KeepingToARateCommandTest {

    private static final String SHELTER = """
            openapi: 3.0.3
            info: {title: Shelter whose owner was promised a rate, version: '1'}
            paths:
              /pets:
                get: {operationId: listPets, responses: {'200': {description: every pet}}}
              /shelters:
                get: {operationId: listShelters, responses: {'200': {description: every shelter}}}
              /owners:
                get: {operationId: listOwners, responses: {'200': {description: every owner}}}
            """;

    private static final Duration BUDGET = Duration.ofSeconds(2);

    /**
     * Ends a run with no rate after this many requests rather than at its budget: enough to show
     * it went far faster than a rate would let it, without loading a small machine for the whole
     * budget while other tests share it.
     */
    private static final List<String> A_FEW_HUNDRED = List.of(
            "--set", "schedule.stopAfterRequests=true",
            "--set", "schedule.requestsBeforeStopping=200");

    /** Ten requests a second: one every tenth of a second. */
    private static final Duration BETWEEN = Duration.ofMillis(100);

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
        api.stubFor(any(anyUrl()).willReturn(ARun.json("[]")));
    }

    @Test
    @DisplayName("a rate named in one line is kept to: never two requests closer than a turn, none "
            + "after the run's time, and the quiet it made is said apart from the tool's own")
    void a_rate_named_in_one_line_is_kept_to() throws IOException {
        int answer = run("--set", "engine.mostRequestsPerMinute=600", "--store");

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        assertThat(arrivals()).hasSizeBetween(3, (int) BUDGET.dividedBy(BETWEEN) + 2);
        // Where RESTest sent them, which is where the rate is kept: at the API the first arrives
        // late, behind the connection it opens, and the next, sent a turn later, does not.
        List<Instant> sent = sentAt();
        for (int i = 1; i < sent.size(); i++) {
            assertThat(Duration.between(sent.get(i - 1), sent.get(i)))
                    .describedAs("a tenth of a second apart, less what a busy machine takes "
                            + "between giving a request its turn and noting the moment it went")
                    .isGreaterThan(BETWEEN.dividedBy(2));
        }
        JsonValue.JsonObject timeline =
                (JsonValue.JsonObject) ARun.report(out()).member("timeline").orElseThrow();
        Instant timeIsUp = Instant.parse(text(timeline, "startedAt")).plus(BUDGET);
        // A request whose turn came just before the time was up may still leave a little after
        // it, as late as a busy machine is in building it and sending it: a Windows runner once took
        // 55 ms. One still waiting for its turn is dropped; kept, it would go a turn or more late.
        assertThat(sent.getLast())
                .describedAs("nothing that waited for a turn went once the time was up")
                .isBefore(timeIsUp.plus(BETWEEN));
        assertThat(Duration.parse(text(engine(), "idleKeepingToTheRate"))).isPositive();
        assertThat(screen.toString()).contains("keeping to the rate it was given)");
    }

    @Test
    @DisplayName("with no rate named, the run goes as fast as the API answers and says nothing of a "
            + "rate")
    void no_rate_no_limit() throws IOException {
        int answer = run(A_FEW_HUNDRED.toArray(String[]::new));

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        assertFasterThanTheRate(arrivals());
        assertThat(text(engine(), "idleKeepingToTheRate")).isEqualTo("PT0S");
        assertThat(screen.toString()).doesNotContain("keeping to the rate")
                .doesNotContain("engine.mostRequestsPerMinute");
    }

    @Test
    @DisplayName("a rate given over a printed file that says not to keep to one is not kept to, "
            + "even the default rate, and the run says so before it sends anything")
    void a_rate_over_a_file_with_the_switch_off_is_said() throws IOException {
        Path printed = Files.writeString(directory.resolve("settings.yaml"),
                "engine:\n  limitTheRate: false\n  mostRequestsPerMinute: 600\n");

        List<String> arguments = new ArrayList<>(List.of("--settings", printed.toString(),
                "--set", "engine.mostRequestsPerMinute=600"));
        arguments.addAll(A_FEW_HUNDRED);
        int answer = run(arguments.toArray(String[]::new));

        assertThat(answer).describedAs("%s%s", screen, problems).isZero();
        assertThat(screen.toString()).contains("  engine.mostRequestsPerMinute is 600, but "
                + "engine.limitTheRate is false, so no rate is kept to");
        assertFasterThanTheRate(arrivals());
    }

    /** Two hundred requests in less time than ten a second would have taken for twenty. */
    private static void assertFasterThanTheRate(List<Instant> arrivals) {
        assertThat(arrivals).hasSize(200);
        assertThat(Duration.between(arrivals.getFirst(), arrivals.getLast()))
                .describedAs("at ten a second, two hundred requests take twenty seconds")
                .isLessThan(BUDGET);
    }

    private int run(String... more) throws IOException {
        Path file = Files.writeString(directory.resolve("openapi.yaml"), SHELTER);
        List<String> arguments = new ArrayList<>(List.of("run", file.toString(), "--url",
                api.baseUrl(), "--budget", BUDGET.toMillis() + "ms", "--seed", "1", "--out",
                out().toString()));
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

    private JsonValue.JsonObject engine() {
        return (JsonValue.JsonObject) ARun.report(out()).member("engine").orElseThrow();
    }

    /** When RESTest sent each request it stored, in order, leaving out those that never went. */
    private List<Instant> sentAt() {
        try (InteractionStore store = SqliteInteractionStore.at(out().resolve("run.sqlite"))) {
            return store.find(InteractionQuery.all()).stream()
                    .filter(interaction -> interaction.statusCode().isPresent())
                    .map(Interaction::sentAt)
                    .sorted()
                    .toList();
        }
    }

    /** When each request reached the stand-in, in order. */
    private static List<Instant> arrivals() {
        return api.getAllServeEvents().stream()
                .map(event -> event.getRequest().getLoggedDate().toInstant())
                .sorted()
                .toList();
    }

    private static String text(JsonValue.JsonObject object, String member) {
        return ((JsonValue.JsonString) object.member(member).orElseThrow()).value();
    }
}
