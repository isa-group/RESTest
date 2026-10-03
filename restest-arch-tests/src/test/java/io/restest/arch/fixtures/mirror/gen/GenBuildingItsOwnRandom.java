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

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Obeys {@code noRandomGeneratorAskedForByName}, and is here so the rule cannot be widened into a
 * ban on randomness altogether.
 *
 * <p>This is what the real generator does: build the source every Java runtime is required to
 * carry, and pass it around as the interface. Reporting either of these would fail correct code.
 */
public final class GenBuildingItsOwnRandom {

    private final RandomGenerator random;

    public GenBuildingItsOwnRandom(long seed) {
        this.random = new SplittableRandom(seed);
    }

    public int pick(int howMany) {
        return random.nextInt(howMany);
    }

    /**
     * Asking what this runtime actually has, rather than naming something and hoping.
     *
     * <p>This is the careful way to prefer a better generator where one exists, and it works on
     * every runtime, so the rule must leave it alone. Reporting it would mean reporting the remedy
     * along with the mistake.
     */
    public static RandomGenerator theBestOneThisRuntimeActuallyHas(long seed) {
        return RandomGeneratorFactory.all()
                .filter(RandomGeneratorFactory::isStatistical)
                .findFirst()
                .map(available -> available.create(seed))
                .orElseGet(() -> new SplittableRandom(seed));
    }
}
