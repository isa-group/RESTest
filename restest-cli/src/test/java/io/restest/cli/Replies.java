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

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.Operation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Replies made up by hand, to put a rule to a real API's document without calling the API. */
final class Replies {

    private Replies() {
    }

    /** A 200 reply of the given body, said to be JSON, for the given operation. */
    static Interaction of(Operation operation, String body) {
        return Interaction.answered(
                TestCase.of(operation.id(), List.of()),
                HttpRequestRecord.of(operation.method(), "https://api.example" + operation.path()),
                new HttpResponseRecord(StatusLine.of(200),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.of(body.getBytes(StandardCharsets.UTF_8),
                                "application/json"))),
                Instant.EPOCH, Duration.ofMillis(9));
    }

}
