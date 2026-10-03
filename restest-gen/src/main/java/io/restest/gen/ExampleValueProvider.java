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
 * falling back on the shape's when the parameter has none that can be used; among however many are
 * left it picks at random rather than always the first, so a run exercises all of them.
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
 *   <li><b>Any sample no request could be assembled with, from where the value goes.</b> A value
 *       that writes out as nothing closes a gap in the path instead of filling it, turning a
 *       request for one pet into a request for every pet; a value carrying a line break, sent as a
 *       header, ends that header and starts another. Both are thrown away when the request is put
 *       together, and a sample that is always thrown away is worse than no sample: it would win
 *       every draw, and the operation would send nothing at all for as long as the run lasts while
 *       still being counted among those being tested. Neither value is a problem anywhere else,
 *       and neither is refused anywhere else.</li>
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
        // The parameter's own samples take precedence, but only where it has one that can be used:
        // a parameter whose only sample cannot fill a gap in the path should fall back on the one
        // its shape offers rather than throw away an identifier the document wrote down.
        List<JsonValue> usable = usable(request.examples(), request.location());
        if (usable.isEmpty()) {
            usable = usable(request.schema().metadata().examples(), request.location());
        }
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(GeneratedValue.declared(usable.get(random.nextInt(usable.size())),
                ValueOrigin.Declared.Statement.EXAMPLE));
    }

    /**
     * The samples a request could actually be assembled with, from where this value goes.
     *
     * <p>The question is put to the very code that will write the value into the request, rather
     * than guessed at from the value's shape, because the two disagree - and because a sample
     * nothing could send is worse than no sample at all: it would win every draw and every attempt
     * at that operation would be thrown away, for as long as the run lasts.
     *
     * <p>Asked of a value nested inside one that goes in the path, this is stricter than it needs
     * to be - a property whose value is empty is still written with its own name beside it. Nothing
     * is lost by that beyond one sample, and a rule that has to know how deep it is would be worse
     * than a rule that is occasionally shy.
     */
    private static List<JsonValue> usable(List<JsonValue> samples, ParameterLocation location) {
        return samples.stream()
                .filter(sample -> RequestBuilder.canBeSentFrom(sample, location))
                .toList();
    }

    @Override
    public String name() {
        return "example";
    }
}
