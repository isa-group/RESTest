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

import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.WfcFault;
import java.util.List;
import java.util.Optional;

/**
 * Reports an API that accepted a request RESTest broke on purpose, which it should have turned away.
 *
 * <p>One way RESTest tests an API is to take a request the API has just accepted and send it again
 * with exactly one thing wrong: a value the document says is required left out, a word where it
 * says a number goes, a value off the closed list it gives. The document forbids each of those, so a
 * well-behaved API answers with a refusal, a 400 or a 422. An API that answers with a success
 * instead has let through something its own document says it never takes, and what it did with it
 * - stored it, acted on it - is something nobody planned for.
 *
 * <p>Each such request records, when it is built, that a refusal is expected and what one thing
 * was changed; see {@link Mutation}. This rule reads only that record and the status code that came
 * back. Everything else in the request is a copy of one the API accepted, so the success can be put
 * down to the one change, and the fault says which it was.
 *
 * <p>It is not always a bug in the API's own code. The library that reads a request for it may
 * accept more than the document allows - a number where a word is declared, turned into the word,
 * or spaces around a number trimmed away - and the document may be stricter than the API was ever
 * meant to be. Either way the API and its document disagree, which is what the fault says.
 *
 * <p>Some other requests expect a refusal too: one that reads again a thing the API said, a moment
 * before, that it had deleted. A success there is a different kind of fault, about what deleting
 * did, and is left to the rule that judges it. Only a success counts here: a refusal of any kind is
 * what was expected, and a server error is reported by the rules for those.
 */
public final class InvalidDataAcceptedOracle implements Oracle {

    @Override
    public String name() {
        return "invalid-data-accepted";
    }

    @Override
    public String description() {
        return "reports an API that accepted a request broken on purpose, which it should have "
                + "turned away";
    }

    @Override
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        return brokenOnPurpose(interaction.testCase())
                .flatMap(change -> interaction.response()
                        .filter(response -> response.statusCode() >= 200
                                && response.statusCode() < 300)
                        .map(response -> List.of(Finding.of(WfcFault.SCHEMA_VALIDATION_BYPASS,
                                interaction, "the API accepted a request it should have refused: "
                                        + change.description()))))
                .orElseGet(List::of);
    }

    /**
     * The one change that broke what the document says, when this request was made that way and
     * a refusal was expected of it.
     */
    private static Optional<Mutation> brokenOnPurpose(TestCase testCase) {
        return testCase.mutation().filter(change -> testCase.intent() == Intent.REFUSAL_EXPECTED);
    }
}
