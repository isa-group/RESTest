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

import io.restest.core.gen.ValueProvider;
import java.util.Objects;

/**
 * One way of putting a request together, and how much of the testing time it gets.
 *
 * <p>A run does not do only one thing. Most of its time goes on requests meant to work, built from
 * whatever the document says and from lists of values somebody thought were sensible. Some of it
 * goes on requests built from values nobody sensible would send - an empty word, an enormous number,
 * a value of the wrong kind entirely - which is how an API gets asked what it does when something
 * unexpected arrives. The second kind is not looking for a refusal, which would be a perfectly good
 * answer and often the right one; it is looking for the API falling over.
 *
 * <p>So a run divides its time between them. The share is a proportion rather than a stretch of the
 * clock, which is what lets the same division serve a thirty-second check and a two-hour campaign:
 * a quarter is a quarter either way.
 *
 * <p>Every request belongs to exactly one of these. Which sources fill in the individual values
 * inside it is a separate question, answered by the sources this one carries.
 *
 * @param name what this way of building a request is called, as a report would print it
 * @param share how much of the testing time it gets, as a proportion of the whole. The shares of a
 *     run's strategies are counted against each other, so they need not add up to anything in
 *     particular
 * @param pushesAtTheApi whether the requests it builds are meant to push at the API with values
 *     nobody sensible would send. Such a request is not expecting anything in particular back: a
 *     refusal is a fair answer, an acceptance may be fair too, and what it is looking for is the
 *     third answer, where the API falls over
 * @param values where the values in its requests come from, asked in order until one answers
 */
public record Strategy(String name, int share, boolean pushesAtTheApi, ValueProvider values) {

    public Strategy {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(values, "values");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a strategy has a name, which a report prints");
        }
        if (share <= 0) {
            throw new IllegalArgumentException("a strategy given none of the time would never run, "
                    + "so leaving it out is the way to say that: " + name + " asked for " + share);
        }
    }
}
