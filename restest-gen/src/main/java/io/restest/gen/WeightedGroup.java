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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Several sources of values, all asked, with one of their answers chosen by chance.
 *
 * <p>This is the other way of putting sources together. The ordinary way is to ask them in turn and
 * take the first answer, which is right when one source plainly knows better than the next. It is
 * wrong when several of them are worth hearing from: a parameter whose document offers one sample
 * value would otherwise receive that same value on every request for the whole run, and nothing
 * else would ever be tried there.
 *
 * <p>So every source in the group is asked, and one of the answers is picked. How likely each is to
 * be picked is written in the plan as a weight, which is somebody's decision about how much that
 * source is worth for this API - not something a source claims about itself.
 *
 * <p><b>A source with nothing to say does not get its share.</b> If half the group has no answer for
 * this particular value, the choice is made among the half that did, in proportion to their weights
 * alone. That matters more than it sounds: a plan naming a list of values that has not been handed
 * over, or one naming the API's own replies before the API has answered anything, still behaves
 * sensibly instead of leaving a gap. It also means the proportions a run actually achieves will
 * differ from the ones written down, which is worth knowing before measuring them.
 */
public final class WeightedGroup implements ValueProvider {

    /** One source and how much of the choice it gets when it answers. */
    public record Weighted(ValueProvider source, int weight) {

        public Weighted {
            Objects.requireNonNull(source, "source");
            if (weight <= 0) {
                throw new IllegalArgumentException("a source given no weight would never be "
                        + "chosen, so leaving it out is the way to say that: " + source.name()
                        + " asked for " + weight);
            }
        }
    }

    private final List<Weighted> sources;
    private final RandomGenerator random;

    /**
     * A group asking all of these and choosing among whichever answer.
     *
     * @param sources the sources and their weights
     * @param random where the choice comes from. Sharing one seeded source across the whole run is
     *     what lets a run without a memory be repeated
     */
    public WeightedGroup(List<Weighted> sources, RandomGenerator random) {
        Objects.requireNonNull(sources, "sources");
        this.random = Objects.requireNonNull(random, "random");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("a group of sources with nothing in it would answer "
                    + "nothing, every time");
        }
        this.sources = List.copyOf(sources);
    }

    @Override
    public Optional<GeneratedValue> offer(ValueRequest request) {
        Objects.requireNonNull(request, "request");
        List<GeneratedValue> answers = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Weighted held : sources) {
            // A source that breaks is one that had nothing to say, for the reason ValueProviderChain
            // gives at length: sources are what somebody else is invited to write, and one of them
            // throwing must not end a run that was otherwise going fine.
            Optional<GeneratedValue> offered;
            try {
                offered = held.source().offer(request);
            } catch (RuntimeException broken) {
                offered = Optional.empty();
            }
            if (offered.isPresent()) {
                answers.add(offered.get());
                weights.add(held.weight());
                total += held.weight();
            }
        }
        if (answers.isEmpty()) {
            return Optional.empty();
        }
        int drawn = random.nextInt(total);
        for (int at = 0; at < answers.size(); at++) {
            drawn -= weights.get(at);
            if (drawn < 0) {
                return Optional.of(answers.get(at));
            }
        }
        return Optional.of(answers.get(answers.size() - 1));
    }

    /** The sources this group asks, with their weights. */
    public List<Weighted> sources() {
        return sources;
    }

    @Override
    public String name() {
        return sources.stream().map(held -> held.source().name())
                .reduce((a, b) -> a + " or " + b).orElse("nothing");
    }
}
