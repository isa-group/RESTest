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
package io.restest.exec;

import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Payload;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.util.List;
import java.util.Optional;

/**
 * Small helpers shared by the engine's tests: a test case to hang an interaction on, and requests
 * built by hand, since the part of RESTest that invents them does not exist yet.
 */
final class Requests {

    private Requests() {
    }

    static TestCase testCase(HttpMethod method, String path) {
        return TestCase.of(OperationId.synthesised(method, path), List.of());
    }

    static HttpRequestRecord get(String url) {
        return HttpRequestRecord.of(HttpMethod.GET, url);
    }

    static HttpRequestRecord json(HttpMethod method, String url, String body) {
        return new HttpRequestRecord(method, url, List.of(),
                Optional.of(Payload.text(body, "application/json")));
    }
}
