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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariants that live below the level of bytecode, so ArchUnit cannot see them.
 *
 * <p>ArchUnit reasons about compiled classes. Three of the project's rules are about text: a name
 * that must never appear in the source tree, a file every module must declare, and a pin every
 * workflow step must carry. None of those becomes a class, so each is checked by reading the
 * repository. {@link RepositoryRoot} finds the checkout.
 */
class SourceTreeRulesTest {

    /**
     * ADR-0011 and the hard rules in CLAUDE.md: the benchmark platform lives in {@code evaluation/},
     * which is not a Maven module, and the tool must know nothing about it. CLAUDE.md phrases the
     * rule as a search over {@code src/} that must return zero hits, which is what this test runs.
     *
     * <p>Checked as text rather than as a dependency because the danger is not only a compiled
     * reference. A path, a property name or a comment that assumes the harness's layout couples the
     * tool to it just as effectively, and no bytecode rule would see any of them. The POM files are
     * scanned for the same reason: a Maven property pinning the harness's commit would couple the
     * build to it while leaving every source file clean.
     *
     * <p>{@code evaluation/} is excluded, and must be. ADR-0011 puts the harness there, so naming it
     * inside that directory is the intended state rather than a violation - a rule that failed on
     * ADR-sanctioned work would be weakened by the first person to hit it.
     */
    @Test
    @DisplayName("no benchmark platform is referenced anywhere under src (ADR-0011)")
    void no_benchmark_platform_under_src() {
        List<String> offenders = new ArrayList<>();
        for (Path file : toolFiles()) {
            String content = readIfText(file);
            if (content != null && content.toLowerCase(Locale.ROOT).contains(BENCHMARK_PLATFORM)) {
                offenders.add(RepositoryRoot.locate().relativize(file).toString());
            }
        }
        assertThat(offenders)
                .describedAs("ADR-0011 keeps the benchmark platform out of the tool entirely. "
                        + "These files under src/ mention it; they belong in evaluation/")
                .isEmpty();
    }

    /**
     * ADR-0004: "Nine Maven modules, each with a module-info.java." The declaration is what makes
     * the boundary compiled rather than conventional, so losing one silently turns a module back
     * into a package.
     *
     * <p>The module list is read from the aggregator POM rather than hard-coded, so a module added
     * later is covered without anyone remembering to extend this test.
     */
    @Test
    @DisplayName("every production module declares a module-info.java (ADR-0004)")
    void every_production_module_declares_a_module_info() {
        Path root = RepositoryRoot.locate();
        List<String> missing = new ArrayList<>();
        for (String module : RepositoryRoot.declaredModules()) {
            if (RepositoryRoot.VERIFICATION_ONLY_MODULES.contains(module)) {
                continue;
            }
            if (!Files.isRegularFile(root.resolve(module).resolve("src/main/java/module-info.java"))) {
                missing.add(module);
            }
        }
        assertThat(missing)
                .describedAs("A module without a module-info.java is a package with extra steps. "
                        + "Modules exempt from this rule, because they have no main sources: %s",
                        RepositoryRoot.VERIFICATION_ONLY_MODULES)
                .isEmpty();
    }

