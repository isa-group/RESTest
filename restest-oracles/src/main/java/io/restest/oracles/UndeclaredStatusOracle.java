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
package io.restest.oracles;

import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.ResponseModel;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.RestestFault;
import io.restest.core.oracle.WhyNotJudged;
import java.util.stream.Collectors;

/**
 * Reports a reply whose status code the API's document does not declare for that operation.
 *
 * <p>A document lists, for each operation, the status codes it answers with: {@code 200} and
 * {@code 404} for looking a pet up, say. A program written against that document handles those and
 * nothing else. An API that answers {@code 201} where the document promises {@code 200}, or
 * {@code 400} where it promises no refusal at all, has drifted from what it tells its users, and
 * this rule says so: the document, or the API, has something to fix.
 *
 * <p>A status code counts as declared when the document names it, names its range such as
 * {@code 4XX}, or gives a {@code default} reply, which covers every code it does not name. Server
 * errors, which other rules report, and every reply {@link HeldToTheDocument} keeps out, are left
 * alone.
 *
 * <p>Two kinds of reply it cannot judge, and says so. One to an operation whose document declares no
 * reply at all, since that document has promised nothing. And one whose status code is not among
 * those it read for an operation it could read only in part, since the code may be declared in the
 * part it could not read; a code it did read is held to as on any other operation.
 *
 * <p>This rule is often noisy, and not because it is wrong: documents frequently leave out the
 * refusals an API makes, and every request that meets one is reported. Its switch turns it off for
 * a run where that is known and not wanted.
 */
public final class UndeclaredStatusOracle implements SaysWhyNot {

    @Override
    public String name() {
        return "undeclared-status-code";
    }

    @Override
    public String description() {
        return "reports a reply whose status code, below 500, the document does not declare for "
                + "the operation";
    }

    @Override
    public FaultCategory kind() {
        return RestestFault.UNDECLARED_STATUS_CODE;
    }

    @Override
    public String what() {
        return "the status code";
    }

    @Override
    public Verdict verdict(Interaction interaction, ApiModel api) {
        return interaction.response()
                .flatMap(response -> HeldToTheDocument.operationFor(interaction, response, api)
                        .map(operation -> verdict(interaction, response, operation, api)))
                .orElse(Verdict.NOTHING);
    }

    /**
     * In this order: a code the document declares is fine, even on an operation read only in part;
     * then a code that may be declared in the part RESTest could not read, and an operation that
     * declares no reply at all, cannot be judged; anything else is a code the document leaves out.
     */
    private Verdict verdict(Interaction interaction, HttpResponseRecord response,
            Operation operation, ApiModel api) {
        if (operation.responseFor(response.statusCode()).isPresent()) {
            return Verdict.NOTHING;
        }
        if (api.readInPart(operation.id())) {
            return Verdict.couldNot(WhyNotJudged.READ_IN_PART);
        }
        if (operation.responses().isEmpty()) {
            return Verdict.couldNot(WhyNotJudged.NO_REPLY_DECLARED);
        }
        return Verdict.of(Finding.of(kind(), interaction,
                "the API answered " + response.statusCode() + ", which the document does not "
                        + "declare for this operation; it declares " + declared(operation)));
    }

    /** The status codes, ranges and {@code default} the operation declares, in the document's order. */
    private static String declared(Operation operation) {
        return operation.responses().stream()
                .map(ResponseModel::status)
                .collect(Collectors.joining(", "));
    }
}
