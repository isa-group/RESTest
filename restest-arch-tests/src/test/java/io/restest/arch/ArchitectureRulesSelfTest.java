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
 * Proves that the rules in {@link ArchitectureRules} actually report violations, rather than merely
 * passing.
 *
 * <p>A rule that matches no classes at all passes whether it is correctly written or broken, so
 * trusting {@link ProductionArchitectureTest} alone would not be enough. Here each rule is instead
 * pointed at {@code io.restest.arch.fixtures.mirror}, a miniature copy of the project's module
 * layout whose classes break the rules on purpose, and the test asserts that the rule fails and
 * names the offending class.
 *
 * <p>Without these assertions, a typo in a package pattern would leave a permanently green build
 * that was silently guarding nothing.
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
    @DisplayName("the static-state rule catches a writable static field, whatever its visibility")
    void static_state_rule_reports_a_non_final_static_field() {
        expectViolation(ArchitectureRules.noStaticMutableState(MIRROR), "requestsSoFar");
        // Three visibilities, so that narrowing the rule by modifier - which is how the synthetic
        // exclusion is written - cannot silence part of it without failing here.
        expectViolation(ArchitectureRules.noStaticMutableState(MIRROR), "failuresSoFar");
        expectViolation(ArchitectureRules.noStaticMutableState(MIRROR), "lastStatusCode");
    }

    @Test
    @DisplayName("the static-state rule ignores the table a compiler generates for an enum switch")
    void static_state_rule_ignores_a_compiler_generated_field() {
        assertThatThrownBy(() -> ArchitectureRules.noStaticMutableState(MIRROR)
                .check(mirrorClasses))
                .describedAs("a switch over an enum is not global mutable state, whoever compiled "
                        + "it. Under javac the generated table is final and this assertion is "
                        + "trivially true; under the Eclipse compiler, which an IDE may use to "
                        + "write the very class files being read here, it is a volatile non-final "
                        + "field and this is the assertion that fails if the synthetic exclusion "
                        + "is removed")
                .hasMessageNotContaining("GenSwitchingOverAnEnum")
                .hasMessageNotContaining("SWITCH_TABLE");
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
