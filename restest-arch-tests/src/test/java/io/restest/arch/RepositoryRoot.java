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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the repository checkout, for the rules that have to read files rather than bytecode.
 *
 * <p>Surefire's working directory is the module being tested, so the root is found by walking
 * upwards until a directory holds both the aggregator {@code pom.xml} and the {@code restest-core}
 * module. Failure is loud rather than skipped: a rule that quietly does nothing when it cannot find
 * the tree is worse than no rule at all.
 */
final class RepositoryRoot {

    private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<\\s]+)\\s*</module>");

    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private RepositoryRoot() {
    }

    /**
     * Modules that exist to verify the others: no {@code src/main}, never published, and therefore
     * exempt from the rules that are about production code.
     *
     * <p>Named here rather than in each test so the exemption is one decision on the record. Note
     * that {@code restest-arch-tests} also does not fit the {@code restest-<name>} to
     * {@code io.restest.<name>} mapping - its package is {@code io.restest.arch}, not
     * {@code io.restest.arch.tests} - which is a second reason not to leave it to be excluded by a
     * side effect of having no classes.
     */
    static final List<String> VERIFICATION_ONLY_MODULES = List.of("restest-arch-tests");

    /** The directory holding the aggregator POM. */
    static Path locate() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("restest-core"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not find the repository root above " + Path.of("").toAbsolutePath()
                        + ". The source-tree rules need the checkout to read.");
    }

    /**
     * The module names the aggregator POM declares, in declaration order.
     *
     * <p>Comments are stripped first. A module commented out to bisect a build is not in the
     * reactor, and treating it as declared makes one rule demand a file from a module that is not
     * being built while another silently skips it - two wrong answers from one cause.
     */
    static List<String> declaredModules() {
        String pom = XML_COMMENT.matcher(read(locate().resolve("pom.xml"))).replaceAll("");
        Matcher matcher = MODULE.matcher(pom);
        List<String> modules = new ArrayList<>();
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        if (modules.isEmpty()) {
            throw new IllegalStateException("The aggregator POM declares no modules, which cannot "
                    + "be right - the module regex has probably stopped matching.");
        }
        return List.copyOf(modules);
    }

    static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}
