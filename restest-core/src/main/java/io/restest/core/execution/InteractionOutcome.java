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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What happened when a {@link TestCase} was sent.
 *
 * <p>Three outcomes, not two, because they are different facts for an oracle and conflating any
 * pair of them hides one. A well-formed response the wrong shape is the API's fault, and most
 * oracles (M1.6's schema-conformance oracle among them) need {@link Answered} to have anything to
 * judge. A response that is not well-formed HTTP at all - broken chunked encoding, a truncated
 * status line, a body shorter than its own declared {@code Content-Length} - is also the API's
 * fault, and a different one: the HTTP-semantics oracles WFC reserves codes 900-909 for (M3.2) exist
 * specifically to report it, and they cannot fire on an outcome that looks identical to a dropped
 * connection. No response at all - refused, timed out, a broken TLS handshake - is a fact about the
 * attempt, not about the API, and is the one case with nothing to show an oracle.
 *
 * <p>Sealed over exactly these three, so an oracle or a report that means to handle one case gets a
 * compiler error if a fourth is ever added, rather than quietly doing nothing useful with it.
 *
 * <p>Not represented here: a {@link TestCase} the engine planned but never attempted at all - shed by
 * adaptive concurrency, cut off by the budget running out. That is not an outcome of sending a
 * request; nothing was sent, so no {@link Interaction} exists for it. A run's accounting of "planned
 * versus attempted" is the engine's and the report's to keep (M1.3, M3.6), not a fourth case here.
 */
public sealed interface InteractionOutcome {

    /**
     * A well-formed response came back.
     *
     * @param response the response received
     */
    record Answered(HttpResponseRecord response) implements InteractionOutcome {
        public Answered {
            Objects.requireNonNull(response, "response");
        }
    }

    /**
     * Bytes came back, but they were not a well-formed HTTP response.
     *
     * <p>The status line and the headers are kept separately from {@code partial}, not folded into
     * it, because the common shape of this outcome parses both cleanly and only the body breaks - a
     * declared {@code Content-Length} the actual bytes fall short of, a chunked stream that never
     * sends its final chunk. An oracle judging that (M3.2, WFC 900-909) needs what the response
     * claimed about itself, not only the reason text - the protocol version and reason phrase
     * included, since whether a given framing failure is even possible (chunked encoding exists only
     * under HTTP/1.1) depends on which protocol was in use. {@code statusLine} is one optional
     * component, not three: a reason phrase or a protocol version parsing while the status code did
     * not is not a shape HTTP itself can produce, and {@link StatusLine} makes that shape
     * unconstructable instead of merely undocumented.
     *
     * <p>{@code partial}'s {@link Payload#wireLength()}, if set, means only "our own storage kept
     * fewer bytes than were actually delivered" - never "the response claimed more than it
     * delivered". That second fact, a declared length the API failed to honour, is already visible
     * by comparing {@code partial}'s size against a {@code Content-Length} in {@code headers}; it is
     * not this field's job to restate it. Bytes that simply stopped arriving, with no declared
     * length to compare against - a chunked stream with no final chunk - are exactly what
     * {@code partial} with no {@code wireLength} represents: everything we have, with nothing said
     * about whether more was coming.
     *
     * @param reason what was wrong, in a form fit to print in a report - "chunked encoding ended
     *     without a final zero-length chunk", not a stack trace
     * @param statusLine the status code and whatever else parsed, when the status line parsed at all
     * @param headers the headers, when they parsed, in wire order, repeats kept
     * @param partial whatever body bytes were received before the exchange broke, when any were.
     *     Declared under {@link Payload#UNKNOWN_MEDIA_TYPE} when nothing said what they were meant
     *     to be, rather than repeating a {@code Content-Type} the response may never have sent
     */
    record MalformedResponse(String reason, Optional<StatusLine> statusLine, List<Header> headers,
            Optional<Payload> partial) implements InteractionOutcome {
        public MalformedResponse {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(statusLine, "statusLine");
            Objects.requireNonNull(headers, "headers");
            Objects.requireNonNull(partial, "partial");
            if (reason.isBlank()) {
                throw new IllegalArgumentException(
                        "a malformed response must say what was wrong with it");
            }
            headers = List.copyOf(headers);
        }
    }

    /**
     * No response came back at all.
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
