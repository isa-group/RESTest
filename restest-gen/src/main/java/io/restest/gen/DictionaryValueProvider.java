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

import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Offers the values a dictionary holds for an input, picking among them at random.
 *
 * <p>This is what turns a list of values into a source the tool can ask. It offers nothing when the
 * dictionary has nothing for this input, so the next source gets its turn; and when the dictionary
 * has several it takes one at random rather than always the first, so a run works through the whole
 * list instead of sending the same value over and over.
 *
 * <p>It also declines any value that could not actually be put in the request - one that would leave
 * a gap in the path empty, or that carries a line break into a header. A value nothing could send is
 * worse than no value at all, because it would be chosen and the whole attempt then thrown away.
 * That matters most here of all, because a dictionary of deliberately awkward values is full of
 * exactly those.
 */
public final class DictionaryValueProvider implements ValueProvider {

    private final Dictionary dictionary;
    private final RandomGenerator random;

    /**
     * A source offering the values this dictionary holds.
     *
     * @param dictionary where the values come from
     * @param random where the choice among several comes from. Sharing one seeded source across the
     *     whole run is what makes a run repeatable
     */
    public DictionaryValueProvider(Dictionary dictionary, RandomGenerator random) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        List<JsonValue> usable = dictionary.valuesFor(request).stream()
                .filter(value -> RequestBuilder.canBeSentFrom(value, request.location()))
                .toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(GeneratedValue.generatedBy(
                usable.get(random.nextInt(usable.size())), name()));
    }

    /** What this dictionary is called, which is what a report prints beside the value. */
    @Override
    public String name() {
        return dictionary.name();
    }
}
