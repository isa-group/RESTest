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

import io.restest.cli.Restest;
import io.restest.core.auth.AuthGiven;
import io.restest.core.settings.SettingKey;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps the page that describes the command line in step with the command line itself.
 *
 * <p>The page is where a person, or somebody writing a script around RESTest, finds every command,
 * every option of a run, every variable the tool reads and what each number the command ends with
 * means - without building anything. From v2.0 those are a promise that does not change within 2.x,
 * so the page is the promise written down, and a promise written down twice drifts: an option added
 * to the tool and not to the page, or a default changed in one place only, leaves a page that is
 * confidently wrong.
 *
 * <p>So the page is checked against what {@link Restest} itself prints when asked for help, rather
 * than trusted. Nothing here has an opinion about what the command line should be.
 */
class DocumentedCommandLineTest {

    /** What a failure here means, said once so that every one of them says it. */
    private static final String FROZEN = "The command line is frozen from v2.0: a change to it is a "
            + "2.x decision, and ADR-0015 says what may change. If this one was meant, change "
            + "docs/command-line.md with it";

    /** One row of the page's table of options: the option, its value, its default. */
    private static final Pattern OPTION_ROW = Pattern.compile(
            "^\\| `(--[a-z-]+)` \\| (.*?) \\| (.*?) \\| .* \\|$", Pattern.MULTILINE);

    /** One row of the page's table of commands. */
    private static final Pattern COMMAND_ROW =
            Pattern.compile("^\\| `restest ([a-z]+)[^`]*` \\|", Pattern.MULTILINE);

    /** One row of the page's table of variables. */
    private static final Pattern VARIABLE_ROW =
            Pattern.compile("^\\| `(RESTEST_[A-Z_<>]+)` \\|", Pattern.MULTILINE);

    /** A variable named anywhere, written out in full. */
    private static final Pattern A_VARIABLE = Pattern.compile("\\bRESTEST_[A-Z][A-Z_]*[A-Z]\\b");

    private static String page;
    private static String help;
    private static String runHelp;

    @BeforeAll
    static void readThePageAndTheHelp() throws IOException {
        // Line endings taken out on the way in, as for every page these tests read: a checkout on
        // Windows ends every line of a document with a carriage return as well.
        page = Files.readString(RepositoryRoot.locate().resolve("docs").resolve("command-line.md"))
                .replace("\r\n", "\n");
        help = helpFor("--help");
        runHelp = helpFor("run", "--help");
    }

    @Test
    @DisplayName("the page's commands are the commands there are")
    void the_commands_are_the_ones_there_are() {
        Set<String> listed = new TreeSet<>(section(help, "Commands:").lines()
                .map(line -> line.matches("^  [a-z]+ .*") ? line.trim().split(" ")[0] : "")
                .filter(name -> !name.isEmpty())
                .toList());

        assertThat(matches(COMMAND_ROW, part("## Commands")))
                .describedAs(FROZEN)
                .isEqualTo(listed)
                .isNotEmpty();
    }

    @Test
    @DisplayName("every option of a run is a row on the page, with the value and the default the help gives")
    void every_option_is_on_the_page() {
        Map<String, HelpedOption> helped = optionsIn(runHelp);
        helped.remove("--help");
        helped.remove("--version");
        Map<String, String[]> rows = new LinkedHashMap<>();
        OPTION_ROW.matcher(part("## The options of `restest run`")).results()
                .forEach(row -> rows.put(row.group(1), new String[] {row.group(2), row.group(3)}));

        assertThat(rows.keySet()).describedAs(FROZEN).containsExactlyElementsOf(helped.keySet());
        helped.forEach((name, option) -> {
            String[] row = rows.get(name);
            assertThat(row[0]).describedAs("%s takes %s. %s", name, option.value(), FROZEN)
                    .isEqualTo(option.value().isEmpty() ? "—" : "`" + option.value() + "`");
            if (option.defaultValue() != null) {
                assertThat(row[1]).describedAs("%s is %s when nobody says otherwise. %s", name,
                        option.defaultValue(), FROZEN)
                        .isEqualTo("`" + option.defaultValue() + "`");
            } else {
                assertThat(row[1]).describedAs("the help gives %s no default, so the page cannot "
                        + "give it one to copy", name).doesNotMatch("`[^`]*`");
            }
        });
        assertThat(runHelp).contains("[<specification>]");
        assertThat(part("## The options of `restest run`")).contains("`<specification>`");
    }

