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

import static io.restest.arch.DocumentedLinksTest.LINK;
import static io.restest.arch.DocumentedLinksTest.lines;
import static io.restest.arch.DocumentedLinksTest.outsideCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps the documentation in the shape its website reads.
 *
 * <p>RESTest's documentation is also published as a website, built from the Markdown files of a
 * released version just as they are. The website's menu comes from the documentation's two indexes:
 * the numbered list of chapters in the manual's index, and one group for each section of the
 * documentation's index, whose table gives the group's pages. The website refuses to build a
 * version whose indexes it cannot read, that has a page the menu would not reach, or that has a
 * page with no title. A released version never changes, so its documentation could then never be
 * published.
 *
 * <p>So the rules the website builds by are checked here, on every change, while a mistake is still
 * cheap to put right:
 *
 * <ul>
 *   <li>the manual's index has a numbered list, and each of its items starts with a link to a page;
 *   <li>the documentation's index has sections, each with a table - a header, the line of dashes
 *       under it, and at least one row - and the first cell of each row links a page;
 *   <li>every page is an entry of one of the two indexes - a list item's first link, or the link in
 *       a row's first cell - but the two indexes themselves, the README and the decision records,
 *       which have an index of their own; a link anywhere else in an index is no entry;
 *   <li>every page has a title, a first line starting with {@code # } that has words in it, and
 *       none starts with front matter.
 * </ul>
 *
 * <p>A page is a Markdown file under {@code docs/}, or one of the README, the rules and the guide
 * to contributing at the root. Two rules are stricter than the website: every chapter in the
 * manual's folder is in the manual's own list, and each item of that list starts with its link.
 * Whether links and headings lead somewhere is {@link DocumentedLinksTest}'s job.
 */
class DocumentationSiteTest {

    private static final String DOCS_INDEX = "docs/README.md";

    private static final String MANUAL_INDEX = "docs/manual/README.md";

    /** The pages at the root of the repository, which the documentation's index links. */
    private static final List<String> ROOT_PAGES =
            List.of("README.md", "AGENTS.md", "CONTRIBUTING.md");

    /** An item of a numbered list, at the start of a line. */
    private static final Pattern ITEM = Pattern.compile("^\\d+[.)] (.*)$");

    /** A section of the documentation's index. */
    private static final Pattern SECTION = Pattern.compile("^## (.*)$");

    /** The line under a table's header: only pipes, dashes, colons and spaces. */
    private static final Pattern TABLE_RULE = Pattern.compile("^\\|?[\\s:|-]*-[\\s:|-]*$");

    /** What a title shows that is not words: images, HTML tags, marks of emphasis and of code. */
    private static final Pattern NOT_WORDS =
            Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)|<[^>]*>|[*_`\\s]");

    @Test
    @DisplayName("the documentation keeps the shape its website reads (ADR-0047)")
    void the_documentation_keeps_the_shape_its_website_reads() throws IOException {
        Path root = RepositoryRoot.locate();
        assertThat(pages(root))
                .describedAs("the pages of the documentation, under %s", root)
                .contains(DOCS_INDEX, MANUAL_INDEX, "README.md", "docs/command-line.md");
        assertThat(problems(root))
                .describedAs("what would stop the documentation's website from building this "
                        + "version")
                .isEmpty();
    }

    @Test
    @DisplayName("documentation in the shape the website reads has nothing reported")
    void documentation_in_shape_has_nothing_reported(@TempDir Path root) throws IOException {
        write(root, Map.of());
        assertThat(problems(root)).isEmpty();
    }

    @Test
    @DisplayName("every way the website would refuse a version is reported, naming the file")
    void every_way_the_website_would_refuse_a_version_is_reported(@TempDir Path folder)
            throws IOException {
        String faults = "docs/faults.md";
        String notListed = DOCS_INDEX + " lists no entry for " + faults;
        // Each case: the files changed from that small documentation, and what is then reported.
        List<Map.Entry<Map<String, String>, String>> cases = List.of(
                // A page that is no entry of either index, however an index mentions it.
                Map.entry(Map.of(faults, "# Faults\n"), notListed),
                Map.entry(Map.of(faults, "# Faults\n",
                                DOCS_INDEX, DOCS.replace("is read first",
                                        "is read before [the faults](faults.md)")),
                        notListed),
                Map.entry(Map.of(faults, "# Faults\n",
                                DOCS_INDEX, DOCS.replace("| Every option |",
                                        "| Every option, and [the faults](faults.md) |")),
                        notListed),
                Map.entry(Map.of(faults, "# Faults\n",
                                DOCS_INDEX, DOCS + "\n## Faults\n\n"
                                        + "| [The faults](faults.md) | What |\n|---|---|\n"
                                        + "| [The command line](command-line.md) | All |\n"),
                        notListed),
                Map.entry(Map.of(faults, "# Faults\n",
                                MANUAL_INDEX, MANUAL.replace("— a practice API.",
                                        "— a practice API, and [the faults](../faults.md).")),
                        notListed),
                Map.entry(Map.of(faults, "# Faults\n",
                                MANUAL_INDEX,
                                MANUAL + "\nAnd then:\n\n1. [The faults](../faults.md)\n"),
                        notListed),
                // The documentation's index.
                Map.entry(Map.of(DOCS_INDEX,
                                DOCS + "| [The skill](../skills/restest/SKILL.md) | Agents |\n"),
                        DOCS_INDEX + " lists ../skills/restest/SKILL.md, which is not a page"),
                Map.entry(Map.of(DOCS_INDEX, DOCS + "| [Nowhere](nowhere.md) | Nothing |\n"),
                        DOCS_INDEX + " lists nowhere.md, which is not a page"),
                Map.entry(Map.of(DOCS_INDEX, DOCS + "| [The top](#reference) | This page |\n"),
                        DOCS_INDEX + " lists #reference, which is not a page"),
                Map.entry(Map.of(DOCS_INDEX, DOCS + "\n## Elsewhere\n\nSome words.\n"),
                        "section \"Elsewhere\" has no table of pages"),
                Map.entry(Map.of(DOCS_INDEX, DOCS.replace(
                                "## Reference\n\n| Page | What it covers |\n|---|---|\n",
                                "## Reference\n\n| Page | What it covers |\n")),
                        "section \"Reference\" has no table of pages"),
                Map.entry(Map.of(DOCS_INDEX, DOCS.replace("## Building RESTest",
                                "## Reference\n\nSome words.\n\n## Building RESTest")),
                        "section \"Reference\" has no table of pages"),
                Map.entry(Map.of(DOCS_INDEX,
                                DOCS + "\n## Empty\n\n| Page | What it covers |\n|---|---|\n"),
                        "section \"Empty\" lists no page"),
                Map.entry(Map.of(DOCS_INDEX, DOCS.replace("[The command line](command-line.md)",
                                "The command line")),
                        "a row of \"Reference\" links no page"),
                Map.entry(Map.of(DOCS_INDEX, "# Documentation\n\nEvery page.\n"),
                        DOCS_INDEX + " has no section"),
                // The manual's index.
                Map.entry(Map.of(MANUAL_INDEX, MANUAL.replace("1. ", "- ").replace("2. ", "- ")),
                        MANUAL_INDEX + " has no numbered list of chapters"),
                Map.entry(Map.of(MANUAL_INDEX, MANUAL + "3. The glossary — the words.\n"),
                        "chapter 3 of its list does not start with a link"),
                Map.entry(Map.of(MANUAL_INDEX, MANUAL.replace("[A first run](01-a-first-run.md)",
                                "**[A first run](01-a-first-run.md)**")),
                        "chapter 1 of its list does not start with a link"),
                Map.entry(Map.of("docs/manual/03-glossary.md", "# Glossary\n"),
                        MANUAL_INDEX + "'s list has no chapter docs/manual/03-glossary.md"),
                // Titles and front matter.
                Map.entry(Map.of("docs/command-line.md", "Every option.\n"),
                        "docs/command-line.md has no title"),
                Map.entry(Map.of("docs/command-line.md", "```\n# not a title\n```\n"),
                        "docs/command-line.md has no title"),
                Map.entry(Map.of("docs/command-line.md", "# ![The command line](line.png)\n"),
                        "docs/command-line.md has an empty title"),
                Map.entry(Map.of("docs/command-line.md",
                                "---\ntitle: The command line\n---\n\n# The command line\n"),
                        "docs/command-line.md starts with front matter"),
                Map.entry(Map.of("docs/command-line.md",
                                "\uFEFF---\ntitle: The command line\n---\n\n# The command line\n"),
                        "docs/command-line.md starts with front matter"));

        for (Map.Entry<Map<String, String>, String> example : cases) {
            Path root = Files.createTempDirectory(folder, "case");
            write(root, example.getKey());
            assertThat(problems(root))
                    .describedAs("documentation with %s changed", example.getKey().keySet())
                    .anyMatch(problem -> problem.contains(example.getValue()));
        }
    }

    /**
     * What would stop the website from building the documentation under {@code root}, one line for
     * each, naming the file: an empty list when there is nothing.
     */
    private static List<String> problems(Path root) throws IOException {
        Set<String> pages = pages(root);
        List<String> problems = new ArrayList<>();
        Set<String> chapters = new HashSet<>();
        readManual(root, pages, chapters, problems);
        Set<String> entries = new HashSet<>(chapters);
        readSections(root, pages, entries, problems);

        for (String page : pages) {
            if (!needsNoEntry(page) && !entries.contains(page)) {
                problems.add(DOCS_INDEX + " lists no entry for " + page + ", so the website's menu "
                        + "would not reach it; every page needs one, in it or in " + MANUAL_INDEX);
            }
            if (page.startsWith("docs/manual/") && !page.equals(MANUAL_INDEX)
                    && !chapters.contains(page)) {
                problems.add(MANUAL_INDEX + "'s list has no chapter " + page);
            }
            List<String> lines = lines(root.resolve(page));
            if (!lines.isEmpty() && lines.get(0).replace("\uFEFF", "").equals("---")) {
                problems.add(page + " starts with front matter, which the website writes itself");
            }
            Optional<String> title = outsideCode(lines).stream()
                    .filter(line -> line.startsWith("# "))
                    .findFirst();
            if (title.isEmpty()) {
                problems.add(page + " has no title, a line starting with `# `");
            } else if (NOT_WORDS.matcher(title.get().substring(2)).replaceAll("").isEmpty()) {
                problems.add(page + " has an empty title: its first `# ` line has no words");
            }
        }
        return problems;
    }

    /** The pages of the documentation under {@code root}, as paths with forward slashes. */
    private static Set<String> pages(Path root) throws IOException {
        Set<String> pages = new TreeSet<>();
        for (String page : ROOT_PAGES) {
            if (Files.isRegularFile(root.resolve(page))) {
                pages.add(page);
            }
        }
        Path docs = root.resolve("docs");
        if (Files.isDirectory(docs)) {
            try (Stream<Path> files = Files.walk(docs)) {
                files.filter(file -> file.toString().endsWith(".md"))
                        .map(file -> slashed(root.relativize(file)))
                        .forEach(pages::add);
            }
        }
        return pages;
    }

    /**
     * Reads the manual's list of chapters, its first numbered list, adding each chapter it leads to
     * to {@code chapters} and anything the website would refuse to {@code problems}.
     */
    private static void readManual(
            Path root, Set<String> pages, Set<String> chapters, List<String> problems) {
        List<String> items = firstNumberedList(outsideCode(lines(root.resolve(MANUAL_INDEX))));
        if (items.isEmpty()) {
            problems.add(MANUAL_INDEX + " has no numbered list of chapters");
        }
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).startsWith("[")
                    || !readEntry(MANUAL_INDEX, items.get(i), pages, chapters, problems)) {
                problems.add(MANUAL_INDEX + ": chapter " + (i + 1)
                        + " of its list does not start with a link");
            }
        }
    }

    /**
     * Reads the sections of the documentation's index, adding each page a row leads to to
     * {@code entries} and anything the website would refuse to {@code problems}. A table is a line
     * starting with a pipe, its header, straight followed by the line of dashes under it; its rows
     * are the lines starting with a pipe after that.
     */
    private static void readSections(
            Path root, Set<String> pages, Set<String> entries, List<String> problems) {
        List<String> sections = new ArrayList<>();
        // For each section, the rows of its tables, or null while it has no table.
        List<Integer> rows = new ArrayList<>();
        boolean header = false;
        boolean inTable = false;
        for (String line : outsideCode(lines(root.resolve(DOCS_INDEX)))) {
            Matcher heading = SECTION.matcher(line);
            int last = sections.size() - 1;
            if (heading.matches()) {
                sections.add(heading.group(1).trim());
                rows.add(null);
                header = false;
                inTable = false;
            } else if (last < 0 || !line.startsWith("|")) {
                header = false;
                inTable = false;
            } else if (inTable) {
                rows.set(last, rows.get(last) + 1);
                String firstCell = line.substring(1).split("\\|", 2)[0];
                if (!readEntry(DOCS_INDEX, firstCell, pages, entries, problems)) {
                    problems.add(DOCS_INDEX + ": a row of \"" + sections.get(last)
                            + "\" links no page");
                }
            } else if (header && TABLE_RULE.matcher(line).matches()) {
                inTable = true;
                if (rows.get(last) == null) {
                    rows.set(last, 0);
                }
            } else {
                header = true;
            }
        }
        if (sections.isEmpty()) {
            problems.add(DOCS_INDEX + " has no section, a heading starting with `## `");
        }
        for (int i = 0; i < sections.size(); i++) {
            if (rows.get(i) == null) {
                problems.add(DOCS_INDEX + ": section \"" + sections.get(i)
                        + "\" has no table of pages");
            } else if (rows.get(i) == 0) {
                problems.add(DOCS_INDEX + ": section \"" + sections.get(i) + "\" lists no page");
            }
        }
    }

    /**
     * Reads one entry of {@code index}, the first link in {@code text}: adds the page it leads to
     * to {@code entries}, or says in {@code problems} that it leads to no page. Answers whether
     * {@code text} has a link at all.
     */
    private static boolean readEntry(
            String index, String text, Set<String> pages, Set<String> entries,
            List<String> problems) {
        Matcher link = LINK.matcher(text);
        if (!link.find()) {
            return false;
        }
        String page = pageOf(index, link.group(1), pages);
        if (page == null) {
            problems.add(index + " lists " + link.group(1)
                    + ", which is not a page of the documentation");
        } else {
            entries.add(page);
        }
        return true;
    }

    /**
     * The items of the first numbered list, each as its first paragraph: the item's line and the
     * indented lines straight after it. The list ends at the first line that is neither an item,
     * nor indented, nor blank.
     */
    private static List<String> firstNumberedList(List<String> lines) {
        List<String> items = new ArrayList<>();
        boolean paragraph = false;
        for (String line : lines) {
            Matcher item = ITEM.matcher(line);
            if (item.matches()) {
                items.add(item.group(1));
                paragraph = true;
            } else if (items.isEmpty()) {
                continue;
            } else if (line.isBlank()) {
                paragraph = false;
            } else if (Character.isWhitespace(line.charAt(0))) {
                if (paragraph) {
                    items.set(items.size() - 1, items.get(items.size() - 1) + " " + line.trim());
                }
            } else {
                break;
            }
        }
        return items;
    }

    /**
     * The page a link written in {@code from} leads to, as the website finds it: the file itself,
     * or the {@code README.md} of a folder; {@code null} for anything that is not a page, a heading
     * of the same page included.
     */
    private static String pageOf(String from, String target, Set<String> pages) {
        if (DocumentedLinksTest.ANOTHER_SITE.matcher(target).matches()) {
            return null;
        }
        int hash = target.indexOf('#');
        String file = hash < 0 ? target : target.substring(0, hash);
        if (file.isEmpty()) {
            return null;
        }
        String path = slashed(Path.of(from).resolveSibling(file).normalize());
        if (pages.contains(path)) {
            return path;
        }
        String readme = path.isEmpty() ? "README.md" : path + "/README.md";
        return pages.contains(readme) ? readme : null;
    }

    /** The pages that need no entry in an index. */
    private static boolean needsNoEntry(String page) {
        return page.equals(DOCS_INDEX) || page.equals(MANUAL_INDEX) || page.equals("README.md")
                || page.startsWith("docs/adr/");
    }

    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }

    /** The documentation's index of a small documentation in the shape the website reads. */
    private static final String DOCS = """
            # Documentation

            The [manual](manual/README.md) is read first.

            ## Building RESTest

            | Page | What it covers |
            |---|---|
            | [The rules](../AGENTS.md) | The rules |
            | [Decision records](adr/README.md) | Why it is shaped so |
            | [Contributing](../CONTRIBUTING.md) | How a change is made |

            ## Reference

            | Page | What it covers |
            |---|---|
            | [The command line](command-line.md) | Every option |
            """;

    /** The manual's index of that documentation. */
    private static final String MANUAL = """
            # The manual

            The [reference pages](../README.md) are the other half.

            ## Chapters

            1. [A first run](01-a-first-run.md) — a practice API.
            2. [Settings](02-settings.md) — how the tool
               behaves.
            """;

    /**
     * Writes that small documentation under {@code root}, with the files in {@code changed} in
     * place of its own.
     */
    private static void write(Path root, Map<String, String> changed) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", "# RESTest\n");
        files.put("AGENTS.md", "# The rules\n");
        files.put("CONTRIBUTING.md", "# Contributing\n");
        files.put("skills/restest/SKILL.md", "# The skill\n");
        files.put(DOCS_INDEX, DOCS);
        files.put("docs/command-line.md", "# The command line\n\nEvery option.\n");
        files.put(MANUAL_INDEX, MANUAL);
        files.put("docs/manual/01-a-first-run.md", "# A first run\n");
        files.put("docs/manual/02-settings.md", "# Settings\n");
        files.put("docs/adr/README.md", "# Decision records\n");
        files.put("docs/adr/0001-record-decisions.md", "# ADR-0001: Record decisions\n");
        files.putAll(changed);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
    }
}
