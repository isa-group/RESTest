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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that every link between the repository's documents leads to a page and a heading that
 * exist.
 *
 * <p>The documents that explain RESTest - the README, the reference pages on the command line, the
 * report, the plan and the settings, the manual, the design and its decisions - send the reader from
 * one to another, often to one heading of a long page. A heading renamed in one document breaks every
 * link to it from the others, and nothing on the page that moved says so: the reader lands at the
 * top of the right page, or on no page at all, and gives up.
 *
 * <p>So every Markdown file in the repository is read, every link to another file of the repository
 * is followed, and a link that ends in a heading's name has to name a heading that page has, spelt
 * the way GitHub spells it in the address. Links to other sites are left alone: whether they still
 * answer is not something a build can promise.
 */
class DocumentedLinksTest {

    /** A link written the Markdown way, an image included: its target, up to the closing bracket. */
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");

    /** A target that names a scheme - {@code https:}, {@code mailto:} - and so leaves the repository. */
    private static final Pattern ANOTHER_SITE = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");

    /** A heading, one to six hashes and a space. */
    private static final Pattern HEADING = Pattern.compile("^#{1,6} (.*)$");

    /** Directories whose Markdown is not the repository's own: what a build or a tool leaves behind. */
    private static final Set<String> NOT_OURS = Set.of("target", "node_modules", ".git", ".claude");

    @Test
    @DisplayName("every link between the repository's documents leads to a page and a heading that "
            + "exist")
    void every_link_leads_somewhere() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> pages = pages(root);
        assertThat(pages)
                .describedAs("the README at least should have been found under %s", root)
                .contains(root.resolve("README.md"));

        Map<Path, Set<String>> headings = new HashMap<>();
        List<String> broken = new ArrayList<>();
        for (Path page : pages) {
            for (String target : linksIn(page)) {
                if (ANOTHER_SITE.matcher(target).matches()) {
                    continue;
                }
                int hash = target.indexOf('#');
                String file = hash < 0 ? target : target.substring(0, hash);
                Path resolved = file.isEmpty() ? page : page.getParent().resolve(file).normalize();
                if (!Files.exists(resolved)) {
                    broken.add(root.relativize(page) + " links to " + target
                            + ", which is not in the repository");
                } else if (hash >= 0 && resolved.toString().endsWith(".md")
                        && !headings.computeIfAbsent(resolved, DocumentedLinksTest::anchorsOf)
                                .contains(target.substring(hash + 1))) {
                    broken.add(root.relativize(page) + " links to " + target + ", and "
                            + root.relativize(resolved) + " has no heading by that name");
                }
            }
        }
        assertThat(broken).describedAs("links that lead nowhere").isEmpty();
    }

    /** Every Markdown file of the repository's own. */
    private static List<Path> pages(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(file -> file.toString().endsWith(".md"))
                    .filter(file -> {
                        for (Path part : root.relativize(file)) {
                            if (NOT_OURS.contains(part.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
    }

    /** The targets of a page's links, leaving out what is shown as code rather than as a link. */
    private static List<String> linksIn(Path page) {
        List<String> targets = new ArrayList<>();
        boolean inCode = false;
        for (String line : lines(page)) {
            if (line.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                continue;
            }
            Matcher link = LINK.matcher(line);
            while (link.find()) {
                targets.add(link.group(1));
            }
        }
        return targets;
    }

    /**
     * The names GitHub gives a page's headings, which is what a link to one of them ends with: the
     * heading's words in lower case, punctuation taken out and spaces turned into hyphens, with a
     * number after any heading that has the same name as one before it.
     */
    private static Set<String> anchorsOf(Path page) {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();
        boolean inCode = false;
        for (String line : lines(page)) {
            if (line.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (inCode || !heading.matches()) {
                continue;
            }
            String anchor = heading.group(1).trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{N} _-]", "")
                    .replace(' ', '-');
            int times = seen.merge(anchor, 1, Integer::sum);
            anchors.add(times == 1 ? anchor : anchor + "-" + (times - 1));
        }
        return anchors;
    }

    private static List<String> lines(Path page) {
        return RepositoryRoot.read(page).replace("\r\n", "\n").lines().toList();
    }
}
