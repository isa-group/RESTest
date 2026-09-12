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
 * Applies every rule in {@link ArchitectureRules} to the nine production modules.
 *
 * <p>Tests are excluded from the import, so the deliberate violations under
 * {@code io.restest.arch.fixtures} - which compile to {@code target/test-classes} - are invisible
 * here. {@link ArchitectureRulesSelfTest} is where those are used.
 *
 * <p>Until M1.1a the production modules contained nothing but {@code module-info.java}, and each
 * check here skipped with a stated reason rather than passing over an empty set - a green build that
 * guards nothing is the failure mode ADR-0004 exists to prevent. The domain model has since arrived,
 * so every check now runs against real classes and the skips are gone. What proved the rules worked
 * in the meantime, and still proves that each of them reports what it claims to, is
 * {@link ArchitectureRulesSelfTest}.
 *
 * <p>{@link HarnessCoverageTest} is what keeps this test honest from here on: it compares the
 * classes each module compiled against the classes this import actually contains, so a module that
 * quietly leaves the import - a dropped dependency, a mis-scoped jar - fails there instead of
 * silently shrinking the subject set here.
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
     * <p>{@code allowEmptyShould} stays on for a reason that outlasted M0.2: the modules fill in
     * across different milestones, so a rule scoped to one of them - the parser confinement rule
     * now that {@code restest-core} holds classes but {@code restest-spec} does not yet -
     * legitimately has an empty subject set for a while. The case it must not excuse is every rule
     * being empty at once, which stopped being possible when the domain model arrived: the
     * inward-dependency rule and the no-network rule both have subjects now, and
     * {@link HarnessCoverageTest} fails if the import ever loses a module.
     */
    private static void check(ArchRule rule) {
        rule.allowEmptyShould(true).check(productionClasses);
    }
}