    @Test
    @DisplayName("the exit codes on the page are the ones the help lists, and mean what the help says")
    void the_exit_codes_are_the_ones_listed() {
        Map<String, String> listed = new LinkedHashMap<>();
        String code = null;
        for (String line : section(runHelp, "Exit codes").lines().toList()) {
            Matcher found = Pattern.compile("^  (\\d+)   (.*)$").matcher(line);
            if (found.matches()) {
                code = found.group(1);
                listed.put(code, found.group(2).trim());
            } else if (code != null) {
                listed.merge(code, line.trim(), (so, far) -> so + " " + far);
            }
        }
        String codes = part("## Exit codes");
        String answered = codes.substring(0, codes.indexOf("### Numbers RESTest does not choose"));
        Map<String, String> rows = new LinkedHashMap<>();
        Pattern.compile("^\\| `(\\d+)` \\| (.*) \\|$", Pattern.MULTILINE).matcher(answered)
                .results().forEach(row -> rows.put(row.group(1), row.group(2)));

        assertThat(rows.keySet()).describedAs(FROZEN).isEqualTo(listed.keySet())
                .contains("0", "1", "2", "3", "4");
        // The page may say more than the help has room for, never something else: each row begins
        // with the help's own words.
        listed.forEach((number, meaning) -> assertThat(rows.get(number))
                .describedAs("the page and the help say different things about %s. %s", number,
                        FROZEN)
                .startsWith(meaning.replaceAll("\\.$", "")));
    }

    @Test
    @DisplayName("the variables on the page are the ones the tool reads, and so are the ones the help names")
    void the_variables_are_the_ones_read() {
        Set<String> settings = SettingKey.all().stream().map(SettingKey::environmentName)
                .collect(Collectors.toSet());
        String pattern = SettingKey.ENVIRONMENT_PREFIX + "<GROUP>_<KEY>";

        assertThat(matches(VARIABLE_ROW, part("## The environment")))
                .describedAs(FROZEN)
                .containsExactlyInAnyOrder(AuthGiven.VARIABLE, pattern);
        assertThat(section(runHelp, "Environment:")).contains(AuthGiven.VARIABLE).contains(pattern);
        for (String text : List.of(page, runHelp)) {
            A_VARIABLE.matcher(text).results().map(MatchResult::group)
                    .filter(name -> !name.equals(AuthGiven.VARIABLE))
                    .forEach(name -> assertThat(settings)
                            .describedAs("%s is named as a setting in the environment and is none",
                                    name)
                            .contains(name));
        }
    }

    /** An option as the help shows it: what it takes, if anything, and its default, if any. */
    private record HelpedOption(String value, String defaultValue) {
    }

    /**
     * Every option in a command's help, in the order shown, read from the column the help writes
     * options in: a line beginning with the option, and the lines under it indented further, which
     * are its description and end with its default when it has one.
     */
    private static Map<String, HelpedOption> optionsIn(String helpText) {
        Pattern start = Pattern.compile("^ {2,6}(?:-[A-Za-z], )?(--[a-z-]+)(?:=(<[^>]+>))?(.*)$");
        Map<String, HelpedOption> options = new LinkedHashMap<>();
        String name = null;
        String value = null;
        StringBuilder description = new StringBuilder();
        for (String line : section(helpText, "").lines().toList()) {
            Matcher found = start.matcher(line);
            if (found.matches() || line.isBlank()) {
                if (name != null) {
                    options.put(name, new HelpedOption(value, defaultIn(description)));
                    name = null;
                }
                if (line.isBlank()) {
                    break;
                }
                name = found.group(1);
                value = found.group(2) == null ? "" : found.group(2);
                description.setLength(0);
                description.append(found.group(3));
            } else if (name != null) {
                description.append(' ').append(line.trim());
            }
        }
        if (name != null) {
            options.put(name, new HelpedOption(value, defaultIn(description)));
        }
        return options;
    }

    private static String defaultIn(CharSequence description) {
        Matcher found = Pattern.compile("Default: (\\S+)\\.$").matcher(description.toString().trim());
        return found.find() ? found.group(1) : null;
    }

    /**
     * The lines of the help from the one beginning with a heading to the next blank line; from
     * where the options begin, for none.
     */
    private static String section(String helpText, String heading) {
        List<String> lines = helpText.lines().toList();
        int from = 0;
        if (heading.isEmpty()) {
            while (from < lines.size() && !lines.get(from).matches("^ {2,6}(\\[<|-).*")) {
                from++;
            }
        } else {
            while (from < lines.size() && !lines.get(from).startsWith(heading)) {
                from++;
            }
            from++;
        }
        StringBuilder section = new StringBuilder();
        for (int i = from; i < lines.size() && !lines.get(i).isBlank(); i++) {
            section.append(lines.get(i)).append('\n');
        }
        assertThat(section).describedAs("the help has no section '%s'", heading).isNotEmpty();
        return section.toString();
    }

    /** The page from a heading to the next heading of the same level. */
    private static String part(String heading) {
        int from = page.indexOf("\n" + heading + "\n");
        assertThat(from).describedAs("the page has no heading '%s'", heading).isNotNegative();
        int to = page.indexOf("\n## ", from + heading.length() + 1);
        return page.substring(from, to < 0 ? page.length() : to);
    }

    private static Set<String> matches(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(found -> found.group(1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String helpFor(String... arguments) {
        StringWriter written = new StringWriter();
        PrintWriter out = new PrintWriter(written);
        int answer = Restest.run(arguments, out, new PrintWriter(new StringWriter()));
        out.flush();
        assertThat(answer).isZero();
        return written.toString().replace("\r\n", "\n");
    }
}
