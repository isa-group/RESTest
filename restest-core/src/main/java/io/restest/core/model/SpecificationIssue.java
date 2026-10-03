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
package io.restest.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Something in the API's specification document that RESTest could not fully use: where it was,
 * what it cost, and which operation it belongs to.
 *
 * <p>A broken or unusual specification must never crash RESTest: instead, the affected operation is
 * skipped and the problem is reported here. Without that report, a run that silently tested 40 of an
 * API's 60 operations would look exactly like a run of a 40-operation API, leaving the user no way
 * to tell which one they got.
 *
 * <p>{@link Effect} tells apart "this operation is not being tested at all" from "it is being tested
 * with less information than the document actually contains" - a reader who cannot tell those apart
 * cannot properly judge the run. The operation identifier is kept for the same reason: a report that
 * groups findings by operation can show what could not be read about each one, right next to it.
 *
 * @param location where in the document the problem is, written the way the document is navigated:
 *     {@code paths./pets.get.parameters[2]}. Precise enough to open the file and look
 * @param message what could not be used and why, in a sentence a reader of the report can act on
 * @param operation the operation it concerns, absent when the problem is the document as a whole
 * @param effect what it cost us
 */
public record SpecificationIssue(
        String location,
        String message,
        Optional<OperationId> operation,
        SpecificationIssue.Effect effect) {

    /** What an unreadable construct cost. */
    public enum Effect {
        /** The operation is not being tested at all. */
        OPERATION_SKIPPED,
        /** The operation is being tested with less information than the document holds. */
        DEGRADED,
        /** The problem is the document as a whole, not one operation. */
        DOCUMENT
    }

    public SpecificationIssue {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(effect, "effect");
        if (location.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException(
                    "an issue that does not say where it is or what it is cannot be acted on");
        }
    }

    /** An operation that is not being tested at all. */
    public static SpecificationIssue skipped(String location, OperationId operation,
            String message) {
        return new SpecificationIssue(location, message,
                Optional.of(Objects.requireNonNull(operation, "operation")),
                Effect.OPERATION_SKIPPED);
    }

    /** An operation being tested with less than the document says. */
    public static SpecificationIssue degraded(String location, OperationId operation,
            String message) {
        return new SpecificationIssue(location, message,
                Optional.of(Objects.requireNonNull(operation, "operation")), Effect.DEGRADED);
    }

    /** A problem with the document itself rather than with one operation. */
    public static SpecificationIssue document(String location, String message) {
        return new SpecificationIssue(location, message, Optional.empty(), Effect.DOCUMENT);
    }

    /** Whether this issue means an operation is not being tested. */
    public boolean skipsAnOperation() {
        return effect == Effect.OPERATION_SKIPPED;
    }

    @Override
    public String toString() {
        return location + ": " + message;
    }
}