    /**
     * Supply-chain hygiene, and the reason M0.2 lists it: a tag is a moving pointer. {@code @v5} can
     * be repointed at any commit by whoever owns the action, so a workflow pinned to a tag runs
     * whatever that account decides to publish tomorrow. A 40-character commit SHA cannot move.
     *
     * <p>Dependabot updates these pins, which is why the version belongs in a trailing comment: it
     * is what lets Dependabot tell a v5 pin from a v6 one. That comment is checked here too - both
     * {@code docs/ci.md} and {@code .github/dependabot.yml} call it required, and a requirement
     * nothing enforces is a preference.
     *
     * <p>Composite actions under {@code .github/actions} are scanned as well as the workflows. They
     * are the usual next step once a workflow repeats itself, and a pin hidden in one would
     * otherwise escape.
     */
    @Test
    @DisplayName("every workflow action is pinned to a commit SHA")
    void every_workflow_action_is_pinned_to_a_commit_sha() {
        List<String> unpinned = new ArrayList<>();
        List<String> undocumented = new ArrayList<>();
        int pins = 0;

        for (Path definition : actionDefinitions()) {
            Matcher matcher = USES.matcher(RepositoryRoot.read(definition));
            while (matcher.find()) {
                String reference = matcher.group(1).trim();
                if (reference.startsWith("./")) {
                    // A local action in this repository moves with the commit that uses it.
                    continue;
                }
                pins++;
                if (!PINNED.matcher(reference).matches()) {
                    unpinned.add(definition.getFileName() + ": " + reference);
                } else if (!VERSION_COMMENT.matcher(matcher.group(2)).find()) {
                    undocumented.add(definition.getFileName() + ": " + reference);
                }
            }
        }

        assertThat(unpinned)
                .describedAs("A tag can be repointed at any commit by whoever owns the action; a "
                        + "commit SHA cannot. Pin these as owner/repo@<40 hex>")
                .isEmpty();
        assertThat(undocumented)
                .describedAs("A bare SHA tells a reader and Dependabot nothing about which release "
                        + "it is. Add the version as a trailing comment, as in "
                        + "'uses: actions/checkout@<sha> # v7.0.1'")
                .isEmpty();
        assertThat(pins)
                .describedAs("No external action pins were found at all, so this rule checked "
                        + "nothing. Either the workflows stopped using actions, or the pattern "
                        + "matching 'uses:' has stopped matching")
                .isPositive();
    }

    /**
     * The forbidden name, split so that this file does not match its own rule. The alternative was
     * to exempt this source file, which would have left a hole in a rule whose whole value is that
     * it has none.
     */
    private static final String BENCHMARK_PLATFORM = "rest" + "gym";

    private static final Pattern USES =
            Pattern.compile("^[ \\t]*-?[ \\t]*uses:[ \\t]*(\\S+)([^\\n]*)$", Pattern.MULTILINE);

    private static final Pattern PINNED =
            Pattern.compile("[\\w.-]+/[\\w.-]+(?:/[\\w.-]+)*@[0-9a-f]{40}");

    /** The trailing '# v1.2.3' that tells Dependabot, and a reader, which release the SHA is. */
    private static final Pattern VERSION_COMMENT = Pattern.compile("#\\s*v?\\d");

    /**
     * Everything that makes up the tool: the source trees of the declared modules, the repository's
     * own {@code src/} (which still carries the 1.x test corpus), and every POM.
     *
     * <p>Deliberately not a walk of the whole repository. That would descend into {@code .git} and
     * {@code target}, making the cost scale with history rather than with source, and it would
     * sweep up {@code evaluation/}, where naming the harness is correct.
     */
    private static List<Path> toolFiles() {
        Path root = RepositoryRoot.locate();
        List<Path> roots = new ArrayList<>();
        roots.add(root.resolve("src"));
        for (String module : RepositoryRoot.declaredModules()) {
            roots.add(root.resolve(module).resolve("src"));
        }

        List<Path> files = new ArrayList<>();
        for (Path sourceTree : roots) {
            if (!Files.isDirectory(sourceTree)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(sourceTree)) {
                tree.filter(Files::isRegularFile).forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not walk " + sourceTree, e);
            }
        }

        files.add(root.resolve("pom.xml"));
        for (String module : RepositoryRoot.declaredModules()) {
            Path pom = root.resolve(module).resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                files.add(pom);
            }
        }
        return files;
    }

    /** Every workflow, and every composite action definition under {@code .github}. */
    private static List<Path> actionDefinitions() {
        Path github = RepositoryRoot.locate().resolve(".github");
        assertThat(github.resolve("workflows"))
                .describedAs("M0.2 introduced the workflow directory; if it is gone, continuous "
                        + "integration is gone with it")
                .isDirectory();
        try (Stream<Path> tree = Files.walk(github)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml")
                            || path.toString().endsWith(".yaml"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + github, e);
        }
    }

    /** The file's content, or {@code null} if it is not valid UTF-8 text (the corpus holds images). */
    private static String readIfText(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            return decoded.indexOf(0) >= 0 ? null : decoded;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
