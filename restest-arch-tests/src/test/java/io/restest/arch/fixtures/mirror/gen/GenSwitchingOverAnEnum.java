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
 * Breaks nothing, and is here to prove that {@code noStaticMutableState} agrees.
 *
 * <p>A switch over an enum from another class file makes the compiler generate a lookup table
 * nobody wrote, and which compiler built the class decides what that table looks like.
 * {@code javac} emits {@code static final int[] $SwitchMap$…} on a synthetic nested class - final,
 * and reported by nothing. The Eclipse compiler emits {@code private static volatile int[]
 * $SWITCH_TABLE$…} on the enclosing class itself, which is not final; that is what an IDE writes
 * into {@code target/classes} when it builds alongside Maven, and it is what made the rule fire on
 * {@code io.restest.core.model.ParameterStyle} at M1.1a.
 *
 * <p>So this fixture is the one that fails if the synthetic exclusion is removed - under a compiler
 * that emits the non-final form. Under {@code javac} the generated field is final and the
 * assertion holds either way, which {@code ArchitectureRulesSelfTest} says plainly rather than
 * claiming a guard it does not have on this toolchain.
 */
public final class GenSwitchingOverAnEnum {

    public String describe(GenPhase phase) {
        return switch (phase) {
            case PLANNING -> "planning";
            case SENDING -> "sending";
            case JUDGING -> "judging";
        };
    }
}
