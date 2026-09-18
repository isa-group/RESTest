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

import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Offers the sample values the API's own author wrote down.
 *
 * <p>A great many documents show what a real call looks like: the owner to fetch is {@code 1}, the
 * cluster is called {@code cluster-1}, the surname to search for is {@code Davis}. Those are the
 * most valuable inputs a document contains, because they are usually values that exist in the API
 * the author was looking at while writing. Invented in their place, an identifier addresses nothing
 * and the reply is a polite "not found" that tests almost none of the API's behaviour.
 *
 * <p>A sample may be attached to the parameter itself or to the shape of its value, and a document
 * may give several. This prefers the parameter's own, which is what OpenAPI says takes precedence,
 * and picks among however many are left at random rather than always the first, so a run exercises
 * all of them.
 *
 * <p>It stands aside for a value the document restricts to a fixed list. Every sample it could
 * offer there is already one of the listed values, and offering it would pin the run to that one
 * member of a list the tool is meant to walk through.
 */
public final class ExampleValueProvider implements ValueProvider {

    private final RandomGenerator random;

    /**
     * A provider that picks among the document's sample values with the given source of randomness.
     *
     * @param random where the choice among several samples comes from. Sharing one seeded source
     *     across the whole run is what makes a run repeatable
     */
    public ExampleValueProvider(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.schema().metadata().isEnumerated()) {
            return Optional.empty();
        }
        List<JsonValue> samples = request.examples().isEmpty()
                ? request.schema().metadata().examples()
                : request.examples();
        if (samples.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(GeneratedValue.declared(samples.get(random.nextInt(samples.size())),
                ValueOrigin.Declared.Statement.EXAMPLE));
    }

    @Override
    public String name() {
        return "example";
    }
}
