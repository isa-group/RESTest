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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the rules in {@link ArchitectureRules} report violations, rather than merely passing.
 *
 * <p>This is the test that earns the harness its keep at M0.2. The production modules are empty, so
 * {@link ProductionArchitectureTest} currently checks nothing - a rule that matches no classes
 * passes whether it is correct or broken. Here each rule is pointed at
 * {@code io.restest.arch.fixtures.mirror}, a miniature of the nine-module layout whose classes
 * break the rules on purpose, and the test asserts the rule fails and names the offender.
 *
 * <p>Without these assertions a typo in a package pattern would leave a permanently green build
 * guarding nothing, which is the exact failure mode ADR-0004 was written to prevent.
 */
class ArchitectureRulesSelfTest {

    private static final String MIRROR = "io.restest.arch.fixtures.mirror";

    private static JavaClasses mirrorClasses;

    @BeforeAll
    static void importMirrorClasses() {
        mirrorClasses = new ClassFileImporter().importPackages(MIRROR);
        assertThat(mirrorClasses)
                .describedAs("the mirror fixtures must be on the test class path, "
                        + "otherwise every assertion below would be vacuous")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the inward-dependency rule catches a core class reaching out to spec")
    void dependency_direction_rule_reports_an_outward_dependency() {
        expectViolation(
                ArchitectureRules.dependenciesPointInwards(MIRROR),
                "CoreReachingOutward");
    }

    @Test
    @DisplayName("the inward-dependency rule catches a class that belongs to no layer")
    void dependency_direction_rule_reports_a_class_outside_every_layer() {
        expectViolation(
                ArchitectureRules.dependenciesPointInwards(MIRROR),
                "StrayOutsideEveryLayer");
    }

    @Test
    @DisplayName("the confinement rule catches a module other than spec using the parser package")
    void confinement_rule_reports_a_dependency_from_the_wrong_module() {
        expectViolation(
                ArchitectureRules.onlyOneModuleDependsOn(MIRROR, "spec", "java.sql.."),
                "CoreUsingAForbiddenPackage");
    }

    @Test
    @DisplayName("the process-termination rule catches System.exit outside cli, and allows it inside")
    void termination_rule_reports_an_exit_outside_the_command_line_module() {
        expectViolation(
                ArchitectureRules.onlyOneModuleMayTerminateTheProcess(MIRROR, "cli"),
                "GenTerminatingTheProcess");

        // System::exit compiles to a method reference, which ArchUnit models separately from a
        // call. A rule using callMethod alone reports the line above and silently passes this one.
        expectViolation(
                ArchitectureRules.onlyOneModuleMayTerminateTheProcess(MIRROR, "cli"),
                "ExecTerminatingByMethodReference");

        // Neither System nor Runtime is named here, yet the JVM goes down just the same.
        expectViolation(
                ArchitectureRules.onlyOneModuleMayTerminateTheProcess(MIRROR, "cli"),
                "ExecTerminatingByProcessHandle");

        // Identical bytecode to the line above, but on a child process, which is legitimate and
        // which M6.2's out-of-process transport will need. Flagging it would fail correct code.
        assertThatThrownBy(() -> ArchitectureRules
                .onlyOneModuleMayTerminateTheProcess(MIRROR, "cli").check(mirrorClasses))
                .describedAs("reaping a child process must not be reported; only a handle on this "
                        + "JVM, obtained through ProcessHandle.current(), ends our own process")
                .hasMessageNotContaining("ExecReapingAChildProcess");

        assertThatThrownBy(() -> ArchitectureRules
                .onlyOneModuleMayTerminateTheProcess(MIRROR, "cli").check(mirrorClasses))
                .describedAs("the rule must permit the command-line module its System.exit, "
                        + "or it is a blanket ban rather than the boundary ADR-0004 describes")
                .hasMessageNotContaining("CliTerminatingTheProcess");
    }

    @Test
    @DisplayName("the static-state rule catches a writable static field")
    void static_state_rule_reports_a_non_final_static_field() {
        expectViolation(
                ArchitectureRules.noStaticMutableState(MIRROR),
                "requestsSoFar");
    }

    @Test
    @DisplayName("the no-network rule catches a forbidden package inside core")
    void forbidden_dependency_rule_reports_a_banned_package_inside_the_named_module() {
        expectViolation(
                ArchitectureRules.moduleHoldsNoDependencyOn(MIRROR, "core", "java.sql.."),
                "CoreUsingAForbiddenPackage");
    }

    private static void expectViolation(ArchRule rule, String expectedInMessage) {
        assertThatThrownBy(() -> rule.check(mirrorClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(expectedInMessage);
    }
}
