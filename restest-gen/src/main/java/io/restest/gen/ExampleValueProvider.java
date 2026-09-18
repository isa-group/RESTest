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
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.NothingSchema;
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
 * <p>A sample is offered as the author wrote it, even where the document's own rules about the
 * value would refuse it. When a concrete sample and an abstract rule disagree, there is no telling
 * which of the two the author meant, and the sample is at least as good evidence about what the API
 * accepts. Three kinds of sample are not offered, each for a reason of its own:
 *
 * <ul>
 *   <li><b>Any sample for a value restricted to a fixed list.</b> Every sample it could offer is
 *       already one of the listed values, and using it would pin the run to that one member of a
 *       list the tool is meant to walk through.</li>
 *   <li><b>Any sample for a value the document says has no acceptable value at all.</b> There the
 *       document contradicts itself outright, and the half that says "nothing fits here" is the
 *       half every other source already believes.</li>
 *   <li><b>A sample that writes out as nothing, where it belongs in the path.</b> An empty piece of
 *       a path closes the gap instead of filling it, turning a request for one pet into a request
 *       for every pet - and the reply would then be judged against the wrong promise. Elsewhere an
 *       empty value is a perfectly ordinary thing to send.</li>
 * </ul>
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
        if (request.schema().metadata().isEnumerated()
                || request.schema() instanceof NothingSchema) {
            return Optional.empty();
        }
        List<JsonValue> stated = request.examples().isEmpty()
                ? request.schema().metadata().examples()
                : request.examples();
        List<JsonValue> usable = request.location() == ParameterLocation.PATH
                ? stated.stream().filter(ExampleValueProvider::fillsAGapInThePath).toList()
                : stated;
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(GeneratedValue.declared(usable.get(random.nextInt(usable.size())),
                ValueOrigin.Declared.Statement.EXAMPLE));
    }

    /**
     * Whether this value, written into a path, would leave something between the slashes.
     *
     * <p>Judged from the value's shape rather than by writing it out, because what writes out as
     * nothing is the same short list either way: no value at all, a word of no letters, and a list
     * or an object with nothing in it.
     */
    private static boolean fillsAGapInThePath(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonNull ignored -> false;
            case JsonValue.JsonString text -> !text.value().isEmpty();
            case JsonValue.JsonArray list -> !list.elements().isEmpty();
            case JsonValue.JsonObject object -> !object.members().isEmpty();
            case JsonValue.JsonBoolean ignored -> true;
            case JsonValue.JsonNumber ignored -> true;
        };
    }

    @Override
    public String name() {
        return "example";
    }
}
