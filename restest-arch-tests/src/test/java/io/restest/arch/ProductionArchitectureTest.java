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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Applies every rule in {@link ArchitectureRules} to the project's real, production code.
 *
 * <p>Test code is excluded from the import, so the small examples under
 * {@code io.restest.arch.fixtures}, which deliberately break these rules, are invisible here.
 * {@link ArchitectureRulesSelfTest} is where those are used instead, to prove the rules actually
 * report what they claim to.
 *
 * <p>{@link HarnessCoverageTest} keeps this test honest: it compares the classes each module
 * compiled against the classes this import actually contains, so a module that quietly drops out of
 * the import - a missing dependency, a misconfigured build - fails there instead of silently
 * shrinking what gets checked here.
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
     * <p>{@code allowEmptyShould} stays on because the modules fill in one at a time as the project
     * grows, so a rule scoped to a module that has no classes in it yet legitimately has nothing to
     * check for a while. What it must not excuse is every rule being empty at once - and
     * {@link HarnessCoverageTest} fails if the import ever silently loses a whole module.
     */
    private static void check(ArchRule rule) {
        rule.allowEmptyShould(true).check(productionClasses);
    }
}
