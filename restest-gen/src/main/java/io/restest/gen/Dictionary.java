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
package io.restest.gen;

import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import java.util.List;

/**
 * A named list of values somebody thinks are worth sending, and a way of finding the ones that
 * apply to a particular input.
 *
 * <p>A dictionary is the simplest idea in the tool and one of the most useful: rather than working
 * out a value from the rules a document states about it, look one up. A list of real surnames beats
 * a random word for a parameter called {@code lastName}; a list of deliberately awkward values is
 * how an API gets asked what it does with input nobody sensible would send; and the identifiers an
 * API handed back earlier in the same run are better than any of them.
 *
 * <p>All three are the same thing with a different way of deciding which values apply - the kind of
 * value wanted, the format the document declares for it, the name of the shape, the name of the
 * parameter, or the operation and parameter together. That decision is called the <em>keying</em>,
 * and it is the only thing that differs between them.
 *
 * <p>Where the values come from is not fixed either. Most are read from a file, which is why the
 * file format is written down and anybody can add one. One of them will instead be filled in as the
 * run goes along, out of the API's own replies, and nothing that asks a dictionary a question needs
 * to know which kind it is holding.
 *
 * <p>A dictionary also says what it believes about its own values: that the API should accept them,
 * that it should refuse them, or that nobody has checked. That last one is the ordinary case and is
 * not an admission of failure - a list of plausible surnames is a genuinely useful thing to have
 * without anybody having confirmed that this particular API takes any of them.
 */
public interface Dictionary {

    /** What a dictionary believes about the values it holds. */
    enum Expectation {
        /** Somebody checked, or the API itself supplied them: these should be accepted. */
        ACCEPTANCE,
        /** These are meant to be refused, and a reply that is neither a refusal nor a failure is news. */
        REFUSAL,
        /** Nobody has checked against this API. The ordinary case, and an honest answer. */
        UNKNOWN
    }

    /**
     * The values this dictionary offers for one input, in the order it holds them.
     *
     * @param request what a value is wanted for
     * @return the values, or an empty list when this dictionary knows nothing about it
     */
    List<JsonValue> valuesFor(ValueRequest request);

    /**
     * What this dictionary is called, which is how a plan refers to it.
     *
     * @return the name
     */
    String name();

    /**
     * What this dictionary believes about its own values.
     *
     * @return the expectation
     */
    Expectation expects();

    /**
     * Whether this dictionary is about one value in particular rather than a whole kind of them.
     *
     * <p>It decides when the dictionary gets asked. One written for a named parameter or a named
     * operation knows more about that value than the document's own sample of it, so it is asked
     * first; one written for every date or every piece of text knows less, so it is asked after the
     * document has had its say.
     *
     * <p>Every dictionary answers this for itself rather than having it worked out from the outside,
     * because the ones that are not files - a list filled in from the API's own replies as a run
     * goes along - have no keying to read it from.
     *
     * @return whether it is about one value in particular
     */
    boolean isAboutOneValueInParticular();
}
