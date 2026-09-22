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

/**
 * Breaks the rule that only the command-line module reads the environment the tool was started in.
 *
 * <p>All three ways of asking are here, because a rule that saw only the obvious one would let the
 * other two through. Reading a setting at the point where it is needed, instead of being handed it,
 * is what this rule exists to stop: it makes the value global, invisible to anything that prints a
 * run's configuration, and shared between two runs in the same program.
 */
public final class GenReadingTheEnvironment {

    private GenReadingTheEnvironment() {
    }

    /** Asks the environment for a value the command line should have handed over. */
    public static String howManyRequestsAtOnce() {
        return System.getenv("RESTEST_ENGINE_MAX_CONCURRENCY");
    }

    /** The same mistake through a system property. */
    public static String howLongToWait() {
        return System.getProperty("restest.engine.readTimeout");
    }

    /** And the way round both of the above, if a rule named only those two. */
    public static Object everything() {
        return System.getProperties();
    }
}
