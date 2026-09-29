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
import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import io.restest.core.model.ApiModel;
import io.restest.core.settings.SettingKey;
import io.restest.core.settings.SettingKind;
import io.restest.core.settings.SettingSource;
import io.restest.core.settings.Settings;
import io.restest.gen.Campaign;
import io.restest.gen.Campaigns;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps the page that lists the switches in step with the switches themselves.
 *
 * <p>A switch is a setting that is true or false and turns off one thing RESTest does while it tests
 * an API: the first round of a run, one kind of change made to a request the API accepted, one
 * series of requests around a thing the run created. Somebody who wants to know what one of those
 * is worth runs the tool twice, once with it and once without, and the page is where they find
 * every switch and copy the files that turn off a whole idea at once. They will not read the code,
 * so a switch missing from the page is a switch nobody measures, and a file on it that the tool
 * refuses - or that quietly leaves something on - measures the wrong thing.
 *
 * <p>So what the page says is checked against the tool rather than trusted: against the list of
 * every setting ({@link SettingKey}), against what the tool does when nobody says otherwise
 * ({@link Settings#defaults()}), against the command line reading each of the page's files
 * ({@link Restest}), and against the plan RESTest carries ({@link Campaigns#shipped()}), of which the
 * plan on the page is a copy with one source taken out. Nothing here has an opinion about what the
 * switches should be.
 */
class DocumentedSwitchesTest {

    /** One row of the page's tables of switches: the switch, what it is by default, and the rest. */
    private static final Pattern ROW = Pattern.compile(
            "^\\| `([a-z]+\\.[A-Za-z]+)` \\| `([^`]*)` \\| [^|]* \\| .* \\|$", Pattern.MULTILINE);

    /** A block of YAML on the page, which is either a file of settings or a plan. */
    private static final Pattern BLOCK = Pattern.compile("```yaml\\n(.*?)```", Pattern.DOTALL);

    /** A setting named anywhere on the page, in a sentence as much as in a table. */
    private static final Pattern NAMED = Pattern.compile(
            "\\b(" + String.join("|", SettingKey.groups()) + ")\\.([A-Za-z]+)\\b");

    /** What begins like the name of a setting and is not one: a file the page mentions. */
    private static final Set<String> FILE_NAMES = Set.of("report.json");

    /** A group, and one setting inside it, as the command line prints them. */
    private static final Pattern PRINTED_GROUP = Pattern.compile("^([a-z]+):$");

    private static final Pattern PRINTED_SETTING =
            Pattern.compile("^  ([A-Za-z]+): (.*?)\\s+# (.+)$");

    private static String page;

    @BeforeAll
    static void readThePage() throws IOException {
        // Line endings taken out on the way in, for the reason the settings page's own check gives:
        // a checkout on Windows ends every line of a document differently, and what is compared
        // here is what the page says.
        page = Files.readString(switchesPage()).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("every switch the tool has is on the page, so none of them goes unmeasured")
    void every_switch_is_on_the_page() {
        List<String> listed = rows().stream().map(Row::name).toList();

        assertThat(switches()).allSatisfy(key ->
                assertThat(listed)
                        .describedAs("%s is true or false and is in none of the page's tables of "
                                + "switches", key.fullName())
                        .contains(key.fullName()));
    }

    @Test
    @DisplayName("and every switch on the page is one the tool has, listed once")
    void every_switch_on_the_page_exists() {
        List<String> real = switches().stream().map(SettingKey::fullName).toList();

        assertThat(rows()).allSatisfy(row ->
                assertThat(real)
                        .describedAs("the page lists '%s' as a switch, and the tool has no setting "
                                + "of that name that is true or false", row.name())
                        .contains(row.name()));
        assertThat(rows().stream().map(Row::name).distinct().count())
                .describedAs("a switch listed twice is two descriptions somebody has to keep the "
                        + "same")
                .isEqualTo(rows().size());
    }

    @Test
    @DisplayName("every switch is said to be on or off the way it is when nobody says otherwise")
    void every_row_says_what_the_switch_is_by_default() {
        assertThat(rows()).allSatisfy(row -> {
            String actually = SettingKey.named(row.name()).map(Settings.defaults()::written)
                    .orElse("not a setting at all");
            assertThat(row.byDefault())
                    .describedAs("the page says %s is %s by default, and it is %s", row.name(),
                            row.byDefault(), actually)
                    .isEqualTo(actually);
        });
    }

    @Test
    @DisplayName("every setting the page names, in a sentence or a table, is one the tool has, so "
            + "nothing sends a reader to a setting that was renamed or taken away")
    void nothing_named_on_the_page_has_gone_away() {
        List<String> named = NAMED.matcher(page).results()
                .map(found -> found.group(1) + "." + found.group(2))
                .filter(name -> !FILE_NAMES.contains(name))
                .distinct()
                .toList();

        assertThat(named).isNotEmpty();
        assertThat(named).allSatisfy(name ->
                assertThat(SettingKey.named(name))
                        .describedAs("the page names %s, and there is no such setting", name)
                        .isPresent());
    }

    @Test
    @DisplayName("every file of settings on the page is one the tool reads, and a run handed it "
            + "uses every value in it")
    void every_file_on_the_page_is_one_the_tool_takes(@TempDir Path directory)
            throws IOException {
        List<String> files = filesOfSettings();
        assertThat(files)
                .describedAs("the page is supposed to carry files to hand over with --settings")
                .isNotEmpty();

        for (int at = 0; at < files.size(); at++) {
            Path file = directory.resolve("settings-" + at + ".yaml");
            Files.writeString(file, files.get(at));
            StringWriter screen = new StringWriter();
            StringWriter problems = new StringWriter();

            int answer = Restest.run(
                    new String[] {"run", "--settings", file.toString(), "--print-settings"},
                    new PrintWriter(screen, true), new PrintWriter(problems, true));

            assertThat(answer)
                    .describedAs("the tool refused this file from the page:%n%s%nand said: %s",
                            files.get(at), problems)
                    .isZero();
            // Read back from what the tool printed rather than from the file, so that a value the
            // tool took some other way - or left as it was - shows as the difference it is. A
            // variable in the environment of whoever runs this would win over the file, and would
            // show here as a value from somewhere else.
            Map<String, Printed> used = printed(screen.toString());
            valuesIn(files.get(at)).forEach((name, value) -> {
                assertThat(used)
                        .describedAs("%s is in a file on the page and not among the settings the "
                                + "tool prints", name)
                        .containsKey(name);
                assertThat(used.get(name).value())
                        .describedAs("a run handed the page's file does not use the %s it gives",
                                name)
                        .isEqualTo(value);
                assertThat(used.get(name).from())
                        .describedAs("%s should have come from the file", name)
                        .isEqualTo(SettingSource.FILE.written());
            });
        }
    }

    @Test
    @DisplayName("every switch that is on unless somebody says otherwise is turned off by one of "
            + "the files on the page")
    void every_switch_can_be_turned_off_by_a_file_on_the_page() {
        Set<String> turnedOff = filesOfSettings().stream()
                .flatMap(file -> valuesIn(file).entrySet().stream())
                .filter(given -> "false".equals(given.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        assertThat(switches())
                .filteredOn(key -> "true".equals(Settings.defaults().written(key)))
                .allSatisfy(key ->
                        assertThat(turnedOff)
                                .describedAs("no file on the page turns %s off, so whoever "
                                        + "copies the page's files never measures it",
                                        key.fullName())
                                .contains(key.fullName()));
    }

    @Test
    @DisplayName("the plan on the page is the one RESTest carries, with the memory of what the API "
            + "returned taken out and its weight shared among the rest in the same proportions")
    void the_plan_on_the_page_is_the_carried_one_without_the_memory(@TempDir Path directory)
            throws IOException {
        List<String> plans = plans();
        assertThat(plans)
                .describedAs("the page shows one plan: the one RESTest carries, without the memory")
                .hasSize(1);
        Path file = directory.resolve("plan.yaml");
        Files.writeString(file, plans.get(0));

        Campaign onThePage = Campaigns.gather(Optional.of(file),
                ApiModel.of("the switches page", "1", List.of()), Set.of()).campaign();

        assertThat(onThePage.strategies())
                .describedAs("the plan on the page is no longer the one RESTest carries with the "
                        + "memory taken out. Print that plan with 'restest run --print-campaign', "
                        + "take out every 'source: observed', share its weight among the rest of "
                        + "its group in the proportions they had, and paste the result back in")
                .isEqualTo(withoutTheMemory(Campaigns.shipped()));
        assertThat(onThePage.operations().narrowsAnything())
                .describedAs("the plan RESTest carries touches every operation, and so does this")
                .isFalse();
    }

    /** Every setting that is true or false, which is what makes it a switch. */
    private static List<SettingKey> switches() {
        return SettingKey.all().stream().filter(key -> key.kind() == SettingKind.YES_OR_NO)
                .toList();
    }

    /** One switch as the page lists it. */
    private record Row(String name, String byDefault) {
    }

    private static List<Row> rows() {
        return ROW.matcher(page).results()
                .map(found -> new Row(found.group(1), found.group(2)))
                .toList();
    }

    /** Every block of YAML on the page, as it is written there. */
    private static List<String> blocks() {
        return BLOCK.matcher(page).results().map(found -> found.group(1)).toList();
    }

    /** The blocks that are files of settings: every one that is not a plan. */
    private static List<String> filesOfSettings() {
        return blocks().stream().filter(block -> !isAPlan(block)).toList();
    }

    /** The blocks that are plans, told apart by the strategies only a plan has. */
    private static List<String> plans() {
        return blocks().stream().filter(DocumentedSwitchesTest::isAPlan).toList();
    }

    private static boolean isAPlan(String block) {
        return YamlText.read(block, "a block on the switches page")
                instanceof JsonValue.JsonObject read && read.members().containsKey("strategies");
    }

    /**
     * What a file of settings on the page gives, by the full name of each setting.
     *
     * <p>Both of the shapes the tool reads: a group holding the settings inside it, and a setting
     * written out in full on a line of its own.
     */
    private static Map<String, String> valuesIn(String file) {
        JsonValue read = YamlText.read(file, "a file of settings on the switches page");
        Map<String, String> values = new LinkedHashMap<>();
        if (read instanceof JsonValue.JsonObject groups) {
            groups.members().forEach((group, held) -> {
                if (held instanceof JsonValue.JsonObject inside) {
                    inside.members().forEach((name, value) ->
                            values.put(group + "." + name, written(value)));
                } else {
                    values.put(group, written(held));
                }
            });
        }
        return values;
    }

    private static String written(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonBoolean yesOrNo -> String.valueOf(yesOrNo.value());
            case JsonValue.JsonNumber number -> number.value().toPlainString();
            case JsonValue.JsonString text -> text.value();
            default -> value.toString();
        };
    }

    /** One setting as the command line printed it: its value, and where the value came from. */
    private record Printed(String value, String from) {
    }

    private static Map<String, Printed> printed(String screen) {
        Map<String, Printed> found = new LinkedHashMap<>();
        String group = null;
        for (String line : screen.lines().toList()) {
            Matcher heading = PRINTED_GROUP.matcher(line);
            if (heading.matches()) {
                group = heading.group(1);
                continue;
            }
            Matcher setting = PRINTED_SETTING.matcher(line);
            if (group != null && setting.matches()) {
                found.put(group + "." + setting.group(1),
                        new Printed(unquoted(setting.group(2)), setting.group(3)));
            }
        }
        return found;
    }

    /** A value the command line quoted, such as a length of time, without its quotation marks. */
    private static String unquoted(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    /** The plan RESTest carries, with every {@code observed} taken out of it. */
    private static List<Campaign.PlannedStrategy> withoutTheMemory(Campaign carried) {
        return carried.strategies().stream()
                .map(strategy -> new Campaign.PlannedStrategy(strategy.name(), strategy.share(),
                        withoutTheMemory(strategy.sources()), strategy.mutatesAccepted(),
                        strategy.sendsSequences()))
                .toList();
    }

    private static List<Campaign.Entry> withoutTheMemory(List<Campaign.Entry> sources) {
        List<Campaign.Entry> kept = new ArrayList<>();
        for (Campaign.Entry entry : sources) {
            switch (entry) {
                case Campaign.Entry.Single single -> {
                    if (!isTheMemory(single.source())) {
                        kept.add(single);
                    }
                }
                case Campaign.Entry.Group group -> {
                    List<Campaign.Share> rest = group.among().stream()
                            .filter(share -> !isTheMemory(share.source()))
                            .toList();
                    // A group left with one source is not a choice any more, and a plan writes
                    // that source on its own.
                    kept.add(rest.size() == 1
                            ? new Campaign.Entry.Single(rest.get(0).source())
                            : new Campaign.Entry.Group(sharedInTheSameProportions(rest)));
                }
            }
        }
        return kept;
    }

    private static boolean isTheMemory(Campaign.Source source) {
        return source instanceof Campaign.Source.Builtin builtin
                && builtin.which() == Campaign.Builtin.OBSERVED;
    }

    /**
     * Weights that add up to a hundred again, in the proportions these had.
     *
     * <p>Whole numbers rarely divide evenly, so each gets its share rounded down, and what that
     * leaves over goes one at a time to the largest remainders - the way seats are shared out, and
     * the way the tool shares out the strategies of a plan. A tie goes to the one written first.
     */
    private static List<Campaign.Share> sharedInTheSameProportions(List<Campaign.Share> rest) {
        int total = rest.stream().mapToInt(Campaign.Share::weight).sum();
        int[] weights = new int[rest.size()];
        int handedOut = 0;
        for (int at = 0; at < rest.size(); at++) {
            weights[at] = Campaign.WHOLE * rest.get(at).weight() / total;
            handedOut += weights[at];
        }
        List<Integer> byRemainder = IntStream.range(0, rest.size()).boxed()
                .sorted(Comparator.comparingInt(
                        (Integer at) -> Campaign.WHOLE * rest.get(at).weight() % total).reversed())
                .toList();
        for (int next = 0; handedOut < Campaign.WHOLE; next++) {
            weights[byRemainder.get(next)]++;
            handedOut++;
        }
        List<Campaign.Share> shared = new ArrayList<>();
        for (int at = 0; at < rest.size(); at++) {
            shared.add(new Campaign.Share(rest.get(at).source(), weights[at]));
        }
        return shared;
    }

    private static Path switchesPage() {
        Path page = RepositoryRoot.locate().resolve("docs").resolve("switches.md");
        assertThat(page)
                .describedAs("the switches have a page, and these rules read it")
                .isRegularFile();
        return page;
    }
}
