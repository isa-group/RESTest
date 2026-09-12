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
 * Violates {@code noStaticMutableState}: a static field one run can write to is a channel through
 * which it can corrupt another run happening at the same time.
 */
public final class GenWithStaticMutableState {

    static int requestsSoFar;

    /*
     * Three visibilities rather than one, so that the rule cannot be narrowed by modifier without
     * something failing. The synthetic exclusion added at M1.1a is the only exclusion the rule is
     * meant to have; another `doNotHaveModifier(...)` appended to it would silence one of these.
     */
    private static int failuresSoFar;

    public static int lastStatusCode;

    public void count(int statusCode) {
        requestsSoFar++;
        failuresSoFar += statusCode >= 500 ? 1 : 0;
        lastStatusCode = statusCode;
    }

    public int failures() {
        return failuresSoFar;
    }
}
