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
import io.restest.core.schema.SchemaMetadata;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Offers the values the specification says the API accepts.
 *
 * <p>A specification often says exactly what to send: a default to use when the caller says nothing,
 * or a closed list of the only values the API will accept - {@code available}, {@code pending},
 * {@code sold}. Those are the best inputs available anywhere, because the API's own author wrote
 * them, and a tool that invented a random word instead would get a refusal and learn nothing.
 *
 * <p>Asked about a parameter with a closed list, this picks one of the listed values at random rather
 * than always the first, so a run exercises the whole list rather than one member of it. Asked about
 * anything the specification says nothing concrete about, it says nothing and lets the next source
 * answer.
 *
 * <p>Sample values are a third thing a document can state, and they belong to a source of their
 * own rather than here, because they are offered in preference to a default and the two would
 * otherwise have to be ranked inside one class.
 *
 * <p>A stated default is different, and worth knowing about: it is one value, so every request that
 * includes that parameter carries the same one, and the run never varies it. That is the right
 * behaviour for a value the API's author chose, and it does mean a parameter like {@code page} or
 * {@code limit} is exercised at its default and nowhere near its limits - which is a job for a source
 * that deliberately walks the edges of what a parameter allows, not for this one.
 */
public final class DeclaredValueProvider implements ValueProvider {

    private final RandomGenerator random;
    private final boolean readsTheList;
    private final boolean readsTheDefault;

    /**
     * A provider that offers both the closed list of accepted values and the stated default.
     *
     * @param random where the choice among several allowed values comes from. Sharing one seeded
     *     source across the whole run is what makes a run repeatable
     */
    public DeclaredValueProvider(RandomGenerator random) {
        this(random, true, true);
    }

    private DeclaredValueProvider(RandomGenerator random, boolean readsTheList,
            boolean readsTheDefault) {
        this.random = Objects.requireNonNull(random, "random");
        this.readsTheList = readsTheList;
        this.readsTheDefault = readsTheDefault;
    }

    /**
     * A provider that offers only the closed list of values a document says it accepts.
     *
     * <p>Kept apart from the default because the two are asked at different moments. A closed list
     * is not advice, it is the whole set of values the API will take, so nothing else may be
     * offered where one exists - not a list somebody else wrote, and not a value we invented. A
     * default is advice, and takes its turn among the rest.
     *
     * @param random where the choice among several allowed values comes from
     * @return the provider
     */
    public static DeclaredValueProvider onlyTheAcceptedList(RandomGenerator random) {
        return new DeclaredValueProvider(random, true, false);
    }

    /**
     * A provider that offers only the value a document says the API uses when none is sent.
     *
     * @param random unused here, and taken so that both halves are built the same way
     * @return the provider
     */
    public static DeclaredValueProvider onlyTheDefault(RandomGenerator random) {
        return new DeclaredValueProvider(random, false, true);
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        SchemaMetadata metadata = request.schema().metadata();
        if (readsTheList) {
            List<JsonValue> allowed = sendable(metadata.enumeration(), request);
            if (!allowed.isEmpty()) {
                return Optional.of(GeneratedValue.declared(
                        allowed.get(random.nextInt(allowed.size())),
                        ValueOrigin.Declared.Statement.ENUMERATION));
            }
        }
        if (!readsTheDefault) {
            return Optional.empty();
        }
        return metadata.defaultValue()
                .filter(value -> RequestBuilder.canBeSentFrom(value, request.location()))
                .map(value -> GeneratedValue.declared(value,
                        ValueOrigin.Declared.Statement.DEFAULT));
    }

    /**
     * The values of the list that a request could actually be built with.
     *
     * <p>A document may allow a value that cannot be put where this one goes - a list of accepted
     * values with an empty word among them, for a parameter that fills a gap in the path. Offering
     * it would close the gap instead of filling it, and the whole attempt would be thrown away when
     * the request was put together. Standing aside for that one value leaves the others usable,
     * which is better than losing the parameter and better than losing the request.
     */
    private static List<JsonValue> sendable(List<JsonValue> values, ValueRequest request) {
        return values.stream()
                .filter(value -> RequestBuilder.canBeSentFrom(value, request.location()))
                .toList();
    }

    @Override
    public String name() {
        return "declared";
    }
}
