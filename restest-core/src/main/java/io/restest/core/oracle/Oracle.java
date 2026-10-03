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
package io.restest.core.oracle;

import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import java.util.List;

/**
 * A rule that looks at one attempt against an API and says whether anything was wrong with it.
 *
 * <p>Inventing requests is only half of testing; deciding whether the answer was acceptable is the
 * other half, and that is what an oracle does. "A request the specification allows must not make
 * the server fall over" is an oracle. So is "a reply must look the way the specification says it
 * will".
 *
 * <p>Each oracle sees one attempt at a time, together with the specification, and hands back
 * everything it objects to - usually nothing. An oracle that cannot tell must say nothing: a rule
 * that guesses produces complaints about working APIs, and a tool that cries wolf gets switched
 * off.
 *
 * <p>Adding a new kind of check to RESTest means writing one of these and nothing else. Oracles
 * that need to look at a whole run rather than one attempt at a time - "this resource was deleted,
 * so why can it still be read?" - are a separate kind, and arrive later.
 */
public interface Oracle {

    /** A short name, in lower case with hyphens, such as {@code server-error}. */
    String name();

    /** One sentence saying what this oracle checks, for anyone asking what the tool looked for. */
    String description();

    /**
     * Everything this oracle objects to about one attempt, or an empty list if it has no objection
     * - which includes the case where it had no way of telling.
     *
     * @param interaction the request that was sent and what came back
     * @param api         the specification the API was supposed to follow
     */
    List<Finding> judge(Interaction interaction, ApiModel api);
}
