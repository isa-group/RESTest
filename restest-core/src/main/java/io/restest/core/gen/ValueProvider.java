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
package io.restest.core.gen;

import java.util.Optional;

/**
 * Something that can suggest a value to put in a request.
 *
 * <p>This is how RESTest gets its inputs, and it is deliberately the narrowest interface in the
 * project: asked about one parameter, either suggest something or say nothing. What sits behind it
 * is nobody else's business - a random number generator, a list of good values kept beside the
 * specification, a solver working out what satisfies a set of rules, or a program in another
 * language answering over a pipe. All of them are this shape, which is why adding one of them later
 * means writing one class rather than changing how test cases are made.
 *
 * <p>Providers are asked in order and the first answer is used, so the order expresses what the tool
 * trusts most. A provider that knows nothing useful about a particular parameter - a generator of
 * dates asked for a number - says nothing and lets the next one answer. Saying nothing is the normal
 * case, not a failure.
 *
 * <p>A provider is asked while a request is being built, so it answers from what it already knows.
 * Anything slow - asking another program, solving a system of constraints - belongs behind an
 * arrangement that has the answer ready before it is needed, because the moment the tool waits for a
 * value is a moment it is not testing the API.
 */
@FunctionalInterface
public interface ValueProvider {

    /**
     * Suggests a value, or declines.
     *
     * @param request which parameter of which operation, and the shape the value must satisfy
     * @return a value and where it came from, or empty to let the next provider answer
     */
    Optional<GeneratedValue> offer(ValueRequest request);

    /**
     * What to call this provider in a report, so a person reading one can tell where a value came
     * from.
     *
     * @return a short name; the class's own by default
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
