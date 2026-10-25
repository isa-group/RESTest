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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.store.InteractionQuery;
import io.restest.core.store.InteractionStore;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the tests of the command line share about a run they start: how it is given a size rather
 * than a length of time, what a stand-in API answers with, and how what the run left behind is
 * read back.
 *
 * <p>Each of those tests runs the whole tool against a stand-in API of its own, and each used to
 * carry its own copy of these few lines. The stand-ins stay with the tests, because what each one
 * answers is what that test is about; what is here is the same for every one of them.
 *
 * <p>A run here is given a number of requests to send rather than a few seconds to send them in.
 * On a slow build machine those seconds were spent starting up, the run sent nothing and answered
 * {@code 3}; a run of a known size sends the same requests on a slow machine as on a fast one, and
 * ends as soon as they have been answered. It is still given a budget, as the ceiling a run that
 * went wrong could not get past.
 */
final class ARun {

    /** Turns on stopping after a set number of requests, given beside one of the sizes below. */
    static final String STOP_AFTER_REQUESTS = "schedule.stopAfterRequests=true";

    /** Enough for every operation of the small documents here several times over. */
    static final String SIXTY_REQUESTS = "schedule.requestsBeforeStopping=60";

    /** Enough for a series of requests about a thing the run created to be started several times. */
    static final String THREE_HUNDRED_REQUESTS = "schedule.requestsBeforeStopping=300";

    /** Enough for a value drawn by chance from a long list to come up. */
    static final String FIFTEEN_HUNDRED_REQUESTS = "schedule.requestsBeforeStopping=1500";

    private ARun() {
    }

    /**
     * A short run of this document against a stand-in, written to {@code out} in the directory, and
     * the code it ended with; whatever it printed goes to the screen.
     */
    static int run(String described, String baseUrl, Path directory, StringWriter screen,
            String... settings) throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), described);
        List<String> arguments = new ArrayList<>(List.of("run", document.toString(),
                "--url", baseUrl, "--budget", "30s",
                "--set", STOP_AFTER_REQUESTS, "--set", SIXTY_REQUESTS, "--seed", "1",
                "--out", directory.resolve("out").toString()));
        arguments.addAll(List.of(settings));
        PrintWriter out = new PrintWriter(screen);
        try {
            return Restest.run(arguments.toArray(String[]::new), out, out);
        } finally {
            out.flush();
        }
    }

    /** A reply of this status whose body is JSON. */
    static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    /** A reply of 200 whose body is JSON. */
    static ResponseDefinitionBuilder json(String body) {
        return json(200, body);
    }

    /** The report a run wrote into this directory, read back. */
    static JsonValue.JsonObject report(Path directory) {
        try {
            return (JsonValue.JsonObject) JsonText.read(
                    Files.readString(directory.resolve("report.json")));
        } catch (IOException cannotRead) {
            throw new UncheckedIOException("the report could not be read", cannotRead);
        }
    }

    /** The one word a report a run wrote into this directory gives for what the run amounts to. */
    static String verdictIn(Path directory) {
        return text(report(directory), "verdict");
    }

    /** How many exchanges a run kept in this file. */
    static long stored(Path runFile) {
        try (InteractionStore store = SqliteInteractionStore.at(runFile)) {
            return store.count(InteractionQuery.all());
        }
    }

    /** One of the totals a report gives, such as how many requests or faults there were. */
    static long totalIn(JsonValue.JsonObject report, String what) {
        JsonValue.JsonObject totals = (JsonValue.JsonObject) report.member("totals").orElseThrow();
        return ((JsonValue.JsonNumber) totals.member(what).orElseThrow()).value().longValueExact();
    }

    /** The catalogue number of every kind of fault a report counts. */
    static List<Integer> codesIn(JsonValue.JsonObject report) {
        JsonValue.JsonArray kinds =
                (JsonValue.JsonArray) report.member("faultsByCategory").orElseThrow();
        return kinds.elements().stream()
                .map(kind -> ((JsonValue.JsonObject) kind).member("code").orElseThrow())
                .map(code -> ((JsonValue.JsonNumber) code).value().intValueExact())
                .distinct()
                .toList();
    }

    /** The operations a report counts faults of this kind on. */
    static List<String> operationsWith(int code, JsonValue.JsonObject report) {
        JsonValue.JsonArray rows =
                (JsonValue.JsonArray) report.member("faultsByOperation").orElseThrow();
        return rows.elements().stream()
                .map(JsonValue.JsonObject.class::cast)
                .filter(row -> ((JsonValue.JsonNumber) row.member("code").orElseThrow()).value()
                        .intValueExact() == code)
                .map(row -> text(row, "operation"))
                .distinct()
                .toList();
    }

    /** How many requests a report counts as having ended this way, such as {@code noReply}. */
    static long repliesOfClass(JsonValue.JsonObject report, String statusClass) {
        JsonValue.JsonObject replies = (JsonValue.JsonObject) report.member("replies").orElseThrow();
        JsonValue.JsonObject byClass = (JsonValue.JsonObject) replies.member("byClass").orElseThrow();
        return byClass.member(statusClass)
                .map(count -> ((JsonValue.JsonNumber) count).value().longValueExact())
                .orElse(0L);
    }

    /** Every setting a report states, as "name value source", for reading in a test. */
    static List<String> settingsIn(JsonValue.JsonObject report) {
        JsonValue.JsonArray rows = (JsonValue.JsonArray) report.member("settings").orElseThrow();
        return rows.elements().stream()
                .map(JsonValue.JsonObject.class::cast)
                .map(row -> text(row, "key") + " " + text(row, "value") + " "
                        + text(row, "source"))
                .toList();
    }

    private static String text(JsonValue.JsonObject row, String member) {
        return ((JsonValue.JsonString) row.member(member).orElseThrow()).value();
    }
}
