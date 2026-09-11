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
package io.restest.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Applies every rule in {@link ArchitectureRules} to the nine production modules.
 *
 * <p>Tests are excluded from the import, so the deliberate violations under
 * {@code io.restest.arch.fixtures} - which compile to {@code target/test-classes} - are invisible
 * here. {@link ArchitectureRulesSelfTest} is where those are used.
 *
 * <p>At M0.2 the production modules contain nothing but {@code module-info.java}, so there is
 * nothing here to check yet. Rather than let the rules pass over an empty set - a green build that
 * guards nothing, which is the failure mode ADR-0004 exists to prevent - each check is skipped with
 * a stated reason. They begin running by themselves at M1.1, when the first classes arrive. What
 * proves the rules work in the meantime is {@link ArchitectureRulesSelfTest}.
 *
 * <p>The skip is per test, not in {@code @BeforeAll}. An assumption that fails during setup aborts
 * the whole container, and surefire then records {@code tests="0" skipped="0"} with the reason
 * nowhere in the XML - so five architecture checks would vanish from every report that reads it,
 * unexplained. Skipping each test individually keeps all five visible as skipped and attaches the
 * reason to each.
 */
class ProductionArchitectureTest {

    private static final String ROOT = "io.restest";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("dependencies point inwards, towards restest-core (ADR-0004)")
    void dependencies_point_inwards() {
        check(ArchitectureRules.dependenciesPointInwards(ROOT));
    }

    @Test
    @DisplayName("only restest-spec references the swagger parser (ADR-0007)")
    void only_restest_spec_references_the_swagger_parser() {
        check(ArchitectureRules.onlyOneModuleDependsOn(ROOT, "spec", "io.swagger.."));
    }

    @Test
    @DisplayName("only restest-cli terminates the process (ADR-0004)")
    void only_restest_cli_terminates_the_process() {
        check(ArchitectureRules.onlyOneModuleMayTerminateTheProcess(ROOT, "cli"));
    }

    @Test
    @DisplayName("no static mutable state anywhere (design principle 6)")
    void no_static_mutable_state() {
        check(ArchitectureRules.noStaticMutableState(ROOT));
    }

    @Test
    @DisplayName("restest-core holds no network dependency (ADR-0004)")
    void core_holds_no_network_dependency() {
        check(ArchitectureRules.moduleHoldsNoDependencyOn(
                ROOT, "core", "java.net..", "okhttp3..", "javax.net.."));
    }

    /**
     * Runs a rule against the production modules.
     *
     * <p>{@code allowEmptyShould} stays on for a reason that outlasts M0.2: the modules fill in
     * across different milestones, so a rule scoped to one of them - the parser confinement rule
     * once {@code restest-spec} exists but {@code restest-oracles} does not - legitimately has an
     * empty subject set for a while. The case it must not excuse is *every* rule being empty at
     * once, and the assumption immediately below is what rules that out.
     */
    private static void check(ArchRule rule) {
        // TODO(M1.1): this assumption stops holding as soon as the domain model lands, at which
        // point every check below starts running on its own and this call can be deleted.
        Assumptions.assumeFalse(productionClasses.isEmpty(),
                "No production classes under " + ROOT + " yet: every module holds only a "
                        + "module-info.java until M1.1. Skipping rather than passing over an empty "
                        + "set, which would prove nothing. The rules themselves are proven by "
                        + "ArchitectureRulesSelfTest.");

        rule.allowEmptyShould(true).check(productionClasses);
    }
}
