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
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.WfcFault;
import java.util.List;

/**
 * Reports an API that answered 500, which means it fell over while handling the request.
 *
 * <p>500 is the status code a server sends when something went wrong inside it that it did not
 * expect - most often an unhandled error in the code. It is the oldest and plainest evidence a
 * black-box tester can get that there is a bug in there somewhere, because no well-behaved API
 * answers a request with "I crashed".
 *
 * <p>It is not proof. An API can answer 500 because its database is down or because another service
 * it depends on is not answering, neither of which is a bug in the API itself. The catalogue this
 * fault is named from says as much, and so does every report RESTest writes: a 500 is worth a
 * person's attention, not an automatic verdict.
 *
 * <p>Only 500 is reported here, not the other codes in the 500s. 502, 503 and 504 are what a
 * gateway or a load balancer says when something in front of the API is unwell, and counting those
 * as faults of the API would inflate the count with things nobody can fix in the code. 501, "not
 * implemented", is about what the document promises rather than about a crash, and belongs with the
 * oracle that checks the status code against the document. Counting exactly what other tools count
 * is also what makes a RESTest fault count comparable with theirs.
 */
public final class ServerErrorOracle implements Oracle {

    /** The one status code this reports on. */
    private static final int INTERNAL_SERVER_ERROR = 500;

    @Override
    public String name() {
        return "server-error";
    }

    @Override
    public String description() {
        return "reports an API that answered 500, which means it fell over handling the request";
    }

    @Override
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        return interaction.response()
                .filter(response -> response.statusCode() == INTERNAL_SERVER_ERROR)
                .map(response -> List.of(Finding.of(WfcFault.HTTP_STATUS_500, interaction,
                        "the API answered 500, so it fell over while handling this request")))
                .orElseGet(List::of);
    }
}
