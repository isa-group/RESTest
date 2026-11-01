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

import static io.restest.arch.DocumentedCommandLineTest.helpFor;
import static io.restest.arch.DocumentedCommandLineTest.optionsIn;
import static io.restest.arch.DocumentedCommandLineTest.section;
import static io.restest.arch.DocumentedReportTest.nameOf;
import static io.restest.arch.DocumentedReportTest.page;
import static io.restest.arch.DocumentedReportTest.parentOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.settings.SettingKey;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps the skill for coding agents in step with RESTest itself.
 *
 * <p>A coding agent - a program that writes code for a person and runs tools as it goes - can be
 * taught to test a REST API with RESTest by one file, the skill, which ships with every release and
 * is also printed, word for word, at the end of the page for coding agents. The agent follows it with
 * no one to ask: it runs the commands the skill gives and reads the keys of the report the skill
 * names. A command or a key that RESTest no longer has would leave it stuck, or reading nothing.
 *
 * <p>So the skill is read here as an agent would, and held to the tool: its heading follows the open
 * format agents read skills in; the page holds it as it is; every command and option it gives is one
 * RESTest has, every setting one it knows, every exit code one the page about the command line
 * explains, the line it finds a run's summary by one a run prints, and every key of the report it
 * reads one the page about the report, itself held to the tool by {@link DocumentedReportTest},
 * explains at that place in the file.
 */
class DocumentedSkillTest {

    /** The skill's name, which is also the name of its folder. */
    private static final String NAME = "restest";

    private static final String SKILL = "skills/" + NAME + "/SKILL.md";

    /** The page that ends with the skill, for agents that read no skills. */
    private static final String PAGE = "docs/for-coding-agents.md";

    private static final String REPORT_PAGE = "docs/report.md";

    /** The fields the open format of skills allows in a skill's heading. */
    private static final Set<String> FIELDS = Set.of(
            "name", "description", "license", "compatibility", "metadata", "allowed-tools");

    /** The format's rule for a name: lowercase letters and digits, single hyphens between. */
    private static final Pattern A_NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /**
     * Every program the skill runs over {@code report.json} with {@code jq}, and the keys it reads,
     * each written out from the top of the file. A program changed in the skill has to be changed
     * here too, with the keys it now reads, so that they are checked against the report's page.
     */
    private static final Map<String, List<String>> READS = Map.of(
            ".faultsByOperation | group_by(.code)[] | \"\\(.[0].severity) \\(.[0].title) "
                    + "(F\\(.[0].code)): \\(map(.operation) | join(\" \"))\"",
            List.of("faultsByOperation", "faultsByOperation[].code", "faultsByOperation[].severity",
                    "faultsByOperation[].title", "faultsByOperation[].operation"),
            "first(.findings[] | select(.operation == $op and .category.code == $code)) | "
                    + "{summary, details, curl, reply: ((.interaction.outcome.body.text // \"\")"
                    + "[0:500])}",
            List.of("findings", "findings[].operation", "findings[].category",
                    "findings[].category.code", "findings[].summary", "findings[].details",
                    "findings[].curl", "findings[].interaction", "findings[].interaction.outcome",
                    "findings[].interaction.outcome.body",
                    "findings[].interaction.outcome.body.text"),
            "\"\\([.operations[] | select((.replies.byClass[\"2xx\"] // 0) > 0)] | length) of "
                    + "\\(.operations | length) operations answered 2xx\"",
            List.of("operations", "operations[].replies", "operations[].replies.byClass"),
            "{cutShort, notTested: [.skippedOperations[].operation], keysNotGiven: "
                    + "[.keysNotGiven[] | \"\\(.scheme) (\\(.operations) operations)\"], notChecked: "
                    + "[.notChecked[] | \"\\(.what) of \\(.replies) replies: \\(.why)\"]}",
            List.of("cutShort", "skippedOperations", "skippedOperations[].operation",
                    "keysNotGiven", "keysNotGiven[].scheme", "keysNotGiven[].operations",
                    "notChecked", "notChecked[].what", "notChecked[].replies",
                    "notChecked[].why"));

    /** A line of a code block that runs {@code jq} over the report: the program in its quotes. */
    private static final Pattern JQ = Pattern.compile("^jq .*?'(.+)' \\S*report\\.json$");

