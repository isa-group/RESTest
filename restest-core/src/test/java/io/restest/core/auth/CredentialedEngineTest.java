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
package io.restest.core.auth;

import static io.restest.core.auth.CredentialPlanTest.CORE;
import static io.restest.core.auth.CredentialPlanTest.KEY;
import static io.restest.core.auth.CredentialPlanTest.gather;
import static io.restest.core.auth.CredentialPlanTest.get;
import static io.restest.core.auth.CredentialPlanTest.model;
import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The door requests leave by when a key was handed over: what the API is sent, and what everything
 * after it is handed back.
 */
class CredentialedEngineTest {

    private final Recording api = new Recording();

    @Test
    @DisplayName("a key for a header replaces a header of its name whatever the capitals, and is hidden in what comes back")
    void a_header() {
        CredentialedEngine engine = new CredentialedEngine(api,
                gather(listPets(), AuthGiven.typed("header:X-API-Key=" + KEY, 1)).plan());

        Interaction interaction = engine.send(testCase("listPets"),
                new HttpRequestRecord(HttpMethod.GET, "http://api/pets",
                        List.of(Header.of("x-api-key", "made up"), Header.of("Accept", "*/*")),
                        Optional.empty()));

        assertThat(api.received().headers())
                .containsExactly(Header.of("Accept", "*/*"), Header.of("X-API-Key", KEY));
        assertThat(interaction.request().headers())
                .contains(Header.of("X-API-Key", "REDACTED-AUTH.header.X-API-Key"));
        assertThat(SecretsTest.writtenOut(interaction)).doesNotContain(CORE);
    }

    @Test
    @DisplayName("a key for the query is added as an address writes it, before any fragment, in place of one of its name")
    void the_query() {
        CredentialedEngine engine = new CredentialedEngine(api,
                gather(listPets(), AuthGiven.typed("query:api key=" + KEY, 1)).plan());

        engine.send(testCase("listPets"), HttpRequestRecord.of(HttpMethod.GET, "http://api/pets"));
        String alone = api.received().url();
        engine.send(testCase("listPets"),
                HttpRequestRecord.of(HttpMethod.GET, "http://api/pets?a=1&api+key=old&b=2#top"));
        String among = api.received().url();

        String encoded = "api%20key=" + Encoding.percent(KEY);
        assertThat(alone).isEqualTo("http://api/pets?" + encoded);
        assertThat(among).isEqualTo("http://api/pets?a=1&b=2&" + encoded + "#top");
    }

    @Test
    @DisplayName("a key for a cookie goes into the one Cookie header as it was given, in place of one of its name")
    void a_cookie() {
        CredentialedEngine engine = new CredentialedEngine(api,
                gather(listPets(), AuthGiven.typed("cookie:sid=" + KEY, 1)).plan());

        engine.send(testCase("listPets"), HttpRequestRecord.of(HttpMethod.GET, "http://api/pets"));
        List<Header> alone = api.received().headers();
        engine.send(testCase("listPets"), new HttpRequestRecord(HttpMethod.GET, "http://api/pets",
                List.of(Header.of("Cookie", "theme=dark; sid=old")), Optional.empty()));
        List<Header> among = api.received().headers();

        assertThat(alone).containsExactly(Header.of("Cookie", "sid=" + KEY));
        assertThat(among).containsExactly(Header.of("Cookie", "theme=dark; sid=" + KEY));
    }

    @Test
    @DisplayName("a field goes into a form built from its fields, and into nothing else")
    void a_field_of_a_form() {
        ApiModel languageTool = CredentialPlanTest.languageTool();
        CredentialedEngine engine = new CredentialedEngine(api,
                gather(languageTool, AuthGiven.typed("query:apiKey=" + KEY, 1)).plan());
        String field = "apiKey=" + Encoding.percent(KEY);

        engine.send(withABody("addWord", form(Optional.empty())),
                formRequest("word=a&apiKey=old"));
        assertThat(bodyReceived()).isEqualTo("word=a&" + field);

        engine.send(withABody("addWord", form(Optional.empty())), formRequest(""));
        assertThat(bodyReceived()).isEqualTo(field);

        engine.send(withABody("addWord", form(Optional.of("{\"broken\":"))),
                formRequest("{\"broken\":"));
        assertThat(bodyReceived())
                .describedAs("a body a change made on purpose is sent as that change made it")
                .isEqualTo("{\"broken\":");

        engine.send(withABody("addWord", new BodyValue("application/json",
                        JsonValue.object(Map.of()), ValueOrigin.DECLARED)),
                new HttpRequestRecord(HttpMethod.POST, "http://api/words/add",
                        List.of(), Optional.of(Payload.text("{}", "application/json"))));
        assertThat(bodyReceived()).isEqualTo("{}");

        engine.send(testCase("addWord"),
                HttpRequestRecord.of(HttpMethod.POST, "http://api/words/add"));
        assertThat(api.received().body()).isEmpty();
    }

