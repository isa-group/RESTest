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
package io.restest.core.oracle;

/**
 * One kind of thing that can be wrong with an API, named by a number everybody agrees on.
 *
 * <p>When a testing tool says it found forty faults, that number means nothing on its own: forty of
 * what, counted how? The Web Fuzzing Commons catalogue exists to answer that. It gives each kind of
 * fault a code and a name - {@code F100}, "HTTP Status 500" - and several tools use the same ones,
 * so a count from RESTest and a count from another tool are counting the same things and can
 * honestly be put side by side.
 *
 * <p>This is an interface rather than a plain list for one reason: the catalogue reserves the 900s
 * for codes a tool defines for itself, for the kinds of fault nobody has agreed a shared name for
 * yet. {@link WfcFault} is the published list; anything RESTest adds later takes its place beside
 * it without any of this having to change.
 */
public interface FaultCategory {

    /** The catalogue number, for example {@code 100}. */
    int code();

    /** What the catalogue calls this kind of fault, for example "HTTP Status 500". */
    String descriptiveName();

    /**
     * The name the catalogue suggests for a generated test that reproduces this fault, for example
     * {@code causes500_internalServerError}. RESTest does not generate test code as its way of
     * running tests, but it does export it as a report, and the exported name should be the one
     * other tools use.
     */
    String testCaseLabel();

    /** How the catalogue writes this category in one piece: {@code F100:HTTP Status 500}. */
    default String label() {
        return "F" + code() + ":" + descriptiveName();
    }
}
