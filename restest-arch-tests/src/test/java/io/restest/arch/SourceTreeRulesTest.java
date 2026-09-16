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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Invariants that live below the level of bytecode, so ArchUnit cannot see them.
 *
 * <p>ArchUnit reasons about compiled classes. Several of the project's rules are about text
 * instead: a name that must never appear in the repository, a file every module must declare, a pin
 * every workflow step must carry, and which modules may read another module's code in their tests.
 * None of those becomes a class, so each is checked by reading the repository. {@link
 * RepositoryRoot} finds the checkout.
 */
class SourceTreeRulesTest {

    /**
     * Modules allowed to use another module's code in their own tests, and why.
     *
     * <p>The rule that dependencies point inwards is checked on compiled production code, which
     * leaves test code free. That freedom is useful - a test proving that generation works against
     * real specifications has to read real specifications, and reading them is the specification
     * module's job - and it is also how a boundary quietly stops existing, one reasonable exception
     * at a time. Naming the exceptions here keeps each one a decision somebody made rather than a
     * habit nobody noticed.
     */
    private static final Map<String, String> MAY_USE_THE_PARSER_IN_TESTS = Map.of(
            "restest-gen", "its generation tests run against the five real specifications the tool "
                    + "is measured on, and hand-written models would only ever test the shapes the "
                    + "same person thought of");

    @Test
    @DisplayName("only the modules named here use the specification parser in their tests")
    void the_parser_is_used_in_tests_only_where_it_was_agreed() {
        List<String> unexpected = new ArrayList<>();
        for (String module : RepositoryRoot.declaredModules()) {
            if (module.equals("restest-spec") || module.equals("restest-cli")
                    || RepositoryRoot.VERIFICATION_ONLY_MODULES.contains(module)
                    || MAY_USE_THE_PARSER_IN_TESTS.containsKey(module)) {
                continue;
            }
            Path pom = RepositoryRoot.locate().resolve(module).resolve("pom.xml");
            String content = readIfText(pom);
            if (content != null && content.contains("restest-spec")) {
                unexpected.add(module);
            }
        }

        assertThat(unexpected)
                .describedAs("a module depending on the specification parser, even only for its "
                        + "tests, is an exception to the module boundaries and belongs in the list "
                        + "in this test with the reason for it")
                .isEmpty();
    }

    /**
     * The files allowed to name the benchmark platform RESTest is measured on, and nothing else in
     * the repository may.
     *
     * <p>These five explain the relationship - what the platform is, why the project is measured on
     * it, and where the code that drives it lives. Every one of them is prose written for a person.
     * Anywhere else, the name is the first sign of the coupling this project has decided not to
     * have.
     */
    private static final List<String> BENCHMARK_MAY_BE_NAMED_IN = List.of(
            "CLAUDE.md",
            "ROADMAP.md",
            "docs/DESIGN.md",
            "docs/adr/0011-evaluation-harness.md",
            ".claude/agents/reviewer.md");

    /**
     * The platform RESTest is measured against lives in a repository of its own, and this one must
     * know nothing about it. This test searches every file the repository contains and fails if the
     * platform's name appears outside the handful of documents that explain the relationship.
     *
     * <p>Checked as text rather than as a compiled dependency, because the danger is not only a
     * compiled reference. A path, a property name, a build property pinning the platform's version
     * or a comment that assumes its layout couples the two just as effectively, and no rule written
     * against bytecode would see any of them.
     *
     * <p>The whole repository rather than the source trees alone: with the harness that drives the
     * platform kept in a repository of its own, there is no longer any directory here where naming
     * it would be correct, so the rule no longer needs an exception the size of a directory.
     */
    @Test
    @DisplayName("no benchmark platform is named outside the documents that explain it (ADR-0011)")
    void no_benchmark_platform_in_the_repository() {
        Path root = RepositoryRoot.locate();
        assertThat(benchmarkMentions(root, filesOfTheRepository()))
                .describedAs("ADR-0011 keeps the benchmark platform out of this repository "
                        + "entirely, not merely out of the build. These files name it; whatever "
                        + "they were doing with it belongs in the evaluation repository")
                .isEmpty();
    }

