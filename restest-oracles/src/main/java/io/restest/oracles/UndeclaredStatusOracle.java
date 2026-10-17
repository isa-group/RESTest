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

import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.ResponseModel;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.RestestFault;
import java.util.List;
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
 * {@code 4XX}, or gives a {@code default} reply, which covers every code it does not name. An
 * operation whose document declares no reply at all is left alone, since it has promised nothing.
 * So are server errors, which other rules report, and every reply {@link HeldToTheDocument} keeps
 * out.
 *
 * <p>This rule is often noisy, and not because it is wrong: documents frequently leave out the
 * refusals an API makes, and every request that meets one is reported. Its switch turns it off for
 * a run where that is known and not wanted.
 */
public final class UndeclaredStatusOracle implements Oracle {

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
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        return interaction.response()
                .flatMap(response -> HeldToTheDocument.operationFor(interaction, response, api)
                        .filter(operation -> !operation.responses().isEmpty()
                                && operation.responseFor(response.statusCode()).isEmpty())
                        .map(operation -> List.of(Finding.of(RestestFault.UNDECLARED_STATUS_CODE,
                                interaction, "the API answered " + response.statusCode()
                                        + ", which the document does not declare for this "
                                        + "operation; it declares " + declared(operation)))))
                .orElseGet(List::of);
    }

    /** The status codes, ranges and {@code default} the operation declares, in the document's order. */
    private static String declared(Operation operation) {
        return operation.responses().stream()
                .map(ResponseModel::status)
                .collect(Collectors.joining(", "));
    }
}
