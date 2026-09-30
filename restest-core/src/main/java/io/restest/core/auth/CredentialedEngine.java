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

import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.TestCase;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The door requests leave RESTest by when the person running it handed over a key: the key goes into
 * each request as it leaves, and comes out of everything that comes back.
 *
 * <p>This stands in front of the HTTP engine that really sends the requests, and does two things. On
 * the way out, it adds to each request the keys that go with its operation, where they go, just as
 * the engine adds the name the tool gives itself - so no test case ever holds a key, and nothing that
 * builds or changes a test case ever sees one. On the way back, before the exchange reaches anything
 * else - the reports, the stored run, the rules that judge the reply, the parts of the tool that
 * learn from what the API said - every appearance of a key in it is hidden, see {@link Secrets}. The
 * hiding is done as each exchange completes, on the engine's own thread, never on the one that
 * decides what to send next.
 *
 * <p>A run handed no key does not use this at all: the requests go straight to the engine, exactly
 * as they did before keys could be handed over.
 */
public final class CredentialedEngine implements HttpEngine {

    private final HttpEngine inner;
    private final CredentialPlan plan;
    private final Secrets secrets;

    /**
     * A door in front of the given engine.
     *
     * @param inner the engine that really sends the requests
     * @param plan which keys go with which operation, and where
     */
    public CredentialedEngine(HttpEngine inner, CredentialPlan plan) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.secrets = plan.secrets();
    }

    @Override
    public CompletableFuture<Interaction> sendAsync(TestCase testCase, HttpRequestRecord request) {
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(request, "request");
        HttpRequestRecord withTheKeys;
        try {
            withTheKeys = Placing.placed(plan.forOperation(testCase.operation()), testCase, request);
        } catch (RuntimeException couldNotBeAdded) {
            // Nothing here is expected to fail. If it does, the request is not sent without the key
            // somebody meant it to have: it is answered here, as one that could not be assembled.
            return CompletableFuture.completedFuture(secrets.hidden(Interaction.transportFailure(
                    testCase, request, "the key could not be added to this request, so it was "
                            + "not sent: " + couldNotBeAdded.getClass().getSimpleName(),
                    Instant.now(), Duration.ZERO)));
        }
        return inner.sendAsync(testCase, withTheKeys).thenApply(secrets::hidden);
    }

    @Override
    public Interaction send(TestCase testCase, HttpRequestRecord request) {
        return sendAsync(testCase, request).join();
    }

    @Override
    public EngineStatistics statistics() {
        return inner.statistics();
    }

    /**
     * How many exchanges had to be kept without their details because the keys could not be picked
     * out of them. It should always be nought, and a run says so if it is not.
     */
    public long hiddenWhole() {
        return secrets.hiddenWhole();
    }

    /** Closes the engine behind this door. Closing it twice is harmless, as it is for the engine. */
    @Override
    public void close() {
        inner.close();
    }
}
