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
import io.restest.core.settings.CheckSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The rules RESTest applies to what an API sends back.
 *
 * <p>Two ways of getting hold of them. {@link #standard(CheckSettings)} is the list RESTest ships
 * with, in a fixed order, less any rule a run was told not to apply: that is what a run uses.
 * {@link #discovered()} goes looking, and finds anything anybody has added - including rules from
 * somebody else's code sitting beside RESTest's own, with nothing here naming them and nothing
 * needing to be rebuilt.
 *
 * <p>That second one is the point of the arrangement. Writing a new kind of check is meant to be
 * one class, and the only honest way to claim that is to have the tool actually find its own rules
 * that way. A rule RESTest itself ships takes one more step: a switch of its own among the
 * settings, so that a run can do without it, joined to the rule here.
 */
public final class Oracles {

    private Oracles() {
    }

    /**
     * The rules RESTest ships with that these settings leave switched on, in a fixed order so that
     * two runs report in the same order.
     *
     * <p>Each switch belongs to one rule, and that rule reports only the kind of fault the switch
     * is named for, so that turning one off never silences another kind with it. A rule switched
     * off is left out rather than applied and then ignored, so that turning off the costly one -
     * checking every reply against the document - saves what it costs.
     *
     * @param switches which checks are switched on
     * @return the rules to apply
     */
    public static List<Oracle> standard(CheckSettings switches) {
        List<Oracle> on = new ArrayList<>();
        if (switches.internalServerError()) {
            on.add(new ServerErrorOracle());
        }
        if (switches.replyOffSchema()) {
            on.add(new ResponseSchemaOracle());
        }
        return List.copyOf(on);
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
