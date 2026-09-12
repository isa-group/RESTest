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
 * What happened when RESTest actually sent a {@link TestCase} to the API: a proper answer, a
 * broken one, or no answer at all.
 *
 * <p>These are three different situations, and treating any two of them as the same would hide real
 * information. A well-formed response with the wrong content is the API misbehaving in the ordinary
 * sense, and is what most checks need in order to judge anything at all - see {@link Answered}. A
 * response that is not even valid HTTP - broken chunked encoding, a cut-off status line, a body
 * shorter than the length it declared - is also the API's fault, but a different and more serious
 * one, deserving its own report rather than being confused with a connection that simply dropped -
 * see {@link MalformedResponse}. Getting no response at all - refused, timed out, a broken
 * connection - is a fact about the attempt itself, not about the API, and is the one case with
 * nothing to actually check - see {@link TransportFailure}.
 *
 * <p>These are the only three possibilities, which the compiler enforces: code meant to handle one
 * outcome must handle all three, rather than silently doing nothing useful if a fourth is ever
 * added.
 *
 * <p>Not represented here: a {@link TestCase} that was planned but never actually attempted, for
 * example because time ran out first. That is not an outcome of sending a request at all - nothing
 * was sent, so no {@link Interaction} exists for it either.
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
     * Bytes came back, but they did not form a well-formed HTTP response.
     *
     * <p>The status line and the headers are kept separately from {@code partial}, because in the
     * usual shape of this failure both parse cleanly and only the body is broken - for example, it
     * is shorter than its declared length, or a chunked transfer never sends its final chunk. Judging
     * that properly needs everything the response claimed about itself: the protocol version and
     * reason phrase as well as the reason text, since which failures are even possible depends on the
     * protocol in use - chunked encoding, for instance, only exists under HTTP/1.1. {@code statusLine}
     * is a single optional value rather than three separate ones for the same reason: a reason phrase
     * or protocol version parsing while the status code itself did not is not a shape HTTP can
     * actually produce, and {@link StatusLine} keeps that impossible shape from being built at all.
     *
     * <p>{@code partial}'s stored length, if set, means only "our own storage kept fewer bytes than
     * were actually delivered" - never "the response claimed more than it delivered". That second,
     * separate fact - a declared length the API failed to honour - is checked by comparing what was
     * actually delivered against the {@code Content-Length} already present in {@code headers}; it is
     * not this field's job to restate it. Bytes that simply stopped arriving, with no declared length
     * to compare against, are exactly what {@code partial} with no stored length represents:
     * everything we have, with no claim about whether more was coming.
     *
     * @param reason what was wrong, in a form fit to print in a report - "chunked encoding ended
     *     without a final zero-length chunk", not a stack trace
     * @param statusLine the status code and whatever else parsed, when the status line parsed at all
     * @param headers the headers, when they parsed, in wire order, repeats kept
     * @param partial whatever body bytes were received before the exchange broke, when any were.
     *     Declared under {@link Payload#UNKNOWN_MEDIA_TYPE} when nothing said what they were meant to
     *     be, rather than guessing at a {@code Content-Type} the response may never have sent
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
