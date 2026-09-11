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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that the architecture rules can actually see every module they claim to govern.
 *
 * <p>{@link ProductionArchitectureTest} imports {@code io.restest} from the class path, which at
 * test time is the nine jars named as dependencies in this module's POM. Nothing else verifies that
 * the list is complete. Drop one of those dependencies, mis-scope it, or trim a class out of a jar,
 * and the import simply comes back smaller: the rules scoped to that module have an empty subject
 * set and pass, {@code ensureAllClassesAreContainedInArchitecture} cannot report a class that was
 * never imported, and {@link ArchitectureRulesSelfTest} never touches the production import at all.
 * The module would leave every architecture rule silently and permanently, with a green build.
 *
 * <p>So this test compares the two things the harness knows independently: how many classes each
 * module compiled into {@code target/classes}, and how many of them the import actually contains.
 * Counts rather than presence, because "at least one class arrived" would pass a module that
 * compiled five hundred and shipped one - a jar-plugin exclude, a shading step or a mis-set source
 * directory drops a subpackage, and that subpackage leaves every rule unnoticed.
 *
 * <p>What it catches, verified by breaking each on purpose: a module whose classes never reached its
 * jar, and a module that was not built. What it does not catch is one of the nine dependencies being
 * deleted from this module's POM outright - {@code restest-cli} depends on the other eight, so they
 * arrive transitively and the import stays complete. That is luck rather than design, and it would
 * stop being true if the CLI's dependencies ever narrowed, which is a further reason to check the
 * import against the disk rather than trusting the POM.
 */
class HarnessCoverageTest {

    @Test
    @DisplayName("every class the modules compiled is visible to the architecture rules")
    void every_compiled_class_is_visible_to_the_rules() {
        Map<String, Long> compiled = compiledClassesPerModule();

        // TODO(M1.1): this assumption stops holding with the first production class. Until then the
        // comparison has nothing to compare, and passing an empty list against an empty list would
        // look like evidence that the rules see every module - while this test's body had never run.
        Assumptions.assumeFalse(compiled.isEmpty(),
                "No module has compiled any class beyond its module descriptor yet, so there is "
                        + "nothing to compare. Skipping rather than asserting emptiness against "
                        + "emptiness.");

        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        List<String> shortfalls = new ArrayList<>();
        compiled.forEach((module, onDisk) -> {
            long visible = importedClassesUnder(imported, packageOf(module));
            if (visible < onDisk) {
                shortfalls.add(module + ": " + onDisk + " compiled, " + visible
                        + " visible to the rules");
            }
        });

        assertThat(shortfalls)
                .describedAs("Classes these modules compiled are not reachable by the architecture "
                        + "rules, so no rule can constrain them. The rules read the module jars, so "
                        + "the usual causes are a jar-plugin exclude, a shading or multi-release "
                        + "step, or a missing dependency in restest-arch-tests/pom.xml")
                .isEmpty();
    }

    private static final String ROOT = "io.restest";

    /**
     * How many classes each production module compiled, excluding module descriptors and modules
     * with none. Read from {@code target/classes} - what {@code javac} produced - so that it is
     * independent of the jars the rules actually import.
     */
    private static Map<String, Long> compiledClassesPerModule() {
        Path root = RepositoryRoot.locate();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String module : RepositoryRoot.declaredModules()) {
            if (RepositoryRoot.VERIFICATION_ONLY_MODULES.contains(module)) {
                continue;
            }
            Path output = root.resolve(module).resolve("target/classes");
            if (!Files.isDirectory(output)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(output)) {
                long compiled = tree.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().equals("module-info.class"))
                        .count();
                if (compiled > 0) {
                    counts.put(module, compiled);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not walk " + output, e);
            }
        }
        return counts;
    }

    private static long importedClassesUnder(JavaClasses imported, String modulePackage) {
        return imported.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(modulePackage)
                        || javaClass.getPackageName().startsWith(modulePackage + "."))
                .count();
    }

    /**
     * {@code restest-core} governs {@code io.restest.core}, and so on.
     *
     * <p>The mapping does not hold for {@code restest-arch-tests}, whose package is
     * {@code io.restest.arch}. That module is excluded by name through
     * {@link RepositoryRoot#VERIFICATION_ONLY_MODULES} rather than left to this method, which would
     * otherwise look for {@code io.restest.arch.tests} and report a module it has no business
     * policing.
     */
    private static String packageOf(String module) {
        assertThat(module).startsWith("restest-");
        String name = module.substring("restest-".length());
        assertThat(name)
                .describedAs("a production module is expected to be restest-<name> mapping to "
                        + "io.restest.<name>. '%s' has more than one word, so the mapping is a "
                        + "guess - either exempt it through RepositoryRoot."
                        + "VERIFICATION_ONLY_MODULES or give it an explicit package here", module)
                .doesNotContain("-");
        return ROOT + "." + name;
    }
}
