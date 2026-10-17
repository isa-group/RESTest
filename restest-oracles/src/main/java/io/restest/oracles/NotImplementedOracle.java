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
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.WfcFault;
import java.util.List;

/**
 * Reports an API that answered 501, "not implemented", to an operation its document declares.
 *
 * <p>A document that lists an operation promises that the API does it. A 501 says the API does not:
 * the operation was written into the document and never built, or was taken out of the code and
 * left in the document. Either way somebody reading the document is told of something that is not
 * there, and the catalogue gives this a kind of its own.
 *
 * <p>It is reported whatever was sent and whether or not the document lists 501 among the
 * operation's replies: a document that promises an operation and also says it may not be
 * implemented has still promised something the API does not do. A 501 to anything the document
 * does not declare is left alone - that is an API saying, correctly, that it does not do something
 * nobody said it did.
 */
public final class NotImplementedOracle implements Oracle {

    /** "Not Implemented". */
    private static final int NOT_IMPLEMENTED = 501;

    @Override
    public String name() {
        return "not-implemented";
    }

    @Override
    public String description() {
        return "reports a reply of 501, not implemented, to an operation the document declares";
    }

    @Override
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        return interaction.response()
                .map(HttpResponseRecord::statusCode)
                .filter(code -> code == NOT_IMPLEMENTED)
                .filter(code -> api.operation(interaction.testCase().operation()).isPresent())
                .map(code -> List.of(Finding.of(WfcFault.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED,
                        interaction, "the API answered 501, not implemented, to an operation its "
                                + "document declares")))
                .orElseGet(List::of);
    }
}