    @Test
    @DisplayName("an operation no key goes with is sent as it was, and what comes back is still hidden")
    void an_operation_without_a_key() {
        ApiModel model = CredentialPlanTest.petstore();
        CredentialedEngine engine = new CredentialedEngine(api,
                gather(model, AuthGiven.typed(KEY, 1)).plan());
        HttpRequestRecord asked = HttpRequestRecord.of(HttpMethod.GET, "http://api/users");
        api.echo("{\"yourKey\":\"" + KEY + "\"}");

        Interaction interaction = engine.send(testCase("listUsers"), asked);

        assertThat(api.received()).isSameAs(asked);
        assertThat(new String(interaction.response().orElseThrow().body().orElseThrow().content(),
                StandardCharsets.UTF_8)).isEqualTo("{\"yourKey\":\"REDACTED-AUTH\"}");
    }

    @Test
    @DisplayName("sending waits for the same answer sending in the background gives, and the rest is the engine's")
    void the_rest_is_the_engines() throws Exception {
        CredentialedEngine engine = new CredentialedEngine(api,
                gather(listPets(), AuthGiven.typed("header:X-API-Key=" + KEY, 1)).plan());

        Interaction later = engine.sendAsync(testCase("listPets"),
                HttpRequestRecord.of(HttpMethod.GET, "http://api/pets")).get();

        assertThat(later.request().headers())
                .containsExactly(Header.of("X-API-Key", "REDACTED-AUTH.header.X-API-Key"));
        assertThat(engine.statistics()).isSameAs(api.statistics);
        assertThat(engine.hiddenWhole()).isZero();
        engine.close();
        assertThat(api.closed).isTrue();
    }

    private static ApiModel listPets() {
        return model(Map.of(), Optional.empty(), get("listPets", "/pets"));
    }

    private static TestCase testCase(String operation) {
        return TestCase.of(OperationId.of(operation), List.of());
    }

    private static TestCase withABody(String operation, BodyValue body) {
        return TestCase.of(OperationId.of(operation), List.of(), body);
    }

    private static BodyValue form(Optional<String> sentAs) {
        BodyValue fields = new BodyValue(ModelToFillIn.FORM,
                JsonValue.object(Map.of("word", JsonValue.of("a"))), ValueOrigin.DECLARED);
        return sentAs.map(text -> fields.withTextSent(ModelToFillIn.FORM, text)).orElse(fields);
    }

    private static HttpRequestRecord formRequest(String body) {
        return new HttpRequestRecord(HttpMethod.POST, "http://api/words/add", List.of(),
                Optional.of(Payload.text(body, ModelToFillIn.FORM)));
    }

    private String bodyReceived() {
        return new String(api.received().body().orElseThrow().content(), StandardCharsets.UTF_8);
    }

    /** A stand-in for the engine that answers everything with 200 and remembers what it was sent. */
    private static final class Recording implements HttpEngine {

        private final List<HttpRequestRecord> received = new ArrayList<>();
        private final EngineStatistics statistics = new EngineStatistics(0, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, 0, 1);
        private String echo = "";
        private boolean closed;

        void echo(String body) {
            this.echo = body;
        }

        HttpRequestRecord received() {
            return received.get(received.size() - 1);
        }

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            received.add(request);
            return CompletableFuture.completedFuture(Interaction.answered(testCase, request,
                    new HttpResponseRecord(StatusLine.of(200), List.of(),
                            echo.isEmpty() ? Optional.empty()
                                    : Optional.of(Payload.text(echo, "application/json"))),
                    Instant.now(), Duration.ZERO));
        }

        @Override
        public EngineStatistics statistics() {
            return statistics;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
