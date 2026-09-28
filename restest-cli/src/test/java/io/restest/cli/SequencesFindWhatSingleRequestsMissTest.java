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
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An API whose faults only several requests together reach, and a run that finds them by sending
 * series about the things it creates - and misses them with the series switched off.
 *
 * <p>The stand-in keeps what it has made, which a stub on its own cannot: it hands out a fresh
 * identifier for every widget, and fails on five things only - reading a widget it has deleted,
 * deleting one a second time, making a second widget with a name already taken, and listing or
 * adding the parts of a widget it has deleted. A request on its own cannot reach any of them here:
 * the plan draws on nothing the API returned, so an ordinary request asks for a widget nobody made
 * and is told there is none, and an invented name of twelve characters is not invented twice in a
 * run - at three, it was, a few times in the tens of thousands of creations a few seconds against a
 * stand-in send.
 */
class SequencesFindWhatSingleRequestsMissTest {

    private static final String SPECIFICATION = """
            openapi: 3.0.3
            info: {title: Forgetful widgets, version: "1.0"}
            paths:
              /widgets:
                post:
                  operationId: addWidget
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required: [name]
                          properties:
                            name: {type: string, minLength: 12, maxLength: 12}
                  responses:
                    "201":
                      description: the widget made
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id: {type: string, format: uuid}
                              name: {type: string}
              /widgets/{widgetId}:
                get:
                  operationId: getWidget
                  parameters:
                    - {name: widgetId, in: path, required: true, schema: {type: string, format: uuid}}
                  responses:
                    "200": {description: the widget}
                    "404": {description: no such widget}
                delete:
                  operationId: deleteWidget
                  parameters:
                    - {name: widgetId, in: path, required: true, schema: {type: string, format: uuid}}
                  responses:
                    "204": {description: deleted}
                    "404": {description: no such widget}
              /widgets/{widgetId}/parts:
                get:
                  operationId: listParts
                  parameters:
                    - {name: widgetId, in: path, required: true, schema: {type: string, format: uuid}}
                  responses:
                    "200": {description: the widget's parts}
                    "404": {description: no such widget}
                post:
                  operationId: addPart
                  parameters:
                    - {name: widgetId, in: path, required: true, schema: {type: string, format: uuid}}
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required: [name]
                          properties:
                            name: {type: string}
                  responses:
                    "201": {description: the part added}
                    "404": {description: no such widget}
            """;

    /** Values from nothing the API returned, so that no ordinary request can name a real widget. */
    private static final String PLAN = """
            version: 1
            strategies:
              - name: nominal
                share: 50
                sources:
                  - source: enum
                  - source: random
              - name: sequences
                share: 50
                sends: sequences
                sources:
                  - source: enum
                  - source: random
            """;

    private static final Pattern WIDGET = Pattern.compile("^/widgets/([^/]+)$");
    private static final Pattern PARTS = Pattern.compile("^/widgets/([^/]+)/parts$");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");

    private static final Widgets WIDGETS = new Widgets();
    private static WireMockServer api;

    @BeforeAll
    static void start() {
        api = new WireMockServer(options().dynamicPort().extensions(WIDGETS));
        api.start();
        // One stub for everything; what it answers is decided by the widgets it has made.
        api.stubFor(any(anyUrl()).willReturn(ok()));
    }

    @AfterAll
    static void stop() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    @DisplayName("reading a deleted widget, deleting it twice, making it twice and reaching its "
            + "parts once it is deleted are found by series, and missed with them switched off")
    void series_find_what_single_requests_miss(@TempDir Path directory) throws IOException {
        Path document = Files.writeString(directory.resolve("openapi.yaml"), SPECIFICATION);
        Path plan = Files.writeString(directory.resolve("plan.yaml"), PLAN);

        String withSeries = run(directory.resolve("with"), document, plan);
        String without = run(directory.resolve("without"), document, plan,
                "--set", "sequences.readAfterDelete=false",
                "--set", "sequences.deleteTwice=false",
                "--set", "sequences.writeUnderDeleted=false",
                "--set", "sequences.putTwice=false",
                "--set", "sequences.safeGet=false",
                "--set", "sequences.createTwice=false");

        assertThat(withSeries)
                .describedAs("a widget read after its deletion, deleted twice or made twice, and "
                        + "the parts of one deleted, listed or added to, are what makes this API "
                        + "fail")
                .contains("5 operation(s) answered 500")
                .contains("series about a thing the run created");
        assertThat(without)
                .describedAs("on its own a request only ever meets widgets nobody made, and "
                        + "names nobody used")
                .doesNotContain("answered 500")
                .doesNotContain("series about a thing");
    }

