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
package io.restest.core.store;

import io.restest.core.model.OperationId;
import java.util.Objects;
import java.util.Optional;

/**
 * A question to ask a store of past interactions: which of them do you want back?
 *
 * <p>Every part is optional, and the ones given are combined: a query naming an operation and the
 * status code 500 asks for the times that one operation answered with a server error. A query naming
 * nothing asks for everything.
 *
 * <p>The factory methods are named after the questions people actually ask - "what did this
 * operation do", "what came back as a server error", "what never answered at all" - and each returns
 * a new query rather than changing this one, so a query can be kept and reused without anyone else's
 * additions leaking into it.
 *
 * @param operation only interactions belonging to this operation
 * @param statusCode only replies with exactly this status code
 * @param statusClass only replies whose status code starts with this digit: 5 for the server errors,
 *     4 for the requests the API refused, 2 for the ones it accepted
 * @param outcome only interactions that ended this way
 * @param limit at most this many, for a caller that wants a sample rather than a corpus. The store
 *     returns them oldest first, so a limit takes the beginning of the run rather than an arbitrary
 *     handful
 */
public record InteractionQuery(
        Optional<OperationId> operation,
        Optional<Integer> statusCode,
        Optional<Integer> statusClass,
        Optional<Outcome> outcome,
        Optional<Integer> limit) {

    /** The three ways an attempt can end, as something a query can name. */
    public enum Outcome {

        /** The API answered, whatever it answered. */
        ANSWERED,

        /** Something came back, but not a whole reply. */
        MALFORMED,

        /** Nothing came back at all: refused, timed out, unreachable. */
        FAILED
    }

    private static final InteractionQuery ALL = new InteractionQuery(Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public InteractionQuery {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(statusCode, "statusCode");
        Objects.requireNonNull(statusClass, "statusClass");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(limit, "limit");
        statusCode.ifPresent(code -> {
            if (code < 100 || code > 599) {
                throw new IllegalArgumentException(
                        "an HTTP status code is between 100 and 599: " + code);
            }
        });
        statusClass.ifPresent(digit -> {
            if (digit < 1 || digit > 5) {
                throw new IllegalArgumentException("a class of status codes is named by its first "
                        + "digit, 1 to 5, not by " + digit);
            }
        });
        limit.ifPresent(count -> {
            if (count < 1) {
                throw new IllegalArgumentException(
                        "asking for at most " + count + " interactions is asking for none");
            }
        });
    }

    /** Everything the store holds. */
    public static InteractionQuery all() {
        return ALL;
    }

    /** Everything one operation did. */
    public static InteractionQuery forOperation(OperationId operation) {
        return ALL.andOperation(operation);
    }

    /** Every reply with exactly this status code. */
    public static InteractionQuery withStatus(int statusCode) {
        return ALL.andStatus(statusCode);
    }

    /** Every reply the API answered with a server error, which is the first thing anyone asks. */
    public static InteractionQuery serverErrors() {
        return ALL.andStatusClass(5);
    }

    /** Every attempt the API never answered: refused, timed out, or cut short. */
    public static InteractionQuery neverAnswered() {
        return ALL.andOutcome(Outcome.FAILED);
    }

    public InteractionQuery andOperation(OperationId value) {
        return new InteractionQuery(Optional.of(Objects.requireNonNull(value, "value")), statusCode,
                statusClass, outcome, limit);
    }

    public InteractionQuery andStatus(int value) {
        return new InteractionQuery(operation, Optional.of(value), statusClass, outcome, limit);
    }

    public InteractionQuery andStatusClass(int firstDigit) {
        return new InteractionQuery(operation, statusCode, Optional.of(firstDigit), outcome, limit);
    }

    public InteractionQuery andOutcome(Outcome value) {
        return new InteractionQuery(operation, statusCode, statusClass,
                Optional.of(Objects.requireNonNull(value, "value")), limit);
    }

    /** The same question, answered with at most this many interactions. */
    public InteractionQuery limitedTo(int count) {
        return new InteractionQuery(operation, statusCode, statusClass, outcome,
                Optional.of(count));
    }

    /** Whether this query asks for everything, which a store may be able to answer more cheaply. */
    public boolean isUnfiltered() {
        return operation.isEmpty() && statusCode.isEmpty() && statusClass.isEmpty()
                && outcome.isEmpty();
    }
}
