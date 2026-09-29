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
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.spec.SwaggerSpecificationParser;
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
import java.util.TreeMap;
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
 * plan on the page is a copy with one source taken out. The files are checked for what their
 * labels promise as well: the file for a whole idea turns all of it off, the file for a milestone
 * is the files for its parts together, and the file that gives a run its seed back leaves nothing a
 * run would send depending on what the API answered, which {@link RandomTestCaseGenerator} is asked
 * about a real document. Nothing here has an opinion about what the switches should be.
 */
class DocumentedSwitchesTest {

    /**
     * One row of the page's tables of switches: the switch, what it is by default, the increment it
     * came with, and the rest.
     */
    private static final Pattern ROW = Pattern.compile(
            "^\\| `([a-z]+\\.[A-Za-z]+)` \\| `([^`]*)` \\| ([^|]*) \\| .* \\|$", Pattern.MULTILINE);

    /** A label that is the number of one increment, such as 10.2. */
    private static final Pattern AN_INCREMENT = Pattern.compile("\\d+\\.\\d+");

    /** A setting named anywhere on the page, in a sentence as much as in a table. */
    private static final Pattern NAMED = Pattern.compile(
            "\\b(" + String.join("|", SettingKey.groups()) + ")\\.([A-Za-z]+)\\b");

    /** What begins like the name of a setting and is not one: a file the page mentions. */
    private static final Set<String> FILE_NAMES = Set.of("report.json");

    /** The label the page gives a file: the words in bold before it, up to a comma or a colon. */
    private static final Pattern LABEL = Pattern.compile("^\\*\\*([^,:*]+)");

    /** The heading the file that gives a run its seed back sits under. */
    private static final String THE_SEED = "Getting the seed back";

