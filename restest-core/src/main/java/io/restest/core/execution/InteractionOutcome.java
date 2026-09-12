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
package io.restest.core.execution;

import java.util.Objects;

/**
 * What happened when a {@link TestCase} was sent: a response came back, or none did.
 *
 * <p>The two are different facts for an oracle. A response the wrong shape is the API's fault; no
 * response at all - a timeout, a refused connection, a broken TLS handshake - is a fact about the
 * attempt itself, and most oracles (M1.6's server-error and schema-conformance oracles among them)
 * have nothing to judge without a response. Sealing the two apart means an oracle that only means to
 * handle {@link Answered} gets a compiler error if a third outcome is ever added, rather than
 * quietly doing nothing useful with it.
 */
public sealed interface InteractionOutcome {

    /**
     * A response came back.
     *
     * @param response the response received
     */
    record Answered(HttpResponseRecord response) implements InteractionOutcome {
        public Answered {
            Objects.requireNonNull(response, "response");
        }
    }

    /**
     * No response came back.
     *
     * @param reason what went wrong, in a form fit to print in a report - "connection refused",
     *     "read timed out after 30s" - not a stack trace
     */
    record TransportFailure(String reason) implements InteractionOutcome {
        public TransportFailure {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException(
                        "a transport failure must say what went wrong");
            }
        }
    }
}
