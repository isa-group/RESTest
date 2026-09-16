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
package io.restest.arch.fixtures.mirror.gen;

import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Violates {@code noRandomGeneratorAskedForByName}: every line here picks a generator that a
 * runtime carrying only the compulsory part of Java does not have, so none of them starts.
 */
public final class GenAskingForARandomGeneratorByName {

    /** Named outright: the name only exists where the optional module was installed. */
    public RandomGenerator byName(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    /** Not named, but the platform's answer is one of the optional ones just the same. */
    public RandomGenerator theDefaultOne() {
        return RandomGenerator.getDefault();
    }

    /**
     * The shortest way to write this mistake, and the one an earlier version of the rule missed.
     *
     * <p>No factory is mentioned at all, yet this asks for the same optional generator by the same
     * name and fails on the same runtimes.
     */
    public RandomGenerator theShortWay(long seed) {
        return RandomGenerator.of("L64X128MixRandom").isDeprecated()
                ? null
                : RandomGenerator.of("L64X128MixRandom");
    }

    /** The same again on one of the nested kinds, which repeat both naming methods. */
    public RandomGenerator.SplittableGenerator aNestedKind() {
        return RandomGenerator.SplittableGenerator.of("L64X128MixRandom");
    }

    /**
     * The same question as a method reference rather than a call.
     *
     * <p>A rule that only looks at calls reports the ones above and lets this one through, which is
     * why the rule looks at both shapes.
     */
    public Supplier<RandomGeneratorFactory<RandomGenerator>> deferred() {
        return RandomGeneratorFactory::getDefault;
    }
}
