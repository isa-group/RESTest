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
package io.restest.oracles;

import io.restest.core.oracle.Oracle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The rules RESTest applies to what an API sends back.
 *
 * <p>Two ways of getting hold of them. {@link #standard()} is the list RESTest ships with, in a
 * fixed order, which is what a run uses unless told otherwise. {@link #discovered()} goes looking,
 * and finds anything anybody has added - including rules from somebody else's code sitting beside
 * RESTest's own, with nothing here naming them and nothing needing to be rebuilt.
 *
 * <p>That second one is the point of the arrangement. Adding a new kind of check to this tool is
 * meant to be one class and no changes anywhere else, and the only honest way to claim that is to
 * have the tool actually find its own rules that way.
 */
public final class Oracles {

    private Oracles() {
    }

    /** The rules RESTest ships with, in a fixed order so that two runs report in the same order. */
    public static List<Oracle> standard() {
        return List.of(new ServerErrorOracle(), new ResponseSchemaOracle());
    }

    /**
     * Every rule that can be found, RESTest's own and anybody else's, ordered by name so that the
     * result does not depend on the order things happen to be found in.
     */
    public static List<Oracle> discovered() {
        List<Oracle> found = new ArrayList<>();
        ServiceLoader.load(Oracle.class).forEach(found::add);
        found.sort(Comparator.comparing(Oracle::name));
        return List.copyOf(found);
    }
}
