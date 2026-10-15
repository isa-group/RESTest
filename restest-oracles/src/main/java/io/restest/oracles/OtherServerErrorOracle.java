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
 * Reports an API that answered with a server error other than 500 and 501: a 502, 503 or 504, or
 * any other status code from 502 to 599.
 *
 * <p>Status codes in the 500s say that the server failed to handle a request it may well have had
 * nothing to object to. 500 is the plain crash, and {@link ServerErrorOracle} reports it. The others
 * say something more particular: 502 and 504 that something the API passed the request on to sent
 * back nonsense or nothing in time, 503 that the API cannot serve requests just now. Each is worth a
 * person's attention, and the catalogue gives them a kind of their own.
 *
 * <p>They are weaker evidence of a bug than a 500. They are often said by a gateway or a load
 * balancer in front of the API, or because a database or another service the API calls was down,
 * and a 503 can be an API asking a caller that sends too much to wait. Whoever reads the fault has
 * to decide whether it is the API's code or its surroundings, and a run that tests an API whose
 * surroundings are known to be unwell can switch this rule off without losing the 500s.
 *
 * <p>501, "not implemented", is left alone: it is about what the document promises rather than a
 * failure, and the catalogue has another kind for an API that answers it to an operation its
 * document declares. Codes of 600 and above are not server errors at all, whatever their first
 * digit.
 */
public final class OtherServerErrorOracle implements Oracle {

    /** The first code this reports on: 500 and 501 are the two the catalogue keeps apart. */
    private static final int FIRST = 502;

    /** The last code in the 500s. */
    private static final int LAST = 599;

    @Override
    public String name() {
        return "other-server-error";
    }

    @Override
    public String description() {
        return "reports an API that answered a server error other than 500 and 501, such as 503";
    }

    @Override
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        return interaction.response()
                .filter(response -> response.statusCode() >= FIRST
                        && response.statusCode() <= LAST)
                .map(response -> List.of(Finding.of(WfcFault.HTTP_OTHER_STATUS_5XX, interaction,
                        "the API answered " + response.statusCode() + ", a server error other "
                                + "than 500: something in it, or in front of it, failed while "
                                + "handling this request")))
                .orElseGet(List::of);
    }
}
