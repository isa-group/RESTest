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
 * A phase of a run, for {@link GenSwitchingOverAnEnum} to switch over.
 *
 * <p>A separate file on purpose. Switching over an enum declared in the same compilation unit lets
 * {@code javac} compile straight to {@code ordinal()}, and no lookup table is generated at all -
 * which would make the fixture next door exercise nothing. An enum that can be recompiled
 * independently is the case that forces the indirection, and it is also the case every switch in
 * the production modules is.
 */
public enum GenPhase {
    PLANNING,
    SENDING,
    JUDGING
}
