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
package io.restest.core.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Unmodifiable copies that keep the order they were given.
 *
 * <p>{@link Map#copyOf} and {@link Set#copyOf} would be shorter, and they are wrong here.
 * Their iteration order is deliberately unspecified and, since JDK 9, randomised by a per-JVM salt:
 * the same specification read twice in two processes yields the same properties in a different
 * order. RESTest promises reproducible runs - a seed and a specification must produce the same
 * requests - and property order reaches the wire, through the order values are generated in and the
 * order members are written to a body. A collection whose order changes per JVM would make that
 * promise unkeepable for reasons no one could see.
 *
 * <p>{@link java.util.List#copyOf} has no such problem and is used directly at the call sites.
 *
 * <p>Not exported by {@code module-info.java}: this is our own plumbing, not part of the model.
 */
public final class Copies {

    private Copies() {
    }

    /** An unmodifiable copy of {@code source} in its iteration order, rejecting null keys and values. */
    public static <K, V> Map<K, V> orderedMap(Map<K, V> source, String what) {
        Objects.requireNonNull(source, what);
        LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Objects.requireNonNull(key, () -> "a key of " + what);
            Objects.requireNonNull(value, () -> "the value of " + key + " in " + what);
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    /** An unmodifiable copy of {@code source} in its iteration order, rejecting null elements. */
    public static <E> Set<E> orderedSet(Collection<E> source, String what) {
        Objects.requireNonNull(source, what);
        LinkedHashSet<E> copy = new LinkedHashSet<>();
        for (E element : source) {
            Objects.requireNonNull(element, () -> "an element of " + what);
            copy.add(element);
        }
        return Collections.unmodifiableSet(copy);
    }
}