    private static String run(Path out, Path document, Path plan, String... extra) {
        // Each run against an API that has made nothing yet: the same seed invents the same names.
        WIDGETS.forgetEverything();
        List<String> arguments = new ArrayList<>(List.of(
                "run", document.toString(),
                "--url", api.baseUrl(),
                "--budget", "3s",
                "--seed", "20261005",
                "--campaign", plan.toString(),
                "--out", out.toString()));
        arguments.addAll(List.of(extra));
        StringWriter screen = new StringWriter();
        try (PrintWriter writer = new PrintWriter(screen, true)) {
            Restest.run(arguments.toArray(new String[0]), writer, writer);
        }
        return screen.toString();
    }

    /**
     * The widgets, faults and all. Each is made with a fresh identifier and kept, alive or deleted,
     * until the next run begins.
     */
    private static final class Widgets implements ResponseDefinitionTransformerV2 {

        private final Map<String, Boolean> alive = new ConcurrentHashMap<>();
        private final Set<String> names = ConcurrentHashMap.newKeySet();

        void forgetEverything() {
            alive.clear();
            names.clear();
        }

        @Override
        public ResponseDefinition transform(ServeEvent event) {
            String path = URI.create(event.getRequest().getUrl()).getPath();
            String method = event.getRequest().getMethod().getName();
            if (path.equals("/widgets") && method.equals("POST")) {
                Matcher named = NAME.matcher(event.getRequest().getBodyAsString());
                if (!named.find()) {
                    return reply(400, "{\"message\":\"a widget has a name\"}");
                }
                if (!names.add(named.group(1))) {
                    return reply(500, "{\"message\":\"duplicate key value violates unique "
                            + "constraint widgets_name\"}");
                }
                String id = UUID.randomUUID().toString();
                alive.put(id, true);
                return reply(201, "{\"id\":\"" + id + "\",\"name\":\"" + named.group(1)
                        + "\"}");
            }
            Matcher parts = PARTS.matcher(path);
            if (parts.matches()) {
                Boolean state = alive.get(parts.group(1));
                if (state == null) {
                    return reply(404, "{\"message\":\"no such widget\"}");
                }
                if (!state) {
                    return reply(500, "{\"message\":\"insert or select on parts violates "
                            + "foreign key constraint parts_widget\"}");
                }
                return method.equals("POST")
                        ? reply(201, "{\"id\":\"" + UUID.randomUUID() + "\"}")
                        : reply(200, "[]");
            }
            Matcher one = WIDGET.matcher(path);
            if (!one.matches()) {
                return reply(404, "{\"message\":\"no such address\"}");
            }
            String id = one.group(1);
            Boolean state = alive.get(id);
            return switch (method) {
                case "GET" -> state == null
                        ? reply(404, "{\"message\":\"no such widget\"}")
                        : state
                                ? reply(200, "{\"id\":\"" + id + "\"}")
                                : reply(500, "{\"message\":\"widget " + id
                                        + " is deleted but still in the index\"}");
                case "DELETE" -> {
                    if (state == null) {
                        yield reply(404, "{\"message\":\"no such widget\"}");
                    }
                    if (!state) {
                        yield reply(500, "{\"message\":\"NullPointerException deleting "
                                + id + "\"}");
                    }
                    alive.put(id, false);
                    yield reply(204, "");
                }
                default -> reply(405, "{\"message\":\"not allowed\"}");
            };
        }

        @Override
        public String getName() {
            return "widgets";
        }

        private static ResponseDefinition reply(int status, String body) {
            ResponseDefinitionBuilder answer = ResponseDefinitionBuilder.responseDefinition()
                    .withStatus(status);
            if (!body.isEmpty()) {
                answer = answer.withHeader("Content-Type", "application/json").withBody(body);
            }
            return answer.build();
        }
    }
}
