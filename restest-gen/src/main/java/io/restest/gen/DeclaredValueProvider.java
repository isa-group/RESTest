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
import io.restest.core.schema.SchemaMetadata;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Offers the values the specification itself states.
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
 */
public final class DeclaredValueProvider implements ValueProvider {

    private final RandomGenerator random;

    /**
     * A provider that picks among declared values with the given source of randomness.
     *
     * @param random where the choice among several allowed values comes from. Sharing one seeded
     *     source across the whole run is what makes a run repeatable
     */
    public DeclaredValueProvider(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        SchemaMetadata metadata = request.schema().metadata();
        List<JsonValue> allowed = metadata.enumeration();
        if (!allowed.isEmpty()) {
            return Optional.of(GeneratedValue.declared(allowed.get(random.nextInt(allowed.size()))));
        }
        return metadata.defaultValue().map(GeneratedValue::declared);
    }

    @Override
    public String name() {
        return "declared";
    }
}
