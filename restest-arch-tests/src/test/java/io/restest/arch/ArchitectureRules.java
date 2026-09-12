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

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import java.util.List;
import java.util.stream.Stream;

/**
 * The architecture rules that ADR-0004 and the design principles in {@code CLAUDE.md} describe in
 * prose. This class holds the rules; {@link ProductionArchitectureTest} applies them to the real
 * modules and {@link ArchitectureRulesSelfTest} applies them to deliberate violations, proving each
 * rule actually reports what it claims to.
 *
 * <p>Every rule is a factory taking the root package it should reason about. Rules such as "only
 * {@code restest-spec} may see the parser" name module packages in their own text, so they can only
 * be exercised against fixtures if the fixtures live under a parallel package tree. The root
 * parameter is what makes that possible: production passes {@code io.restest}, the self-test passes
 * the mirror package under {@code io.restest.arch.fixtures}.
 */
final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /**
     * ADR-0004: "Dependencies point inwards, towards restest-core." Expressed as the exact graph the
     * module POMs declare, so a POM that grows an outward dependency fails here rather than being
     * noticed years later.
     *
     * <p>{@code withOptionalLayers} is required because the layers are empty until M1.1 introduces
     * the domain model. It permits a layer to have no classes; it does not permit a layer to be
     * accessed by someone who may not access it.
     *
     * <p>{@code ensureAllClassesAreContainedInArchitecture} closes the gap that pairing would
     * otherwise open. {@code consideringOnlyDependenciesInLayers} ignores dependencies of any class
     * that matches no layer, so a package outside the nine - {@code io.restest.model}, or
     * {@code io.restest.oracle} written in the singular - would have every one of its dependencies
     * unchecked while the build stayed green. Requiring every class to belong to a layer turns that
     * silent gap into a failure that names the package.
     *
     * <p>A consequence worth knowing before meeting it: this also rejects a class placed directly in
     * {@code io.restest}, a root {@code package-info} included, because that package is not one of
     * the nine modules. That is the intended reading of ADR-0004 - every class belongs to a module -
     * and not an oversight. If a genuine root-level class is ever wanted, add it through
     * {@code ensureAllClassesAreContainedInArchitectureIgnoring} deliberately rather than widening
     * a layer to accommodate it.
     */
    static ArchRule dependenciesPointInwards(String root) {
        LayeredArchitecture architecture = Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Core").definedBy(root + ".core..")
                .layer("Spec").definedBy(root + ".spec..")
                .layer("Idl").definedBy(root + ".idl..")
                .layer("Gen").definedBy(root + ".gen..")
                .layer("Exec").definedBy(root + ".exec..")
                .layer("Store").definedBy(root + ".store..")
                .layer("Oracles").definedBy(root + ".oracles..")
                .layer("Report").definedBy(root + ".report..")
                .layer("Cli").definedBy(root + ".cli..")

                .whereLayer("Cli").mayNotBeAccessedByAnyLayer()
                .whereLayer("Spec").mayOnlyBeAccessedByLayers("Cli")
                .whereLayer("Idl").mayOnlyBeAccessedByLayers("Gen", "Cli")
                .whereLayer("Gen").mayOnlyBeAccessedByLayers("Cli")
                .whereLayer("Exec").mayOnlyBeAccessedByLayers("Cli")
                .whereLayer("Store").mayOnlyBeAccessedByLayers("Cli")
                .whereLayer("Oracles").mayOnlyBeAccessedByLayers("Cli")
                .whereLayer("Report").mayOnlyBeAccessedByLayers("Cli")
                .whereLayer("Core").mayOnlyBeAccessedByLayers(
                        "Spec", "Idl", "Gen", "Exec", "Store", "Oracles", "Report", "Cli");

        return architecture
                .withOptionalLayers(true)
                .ensureAllClassesAreContainedInArchitecture()
                .as("dependencies point inwards, towards " + root + ".core (ADR-0004)");
    }

    /**
     * ADR-0007: the third-party specification parser is confined to one module, so replacing it is a
     * module-sized change. The forbidden package is a parameter so the self-test can point the same
     * rule at something it can actually depend on without dragging swagger-parser into this module.
     */
    static ArchRule onlyOneModuleDependsOn(String root, String module, String forbiddenPackage) {
        return noClasses()
                .that().resideOutsideOfPackage(root + "." + module + "..")
                .and().resideInAPackage(root + "..")
                .should().dependOnClassesThat().resideInAPackage(forbiddenPackage)
                .as("only " + root + "." + module + " depends on " + forbiddenPackage
                        + " (ADR-0007)");
    }

    /**
     * ADR-0004 and design principle 9: RESTest is usable as a library, so nothing but the
     * command-line module may end the JVM. A library that ends the process takes its host down
     * with it.
     */
    static ArchRule onlyOneModuleMayTerminateTheProcess(String root, String module) {
        return noClasses()
                .that().resideOutsideOfPackage(root + "." + module + "..")
                .and().resideInAPackage(root + "..")
                .should(TERMINATE_THE_PROCESS)
                .as("only " + root + "." + module + " terminates the process (ADR-0004)");
    }

    /**
     * Reaching a process-terminating method, whether by calling it or by referencing it.
     *
     * <p>Written as a condition rather than with {@code should().callMethod(...)} because ArchUnit
     * models a call and a method reference as different things, and {@code callMethod} sees only the
     * first. {@code System::exit} passed as an {@code IntConsumer} - a fatal-error handler, a
     * shutdown hook - ends the JVM exactly as thoroughly as {@code System.exit(1)} does, so both
     * shapes have to be covered or the rule guards only the obvious half. Each is exercised by its
     * own fixture in {@code ArchitectureRulesSelfTest}.
     *
     * <p>Not exhaustive, and knowingly so. {@code System.exit}, {@code Runtime.exit},
     * {@code Runtime.halt} and {@code ProcessHandle.destroy[Forcibly]} are covered - the last
     * because it is the modern way to reach for the same effect. Reaching any of them through
     * reflection or a {@code MethodHandle} is beyond what any bytecode rule can see, and is left to
     * code review rather than pretended away here.
     *
     * <p>The {@code ProcessHandle} clause is narrowed, for a reason that would otherwise surface as
     * a false positive in someone else's pull request. {@code processHandle.destroy()} reads
     * identically in bytecode whether the handle is this JVM's or a child process's: both are an
     * {@code invokeinterface} on {@code java.lang.ProcessHandle}. Killing a child is legitimate and
     * necessary - the out-of-process transport at M6.2 has to reap its helper - so flagging every
     * {@code destroy} would fail correct code, and a rule that fails correct code gets switched off.
     * The clause therefore fires only where the same class also calls {@code ProcessHandle.current()},
     * which is the only way to obtain a handle on this JVM. Both shapes have fixtures: one that
     * takes its own handle and destroys it, one that reaps a child.
     */
    private static final ArchCondition<JavaClass> TERMINATE_THE_PROCESS =
            new ArchCondition<>("terminate the process") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    List<JavaAccess<?>> accesses = Stream.<JavaAccess<?>>concat(
                                    item.getMethodCallsFromSelf().stream(),
                                    item.getMethodReferencesFromSelf().stream())
                            .toList();
                    boolean holdsOwnProcessHandle = accesses.stream()
                            .anyMatch(ArchitectureRules::takesThisProcessHandle);

                    accesses.stream()
                            .filter(access -> endsTheProcess(access, holdsOwnProcessHandle))
                            .forEach(access -> events.add(SimpleConditionEvent.satisfied(
                                    access, access.getDescription())));
                }
            };

    private static boolean endsTheProcess(JavaAccess<?> access, boolean holdsOwnProcessHandle) {
        String owner = access.getTargetOwner().getName();
        String method = access.getName();
        boolean onSystem = System.class.getName().equals(owner) && "exit".equals(method);
        boolean onRuntime = Runtime.class.getName().equals(owner)
                && ("exit".equals(method) || "halt".equals(method));
        boolean onProcessHandle = holdsOwnProcessHandle
                && ProcessHandle.class.getName().equals(owner)
                && ("destroy".equals(method) || "destroyForcibly".equals(method));
        return onSystem || onRuntime || onProcessHandle;
    }

    /** {@code ProcessHandle.current()}: the only way to get a handle on the running JVM. */
    private static boolean takesThisProcessHandle(JavaAccess<?> access) {
        return ProcessHandle.class.getName().equals(access.getTargetOwner().getName())
                && "current".equals(access.getName());
    }

    /**
     * Design principle 6: "No global mutable state. Two runs must coexist in one JVM." A static
     * field that one run can write is a channel through which it can corrupt another.
     *
     * <p>Synthetic fields are excluded, which narrows the rule to what a person can actually write.
     * The exclusion is not theoretical tidiness: it was added at M1.1a after the first production
     * enum switch arrived. A switch over an enum makes the compiler generate a lookup table, and the
     * two compilers this repository meets disagree about it. {@code javac} puts a
     * {@code static final int[] $SwitchMap$...} on a synthetic nested class - final, no violation.
     * The Eclipse compiler, which is what an IDE writes into {@code target/classes} when it builds
     * alongside Maven, puts a {@code private static volatile int[] $SWITCH_TABLE$...} on the enum
     * itself - not final, and reported.
     *
     * <p>The rule would therefore have failed for any contributor whose IDE compiled last, naming
     * their enum switch as a breach of design principle 6. Nothing about that report would have been
     * actionable: the field is not in the source, and no run can reach it. A rule that fails correct
     * code gets switched off, and then it guards nothing.
     *
     * <p>Nothing is lost by the exclusion. {@code synthetic} is a modifier only a compiler can set;
     * every static field written in Java source is still subject to the rule, which
     * {@code GenWithStaticMutableState} proves and {@code GenSwitchingOverAnEnum} keeps honest.
     */
    static ArchRule noStaticMutableState(String root) {
        return fields()
                .that().areStatic()
                .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
                .and().areDeclaredInClassesThat().resideInAPackage(root + "..")
                .should().beFinal()
                .as("no static mutable state under " + root + " (design principle 6)");
    }

    /*
     * TODO(M1.1b): extend the rule above to static *final* fields of a mutable type, which it cannot
     * currently see. `static final List<String> SEEN = new ArrayList<>()` has a final reference and
     * writable contents, so it passes the rule while remaining exactly the cross-run channel
     * design principle 6 forbids.
     *
     * It is not a one-line addition, which is why M0.2 does not attempt it. The declared type
     * cannot decide it: `new ArrayList<>()` and `List.of(...)` are both declared `List`, so a rule
     * reading only `field.getRawType()` either misses the first or falsely reports the second - and
     * a rule with false positives gets switched off, at which point it guards nothing. Doing it
     * properly means inspecting each class's static initializer for the constructor it calls.
     *
     * M1.1b introduced the first record holding a mutable component,
     * `io.restest.core.execution.Payload`'s `byte[]`, expecting that increment to be the occasion
     * for this TODO. It was not: the array is an *instance* component, so it was never within this
     * rule's reach in the first place - `noStaticMutableState` only ever looked at static fields.
     * `Payload` defends itself by convention instead (clone on the way in, clone on the way out,
     * `equals`/`hashCode` written by hand), checked by `PayloadTest`, not by ArchUnit; the ADR-0005
     * amendment records why no static-analysis rule can tell "copies the array" from "keeps the
     * reference" by reading a compact constructor. This TODO remains open, waiting for the first
     * `static final` field of a mutable type to actually appear.
     */

    /**
     * ADR-0004: "restest-core: domain model and interfaces. No network, no parser, no heavy
     * dependencies." The rule states the "no network" half, which is the one a well-meaning change
     * is most likely to breach.
     */
    static ArchRule moduleHoldsNoDependencyOn(String root, String module, String... forbidden) {
        DescribedPredicate<JavaClass> forbiddenPackages = resideInAnyPackage(forbidden);
        return noClasses()
                .that().resideInAPackage(root + "." + module + "..")
                .should().dependOnClassesThat(forbiddenPackages)
                .as(root + "." + module + " holds no dependency on " + String.join(", ", forbidden)
                        + " (ADR-0004)");
    }
}