    /**
     * A document whose creations can start every kind of series, so that a series left switched
     * on shows: the pet clinic of the corpus the tool is measured on.
     */
    private static final String A_DOCUMENT_WITH_CREATIONS =
            "restest-spec/src/test/resources/specifications/restleague-2027/pet-clinic/openapi.yaml";

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
                        + "its group in the proportions they had, and paste the result back in - "
                        + "and read again the sentences on the page that quote its shares and "
                        + "weights")
                .isEqualTo(withoutTheMemory(Campaigns.shipped()));
        assertThat(onThePage.operations())
                .describedAs("the plan on the page touches other operations than the one RESTest "
                        + "carries")
                .isEqualTo(Campaigns.shipped().operations());
    }

    @Test
    @DisplayName("the file for the series turns off every series the tool has, and nothing else")
    void the_file_for_the_series_turns_off_every_series() {
        assertThat(valuesIn(labelled("10.3")))
                .describedAs("the file for 10.3 is supposed to turn off every series there is, so "
                        + "that a run handed it sends none")
                .isEqualTo(allOff(series()));
    }

    @Test
    @DisplayName("the files for 10.1 and 10.2 turn off every kind of change between them, and no "
            + "kind in both")
    void the_files_for_the_changes_turn_off_every_kind_between_them() {
        Map<String, String> oneValue = valuesIn(labelled("10.1"));
        Map<String, String> theRest = valuesIn(labelled("10.2"));

        assertThat(oneValue.keySet())
                .describedAs("a kind of change came with one increment or the other, so it is in "
                        + "one of the two files")
                .doesNotContainAnyElementsOf(theRest.keySet());
        Map<String, String> between = new TreeMap<>(oneValue);
        between.putAll(theRest);
        assertThat(between)
                .describedAs("between them, the files for 10.1 and 10.2 are supposed to turn off "
                        + "every kind of change the tool can make to an accepted request")
                .isEqualTo(allOff(kindsOfChange()));
    }

    @Test
    @DisplayName("a file for one increment turns off only switches the page says came with it")
    void a_file_for_an_increment_turns_off_only_what_it_added() {
        Map<String, String> cameWith = new LinkedHashMap<>();
        rows().forEach(row -> cameWith.put(row.name(), row.cameWith()));

        List<Block> forOneIncrement = blocks().stream()
                .filter(block -> AN_INCREMENT.matcher(block.label()).matches())
                .toList();
        assertThat(forOneIncrement).isNotEmpty();
        assertThat(forOneIncrement).allSatisfy(block ->
                assertThat(valuesIn(block.text()).keySet()).allSatisfy(name ->
                        assertThat(cameWith.get(name))
                                .describedAs("the file for %s turns off %s, which the page's table "
                                        + "says came with %s", block.label(), name,
                                        cameWith.get(name))
                                .isEqualTo(block.label())));
    }

    @Test
    @DisplayName("the file for a milestone is the files for its increments put together")
    void the_file_for_a_milestone_is_its_increments_together() {
        assertThat(valuesIn(labelled("Reach")))
                .describedAs("the file for reach is supposed to be the files for 2.9, 9.1 and 9.2")
                .isEqualTo(together("2.9", "9.1", "9.2"));
        Map<String, String> breaking = new TreeMap<>(valuesIn(labelled("10.3")));
        breaking.put("mutation.violations", "false");
        assertThat(valuesIn(labelled("Break")))
                .describedAs("the file for break is supposed to turn off every change to an "
                        + "accepted request and every series")
                .isEqualTo(breaking);
        assertThat(valuesIn(labelled("Both")))
                .describedAs("the file for both is supposed to be the files for reach and break")
                .isEqualTo(together("Reach", "Break"));
    }

    @Test
    @DisplayName("the file for getting the seed back, handed over with the plan on the page, leaves "
            + "nothing a run sends depending on what the API answered")
    void the_file_for_the_seed_gives_it_back(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("plan.yaml");
        Files.writeString(file, plans().get(0));
        Campaign withoutTheMemory = Campaigns.gather(Optional.of(file),
                ApiModel.of("the switches page", "1", List.of()), Set.of()).campaign();
        ApiModel withCreations = new SwaggerSpecificationParser().parse(
                RepositoryRoot.locate().resolve(A_DOCUMENT_WITH_CREATIONS).toString());

        // First by what it says, since a series the pet clinic cannot start would never show below:
        // every change to an accepted request off, and every series there is.
        Map<String, String> everythingThatListens = new TreeMap<>(allOff(series()));
        everythingThatListens.put("mutation.violations", "false");
        assertThat(valuesIn(fileUnder(THE_SEED)))
                .describedAs("the file for getting the seed back is supposed to turn off every "
                        + "change to an accepted request and every series")
                .isEqualTo(everythingThatListens);

        // Then by what a run does with it. Each half on its own is not enough, which is what makes
        // the file worth checking: the series left on, or the changes left on, and the run still
        // depends on the API.
        assertThat(generator(withCreations, withoutTheMemory,
                Settings.from(Map.of("mutation.violations", "false"))).dependsOnTheApisAnswers())
                .describedAs("with the series on, a run of this document depends on what the API "
                        + "answers; if it did not, this check would prove nothing")
                .isTrue();
        assertThat(generator(withCreations, withoutTheMemory, Settings.from(allOff(series())))
                .dependsOnTheApisAnswers())
                .describedAs("with the changes on, a run of this document depends on what the API "
                        + "answers; if it did not, this check would prove nothing")
                .isTrue();
        assertThat(generator(withCreations, withoutTheMemory, Settings.from(valuesIn(fileUnder(
                THE_SEED)))).dependsOnTheApisAnswers())
                .describedAs("handed the page's plan and its file for getting the seed back, a run "
                        + "still depends on what the API answers, so the seed does not repeat it")
                .isFalse();
    }

    /** Every setting that is true or false, which is what makes it a switch. */
    private static List<SettingKey> switches() {
        return SettingKey.all().stream().filter(key -> key.kind() == SettingKind.YES_OR_NO)
                .toList();
    }

    /** One switch as the page lists it. */
    private record Row(String name, String byDefault, String cameWith) {
    }

    private static List<Row> rows() {
        return ROW.matcher(page).results()
                .map(found -> new Row(found.group(1), found.group(2), found.group(3).trim()))
                .toList();
    }

    /** A block of YAML on the page, with the label before it, if any, and the heading above it. */
    private record Block(String text, String label, String heading) {
    }

    /** Every block of YAML on the page, as it is written there. */
    private static List<Block> blocks() {
        List<Block> found = new ArrayList<>();
        String heading = "";
        String label = "";
        StringBuilder inside = null;
        for (String line : page.lines().toList()) {
            if (inside != null) {
                if (line.equals("```")) {
                    found.add(new Block(inside.toString(), label, heading));
                    inside = null;
                    label = "";
                } else {
                    inside.append(line).append('\n');
                }
            } else if (line.equals("```yaml")) {
                inside = new StringBuilder();
            } else if (line.startsWith("## ")) {
                heading = line.substring("## ".length()).trim();
                label = "";
            } else {
                Matcher labelled = LABEL.matcher(line);
                if (labelled.find()) {
                    label = labelled.group(1).trim();
                }
            }
        }
        return found;
    }

    /** The blocks that are files of settings: every one that is not a plan. */
    private static List<String> filesOfSettings() {
        return blocks().stream().map(Block::text).filter(block -> !isAPlan(block)).toList();
    }

    /** The blocks that are plans, told apart by the strategies only a plan has. */
    private static List<String> plans() {
        return blocks().stream().map(Block::text).filter(DocumentedSwitchesTest::isAPlan).toList();
    }

    /** The one file of settings on the page with this label in bold before it. */
    private static String labelled(String label) {
        List<String> found = blocks().stream()
                .filter(block -> block.label().equals(label) && !isAPlan(block.text()))
                .map(Block::text)
                .toList();
        assertThat(found)
                .describedAs("the page is supposed to have one file labelled '%s'", label)
                .hasSize(1);
        return found.get(0);
    }

    /** The one file of settings on the page under this heading. */
    private static String fileUnder(String heading) {
        List<String> found = blocks().stream()
                .filter(block -> block.heading().equals(heading) && !isAPlan(block.text()))
                .map(Block::text)
                .toList();
        assertThat(found)
                .describedAs("the page is supposed to have one file under '%s'", heading)
                .hasSize(1);
        return found.get(0);
    }

    /** What the files with these labels give, put together. */
    private static Map<String, String> together(String... labels) {
        Map<String, String> values = new TreeMap<>();
        for (String label : labels) {
            values.putAll(valuesIn(labelled(label)));
        }
        return values;
    }

    /** Every series there is, which is every switch in its group. */
    private static List<SettingKey> series() {
        return switches().stream().filter(key -> key.group().equals("sequences")).toList();
    }

    /**
     * Every kind of change that can be made to an accepted request: every switch in its group but
     * the one that switches them all off.
     */
    private static List<SettingKey> kindsOfChange() {
        return switches().stream()
                .filter(key -> key.group().equals("mutation") && !key.name().equals("violations"))
                .toList();
    }

    /** These switches, all off, the way a file of settings gives them. */
    private static Map<String, String> allOff(List<SettingKey> keys) {
        Map<String, String> values = new TreeMap<>();
        keys.forEach(key -> values.put(key.fullName(), "false"));
        return values;
    }

    /** A generator for this document, following this plan with these settings. */
    private static RandomTestCaseGenerator generator(ApiModel model, Campaign plan,
            Settings settings) {
        return new RandomTestCaseGenerator(model, 7L, List.of(), plan, settings);
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
