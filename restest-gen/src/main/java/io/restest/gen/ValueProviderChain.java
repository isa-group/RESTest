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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Several sources of values, asked in order until one of them answers.
 *
 * <p>The order is the tool's order of preference, and it is the whole design. A value the
 * specification's own author wrote down is better than one invented from the shape of the data; a
 * value taken from a list somebody curated for this API is better still; a value worked out from the
 * rules the API documents about itself beats all of them. Each of those is one more source in this
 * list, and adding one never means changing how requests are built - which is what lets the tool grow
 * better inputs over time without growing a more complicated generator.
 *
 * <p>A source that has nothing useful to say about a particular parameter simply says nothing, and
 * the next one is asked. If nobody answers, the caller learns that no value could be found, which is
 * a fact about the specification worth reporting rather than a failure to paper over.
 */
public final class ValueProviderChain implements ValueProvider {

    private final List<ValueProvider> providers;

    private ValueProviderChain(List<ValueProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /**
     * A chain that asks these sources, in this order.
     *
     * @param providers the sources, most trusted first
     * @return the chain
     */
    public static ValueProviderChain of(ValueProvider... providers) {
        return new ValueProviderChain(List.of(providers));
    }

    /**
     * A chain that asks these sources, in this order.
     *
     * @param providers the sources, most trusted first
     * @return the chain
     */
    public static ValueProviderChain of(List<ValueProvider> providers) {
        return new ValueProviderChain(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        for (ValueProvider provider : providers) {
            Optional<GeneratedValue> offered = ask(provider, request);
            if (offered.isPresent()) {
                return offered;
            }
        }
        return Optional.empty();
    }

    /**
     * One source's answer, treating a source that breaks as one that had nothing to say.
     *
     * <p>Sources are the one part of RESTest that somebody else is invited to write, and they run in
     * the middle of a run. A source that throws is a bug in that source, and the right response is to
     * carry on asking the others: the alternative is that a dictionary with one bad entry, or a
     * helper program that crashed, ends a test run that was otherwise going fine.
     */
    private static Optional<GeneratedValue> ask(ValueProvider provider, ValueRequest request) {
        try {
            return provider.offer(request);
        } catch (RuntimeException broken) {
            return Optional.empty();
        }
    }

    /** The sources this chain asks, in the order it asks them. */
    public List<ValueProvider> providers() {
        return providers;
    }

    @Override
    public String name() {
        return providers.stream().map(ValueProvider::name).reduce((a, b) -> a + " then " + b)
                .orElse("nothing");
    }
}
