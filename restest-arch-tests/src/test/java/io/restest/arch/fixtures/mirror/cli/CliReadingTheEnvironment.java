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
package io.restest.arch.fixtures.mirror.cli;

/**
 * Reads the environment from the module that is allowed to, so the rule is a boundary rather than a
 * blanket ban.
 *
 * <p>Gathering a run's settings from the environment is the command line's job and nobody else's.
 * A rule that reported this too would be forbidding the thing the design asks for, and a rule that
 * fails correct code gets switched off.
 */
public final class CliReadingTheEnvironment {

    private CliReadingTheEnvironment() {
    }

    /** What the environment says about one setting, gathered here and handed on. */
    public static String howManyRequestsAtOnce() {
        return System.getenv("RESTEST_ENGINE_MAX_CONCURRENCY");
    }
}