    /** A key a program reads: a name after a dot. */
    private static final Pattern DOTTED = Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_]*)");

    /** A key an object built by a program copies as it is: a bare name between braces or commas. */
    private static final Pattern BARE = Pattern.compile("[{,]\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?=[,}])");

    /** Code in a line of text, between single backticks. */
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    /** An option, as a command line writes it. */
    private static final Pattern OPTION = Pattern.compile("(?<![\\w-])--[a-z][a-z-]*");

    /**
     * The options of other tools the skill gives - Docker's, jbang's and {@code jq}'s - which are
     * the only ones it may name that RESTest does not have.
     */
    private static final Set<String> OTHER_TOOLS_OPTIONS = Set.of(
            "--rm", "--network", "--user", "--add-host", "--fresh", "--arg", "--argjson");

    /** A row of the skill's table of exit codes: the number. */
    private static final Pattern EXIT_ROW = Pattern.compile("^\\| (\\d+) \\|", Pattern.MULTILINE);

    /** The line of a code block that finds the summary with {@code sed}: the pattern it looks for. */
    private static final Pattern SED = Pattern.compile("^sed -n '/(.+)/,\\$p' .*$");

    private static final String COMMAND_LINE_PAGE = "docs/command-line.md";

    private static String heading;
    private static String body;

    @BeforeAll
    static void readTheSkill() {
        String skill = page(SKILL);
        assertThat(skill)
                .describedAs("%s should begin with a heading between two lines of ---", SKILL)
                .startsWith("---\n")
                .contains("\n---\n");
        String[] parts = skill.substring("---\n".length()).split("\n---\n", 2);
        heading = parts[0];
        body = parts[1].stripLeading();
    }

    @Test
    @DisplayName("the skill's heading is one coding agents read: its name, its folder's, and a "
            + "description of the length they take")
    void the_heading_follows_the_format_of_skills() {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : heading.split("\n")) {
            int colon = line.indexOf(": ");
            assertThat(colon)
                    .describedAs("each line of the heading of %s should be a field and its value, "
                            + "on one line: %s", SKILL, line)
                    .isPositive();
            fields.put(line.substring(0, colon), line.substring(colon + 2).strip());
        }
        assertThat(FIELDS)
                .describedAs("agents read only these fields in the heading of a skill")
                .containsAll(fields.keySet());
        assertThat(fields.get("name"))
                .describedAs("a skill's name is the name of its folder, in lowercase letters, "
                        + "digits and single hyphens")
                .isEqualTo(NAME)
                .matches(A_NAME);
        assertThat(fields.get("description"))
                .describedAs("a skill's description is what an agent reads to choose it, from 1 "
                        + "to 1024 characters")
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(1024);
    }

    @Test
    @DisplayName("the page for coding agents ends with the skill, word for word")
    void the_page_holds_the_skill() {
        assertThat(page(PAGE))
                .describedAs("%s is read by agents that read no skills, and ends with what %s "
                        + "teaches the others: copy the skill's text, below its heading, to the end "
                        + "of the page", PAGE, SKILL)
                .endsWith(body);
    }

    @Test
    @DisplayName("every command the skill gives RESTest is a command it has")
    void every_command_is_one_restest_has() {
        Set<String> commands = new TreeSet<>(section(helpFor("--help"), "Commands:").lines()
                .filter(line -> line.matches("^  [a-z]+ .*"))
                .map(line -> line.trim().split(" ")[0])
                .toList());

        List<String> given = commandsIn(body);
        assertThat(given).describedAs("%s should give RESTest's commands", SKILL).isNotEmpty();
        for (String command : given) {
            String name = command.split("\\s+")[1];
            assertThat(commands)
                    .describedAs("%s gives `%s`, but RESTest has no command %s", SKILL, command, name)
                    .contains(name);
        }
    }

    @Test
    @DisplayName("every option the skill names is one of RESTest's, or one of the few of other tools "
            + "it gives")
    void every_option_is_one_there_is() {
        Set<String> options = new TreeSet<>(optionsIn(helpFor("--help")).keySet());
        options.addAll(optionsIn(helpFor("run", "--help")).keySet());
        options.addAll(OTHER_TOOLS_OPTIONS);
        OPTION.matcher(body).results().forEach(option -> assertThat(options)
                .describedAs("%s names %s, which is no option of RESTest's, nor one of the other "
                        + "tools' this test lists", SKILL, option.group())
                .contains(option.group()));
    }

    @Test
    @DisplayName("every exit code in the skill's table is one the page about the command line gives")
    void every_exit_code_is_documented() {
        String commandLine = page(COMMAND_LINE_PAGE);
        List<String> codes = EXIT_ROW.matcher(body).results().map(row -> row.group(1)).toList();
        assertThat(codes).describedAs("%s should give a table of exit codes", SKILL).isNotEmpty();
        for (String code : codes) {
            assertThat(commandLine)
                    .describedAs("%s explains exit code %s, which %s does not give", SKILL, code,
                            COMMAND_LINE_PAGE)
                    .contains("`" + code + "`");
        }
    }

    @Test
    @DisplayName("the line the skill finds the summary by is one a run prints")
    void the_summary_is_where_the_skill_looks(@TempDir Path directory) throws IOException {
        String sought = codeLines(body).stream().map(SED::matcher).filter(Matcher::matches)
                .map(sed -> sed.group(1)).findFirst().orElseThrow(() -> new AssertionError(
                        SKILL + " should find the summary with sed -n '/<pattern>/,$p'"));
        Pattern summary = Pattern.compile(sought);
        String printed = DocumentedReportTest.aRealRun(directory).screen();
        assertThat(printed.lines().anyMatch(line -> summary.matcher(line).find()))
                .describedAs("%s finds the summary by a line matching /%s/, and a run printed none:"
                        + "%n%s", SKILL, sought, printed)
                .isTrue();
    }

    @Test
    @DisplayName("every setting the skill names is one RESTest has")
    void every_setting_is_one_restest_has() {
        DocumentedSwitchesTest.NAMED.matcher(body).results()
                .map(named -> named.group(1) + "." + named.group(2))
                .filter(name -> !DocumentedSwitchesTest.FILE_NAMES.contains(name))
                .forEach(name -> assertThat(SettingKey.named(name))
                        .describedAs("%s names %s, which is not a setting", SKILL, name)
                        .isPresent());
    }

    @Test
    @DisplayName("every key of report.json the skill reads is one the report's page explains at that "
            + "place")
    void every_key_read_is_explained() {
        Map<String, List<String>> explained =
                DocumentedReportTest.keysExplainedOn(page(REPORT_PAGE));
        Set<String> programs = new LinkedHashSet<>();
        for (String line : codeLines(body)) {
            Matcher jq = JQ.matcher(line);
            if (jq.matches()) {
                programs.add(jq.group(1));
            }
        }
        assertThat(programs)
                .describedAs("the programs %s runs over report.json, and the keys each reads, are "
                        + "listed in this test; change the list with the skill", SKILL)
                .containsExactlyInAnyOrderElementsOf(READS.keySet());

        READS.forEach((program, keys) -> {
            Set<String> named = new TreeSet<>();
            DOTTED.matcher(program).results().forEach(name -> named.add(name.group(1)));
            BARE.matcher(program).results().forEach(name -> named.add(name.group(1)));
            assertThat(new TreeSet<>(keys.stream().map(DocumentedReportTest::nameOf).toList()))
                    .describedAs("the keys this test lists for `%s` should be the ones it reads",
                            program)
                    .isEqualTo(named);
            for (String key : keys) {
                String place = parentOf(key);
                assertThat(explained.getOrDefault(place, List.of()))
                        .describedAs("%s reads %s from report.json, which %s does not explain under "
                                + "%s", SKILL, key, REPORT_PAGE,
                                place.isEmpty() ? "the top of the file" : place)
                        .contains(nameOf(key));
            }
        });
    }

    @Test
    @DisplayName("the skill links only to other sites, since it is read outside the repository")
    void the_skill_links_nowhere_in_the_repository() {
        for (String target : DocumentedLinksTest.linksIn(RepositoryRoot.locate().resolve(SKILL))) {
            assertThat(target)
                    .describedAs("%s is installed into other projects, where a link into this "
                            + "repository leads nowhere: write the address in full", SKILL)
                    .matches(DocumentedLinksTest.ANOTHER_SITE);
        }
    }

    /** The lines of every fenced code block of the text. */
    private static List<String> codeLines(String text) {
        List<String> lines = new ArrayList<>();
        boolean inBlock = false;
        for (String line : text.split("\n")) {
            if (line.strip().startsWith("```")) {
                inBlock = !inBlock;
            } else if (inBlock) {
                lines.add(line.strip());
            }
        }
        return lines;
    }

    /**
     * Every command the text gives RESTest: each line of a code block, and each piece of code in a
     * line of text, that begins with {@code restest}, up to where its output is sent. Only a {@code >}
     * or a {@code |} between spaces sends it somewhere: the one in a {@code <placeholder>} does not.
     */
    private static List<String> commandsIn(String text) {
        List<String> commands = new ArrayList<>();
        List<String> candidates = new ArrayList<>(codeLines(text));
        INLINE_CODE.matcher(text).results().forEach(code -> candidates.add(code.group(1)));
        for (String candidate : candidates) {
            if (candidate.startsWith("restest ")) {
                commands.add(candidate.split(" > | \\| |;")[0].strip());
            }
        }
        return commands;
    }
}