    /**
     * Which of these files name the benchmark platform where they may not, written as a function of
     * the files rather than of the repository so that the rule's own test can hand it a directory
     * built to break it.
     *
     * <p>A file's name counts as much as its contents. A directory called after the platform,
     * holding an adapter that never spells the name out, would couple the two just as effectively
     * and would read to anybody opening the repository as though the platform belonged here.
     */
    static List<String> benchmarkMentions(Path root, List<Path> files) {
        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            String path = relativePath(root, file);
            if (BENCHMARK_MAY_BE_NAMED_IN.contains(path)) {
                continue;
            }
            String content = readIfText(file);
            boolean named = path.toLowerCase(Locale.ROOT).contains(BENCHMARK_PLATFORM)
                    || (content != null && content.toLowerCase(Locale.ROOT).contains(BENCHMARK_PLATFORM));
            if (named) {
                offenders.add(path);
            }
        }
        return offenders;
    }

    /**
     * The rule above is the one thing this increment added, and a rule nobody has watched fail is a
     * rule nobody knows works. This gives it a small repository of its own: an innocent file, a file
     * that names the platform, a directory named after the platform holding a file that never spells
     * it out, and a document that is allowed to name it.
     */
    @Test
    @DisplayName("the rule reports a file that names the benchmark, and a directory named after it")
    void the_benchmark_rule_reports_what_it_exists_to_report(@TempDir Path root) throws IOException {
        String platform = BENCHMARK_PLATFORM;
        String shouted = platform.toUpperCase(Locale.ROOT);

        Files.writeString(root.resolve("innocent.txt"), "a file with nothing to hide");
        Files.writeString(root.resolve("guilty.txt"), "pinned at " + platform + " abc123");
        // Spelled the way every document in this project spells it, which is not how it is written
        // in the rule: a mention only counts as one if the reader would count it.
        Files.writeString(root.resolve("shouting.md"), "measured on " + shouted);
        Path named = Files.createDirectory(root.resolve(platform + "-harness"));
        Files.writeString(named.resolve("Dockerfile"), "FROM eclipse-temurin:21-jre");
        Path submodule = Files.createDirectory(root.resolve(shouted + "-tool"));
        Files.writeString(root.resolve("CLAUDE.md"), "measured on " + platform);

        List<Path> files = List.of(
                root.resolve("innocent.txt"),
                root.resolve("guilty.txt"),
                root.resolve("shouting.md"),
                named.resolve("Dockerfile"),
                submodule,
                root.resolve("CLAUDE.md"));

        assertThat(benchmarkMentions(root, files))
                .describedAs("a file naming the platform in any case, a file inside a directory "
                        + "named after it, and a directory named after it with nothing to read - a "
                        + "submodule is one - are all violations; an exempt document is not")
                .containsExactlyInAnyOrder(
                        "guilty.txt",
                        "shouting.md",
                        platform + "-harness/Dockerfile",
                        shouted + "-tool");
    }

    /**
     * The rule is only as good as the list of files it is given, and that list is assembled from
     * two places that can each fall silent: git, which may not be there to ask, and a walk of the
     * source trees. This checks that what comes back still covers what the rule was written to
     * cover - every build file, and the code itself.
     */
    @Test
    @DisplayName("the files the rules read cover every build file and every module's source")
    void the_enumeration_covers_the_build_files_and_the_source_trees() {
        Path root = RepositoryRoot.locate();
        List<String> paths = filesOfTheRepository().stream()
                .map(file -> relativePath(root, file))
                .toList();

        List<String> expected = new ArrayList<>();
        expected.add("pom.xml");
        for (String module : RepositoryRoot.declaredModules()) {
            expected.add(module + "/pom.xml");
        }

        assertThat(paths)
                .describedAs("a build property pinning something this project must not depend on "
                        + "would sit in one of these, so losing them loses the rule's whole point")
                .containsAll(expected);

        for (String module : RepositoryRoot.declaredModules()) {
            assertThat(paths)
                    .describedAs("no file from %s/src was read", module)
                    .anyMatch(path -> path.startsWith(module + "/src/"));
        }

        assertThat(paths)
                .describedAs("build output is not part of the repository and reading it would make "
                        + "every rule's cost scale with the size of the last build")
                .noneMatch(path -> path.startsWith("target/") || path.contains("/target/"));
    }

    /**
     * An exemption nobody needs any more is an exemption nobody is watching, so a file on the list
     * that no longer names the platform comes off the list.
     *
     * <p>A file that has been deleted is not a failure here: its exemption matches nothing and
     * permits nothing, so leaving the build red over it would only punish ordinary housekeeping. A
     * file that still exists and no longer names the platform is different - its exemption is live,
     * and it now covers everything that file might become.
     */
    @Test
    @DisplayName("every document allowed to name the benchmark platform still needs the permission")
    void the_documents_allowed_to_name_the_benchmark_still_name_it() {
        Path root = RepositoryRoot.locate();
        List<String> stale = new ArrayList<>();
        for (String path : BENCHMARK_MAY_BE_NAMED_IN) {
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String content = readIfText(file);
            if (content == null || !content.toLowerCase(Locale.ROOT).contains(BENCHMARK_PLATFORM)) {
                stale.add(path);
            }
        }
        assertThat(stale)
                .describedAs("These files are exempt from the rule above and no longer need to be: "
                        + "they no longer name the platform. Drop them from the list, so the "
                        + "exemption stays as small as the reason for it")
                .isEmpty();
    }

    /**
     * Every production module has a {@code module-info.java}. That file is what makes each module a
     * real, enforced boundary rather than just a naming convention, so silently losing one would turn
     * a module back into an ordinary package.
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
     * Supply-chain hygiene: a tag is a moving pointer. {@code @v5} can be repointed at any commit by
     * whoever owns the action, so a workflow pinned to a tag runs whatever that account decides to
     * publish tomorrow. A 40-character commit SHA cannot move.
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
     * Every file this repository holds: what git tracks, plus the build files and anything sitting
     * in a module's source tree whether git knows about it or not.
     *
     * <p>Two lists rather than one, because the two halves want opposite things. Across the whole
     * repository, git's list is the right answer: a walk of the working directory would sweep up
     * scratch checkouts, the output of a run and somebody's private notes, none of which are part of
     * the repository, and failing a build over a file nobody committed is how a rule teaches people
     * to distrust it. Inside a module's {@code src/}, the opposite: a file written and not yet
     * committed is exactly the one worth catching, on the machine where it was written, rather than
     * after it has been pushed.
     *
     * <p>Directories are kept, not only files. A submodule appears in git's list as its directory,
     * and a submodule is the most plausible way the benchmark would arrive here, since that is how
     * the benchmark itself takes on a tool. Its name is checked even though there is nothing to
     * read.
     */
    private static List<Path> filesOfTheRepository() {
        Path root = RepositoryRoot.locate();
        Set<Path> files = new LinkedHashSet<>();

        List<String> tracked = gitListing(root);
        if (tracked != null) {
            for (String name : tracked) {
                files.add(root.resolve(name));
            }
        } else if (!Files.exists(root.resolve(".git"))) {
            // No history here at all - an unpacked source release. Everything present was published
            // as the repository, so walking it reads exactly the repository and nothing else.
            collect(root, NOT_PART_OF_THE_REPOSITORY, files);
        }
        // The remaining case is a checkout whose git could not answer. Its working directory holds
        // things that are not part of the repository, so it is deliberately not walked: the scan
        // falls back to the source trees and the build files, which is the scope this rule had
        // before it was widened, rather than to inventing violations out of somebody's own files.

        files.add(root.resolve("pom.xml"));
        for (String module : RepositoryRoot.declaredModules()) {
            files.add(root.resolve(module).resolve("pom.xml"));
            collect(root.resolve(module).resolve("src"), List.of(), files);
        }

        files.removeIf(path -> !Files.exists(path));
        assertThat(files)
                .describedAs("no files were found in %s, so every rule that reads the repository "
                        + "would pass without checking anything", root)
                .isNotEmpty();
        return List.copyOf(files);
    }

    /**
     * Directories a walk of the repository must not descend into, and why each one is here. Applied
     * to the repository, not to a source tree: a fixture directory called {@code target} inside
     * {@code src/test/resources} is somebody's test data and is read like any other file.
     */
    private static final List<String> NOT_PART_OF_THE_REPOSITORY = List.of(
            ".git",        // history, not content, and larger than everything else together
            "target",      // build output
            "worktrees");  // checkouts of this same repository that other branches are edited in

    private static void collect(Path tree, List<String> excluded, Set<Path> files) {
        if (!Files.isDirectory(tree)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(tree)) {
            walk.filter(Files::isRegularFile)
                    // Relative to the tree being walked, never absolute: this checkout itself may
                    // well sit under a directory with one of these names, and matching on that
                    // would quietly skip everything there is to check.
                    .filter(file -> {
                        for (Path element : tree.relativize(file)) {
                            if (excluded.contains(element.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + tree, e);
        }
    }

    /**
     * The paths {@code git ls-files} reports, or {@code null} when git cannot answer.
     *
     * <p>Null rather than a failure, because the ways it can fail are legitimate: an unpacked source
     * release has no history to ask about, and a build image need not carry git at all. What the
     * caller does with a null depends on which of those it is.
     *
     * <p>Three details are deliberate. The ownership check is switched off for this one command,
     * because a checkout mounted into a container belongs, as far as git is concerned, to somebody
     * else, and refusing to list it would turn an ordinary containerised build into a failure of an
     * architecture rule. The output goes to a file rather than a pipe, so that a git which hangs is
     * cut off by the timeout instead of blocking a read that has no timeout of its own. And git's
     * own messages are discarded rather than merged into the listing, where a single warning would
     * glue itself to the first path and quietly drop that file from the scan.
     */
    private static List<String> gitListing(Path root) {
        Path listing = null;
        try {
            listing = Files.createTempFile("restest-tracked-files-", ".txt");
            Process git = new ProcessBuilder("git", "-c", "safe.directory=*", "ls-files", "-z")
                    .directory(root.toFile())
                    .redirectOutput(listing.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!git.waitFor(60, TimeUnit.SECONDS)) {
                git.destroyForcibly();
                return null;
            }
            if (git.exitValue() != 0) {
                return null;
            }
            String text = new String(Files.readAllBytes(listing), StandardCharsets.UTF_8);
            List<String> names = new ArrayList<>();
            for (String name : text.split("\0")) {
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing the files of " + root, e);
        } finally {
            if (listing != null) {
                try {
                    Files.deleteIfExists(listing);
                } catch (IOException ignored) {
                    // A temporary file the operating system will clear up. Not worth failing a rule.
                }
            }
        }
    }

    /** A file's path relative to a checkout, written the way git writes it on every platform. */
    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
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

    /**
     * The file's content, or {@code null} when there is no text to read: a directory - a submodule
     * is one - or a binary fixture.
     */
    private static String readIfText(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            return decoded.indexOf(0) >= 0 ? null : decoded;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
