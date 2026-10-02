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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.auth.AuthGiven;
import io.restest.core.auth.CredentialPlan;
import io.restest.core.auth.CredentialedEngine;
import io.restest.core.event.RunEvent;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The key somebody hands over with a run is never among the values the run remembers.
 *
 * <p>The memory of what the API returned and accepted keeps the values of requests the API answered
 * with a success, to send them again. A key handed over for an API is a value of exactly that kind
 * - sent with requests the API accepts - and it must never be sent anywhere but where it was meant
 * to go, nor written anywhere a run leaves behind. This is the whole path: a request sent through
 * the door keys go in by, an API that answers with a success and even repeats the key back, and the
 * memory listening to what comes back.
 */
class AKeyIsNeverRememberedTest {

    private static final String KEY = "sk-live-4f9c2e7a1b";

    private static final OperationId REGISTER = OperationId.of("register");

    /** An operation that asks for the key's own name as a header and as a property of its body. */
    private static final ApiModel API = ApiModel.of("keys", "1", List.of(
            Operation.of(HttpMethod.POST, "/users", List.of(
                            Parameter.of("X-API-Key", ParameterLocation.HEADER, false,
                                    StringSchema.of())))
                    .withId(REGISTER)
                    .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                            "apiKey", StringSchema.of(), "email", StringSchema.of())), true))));

    @Test
    @DisplayName("a key handed over is in no memory, though the API accepted the request it went "
            + "with and repeated it back")
    void the_key_is_never_remembered() {
        CredentialedEngine door = new CredentialedEngine(new Echoing(),
                CredentialPlan.gather(List.of(AuthGiven.typed("header:X-API-Key=" + KEY, 1)), API,
                        false).plan());
        TestCase sent = TestCase.of(REGISTER, List.of(ParameterValue.of("X-API-Key",
                        ParameterLocation.HEADER, JsonValue.of("made up"),
                        new ValueOrigin.Generated("random"))),
                new BodyValue("application/json", JsonValue.object(Map.of(
                        "email", JsonValue.of("ana@example.com"))),
                        new ValueOrigin.Generated("random")));
        Interaction exchange = door.send(sent, new HttpRequestRecord(HttpMethod.POST,
                "https://api.example/users", List.of(Header.of("X-API-Key", "made up")),
                Optional.of(Payload.text("{\"email\":\"ana@example.com\"}", "application/json"))));
        ObservedValues seen = new ObservedValues(API);

        seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, exchange));

        for (String name : List.of("X-API-Key", "apiKey", "email")) {
            assertThat(seen.underTheirOwnNames().valuesFor(ValueRequest.of(REGISTER, name,
                    ParameterLocation.BODY, StringSchema.of())))
                    .describedAs(name)
                    .allSatisfy(value -> assertThat(JsonText.write(value)).doesNotContain(KEY));
        }
        assertThat(seen.underTheirOwnNames().valuesFor(ValueRequest.of(REGISTER, "email",
                ParameterLocation.BODY, StringSchema.of())))
                .describedAs("the request was learned from, so the test is not empty by accident")
                .contains(JsonValue.of("ana@example.com"));
    }

    /** An API that accepts everything and repeats back the key it was sent. */
    private static final class Echoing implements HttpEngine {

        @Override
        public CompletableFuture<Interaction> sendAsync(TestCase testCase,
                HttpRequestRecord request) {
            String key = request.headers().stream()
                    .filter(header -> header.name().equalsIgnoreCase("X-API-Key"))
                    .map(Header::value).findFirst().orElse("");
            return CompletableFuture.completedFuture(Interaction.answered(testCase, request,
                    new HttpResponseRecord(StatusLine.of(201),
                            List.of(Header.of("Content-Type", "application/json")),
                            Optional.of(Payload.text("{\"apiKey\":\"" + key + "\",\"X-API-Key\":\""
                                    + key + "\"}", "application/json"))),
                    Instant.EPOCH, Duration.ZERO));
        }

        @Override
        public EngineStatistics statistics() {
            return new EngineStatistics(0, Duration.ZERO, Duration.ZERO, Duration.ZERO, 0, 1);
        }

        @Override
        public void close() {
        }
    }
}
