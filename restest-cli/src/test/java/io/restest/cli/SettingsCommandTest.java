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
package io.restest.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.settings.SettingKey;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Changing how the tool behaves, from the command line.
 *
 * <p>Three options. {@code --print-settings} writes out every number the tool was going to use,
 * with where each one came from, so that somebody can save it and change a line; {@code --settings}
 * hands the file back; {@code --set} changes one of them without a file at all. What matters most
 * here is that a run which could not be configured the way somebody asked does not happen: the
 * numbers decide what the run does, so running with different ones would answer a question nobody
 * put.
 */
class SettingsCommandTest {

    private static WireMockServer api;

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        answersEverythingWith("{}");
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Nested
    @DisplayName("writing the settings out")
    class Printing {

        @Test
        @DisplayName("the settings can be printed, and printing them needs no document, because "
                + "it is a question about the tool rather than about an API")
        void they_can_be_printed() {
            assertThat(run("run", "--print-settings")).isZero();

            assertThat(screen.toString())
                    .contains("engine:")
                    .contains("maxConcurrency: 16")
                    .contains("# default");
        }

        @Test
        @DisplayName("every setting there is appears, so the printed file is also the list of what "
                + "can be changed at all")
        void every_setting_appears() {
            run("run", "--print-settings");

            assertThat(SettingKey.all()).allSatisfy(key ->
                    assertThat(screen.toString())
                            .describedAs("%s is missing from what --print-settings writes",
                                    key.fullName())
                            .contains(key.name() + ":"));
        }

        @Test
        @DisplayName("what is printed is what this command would have used, --set and all")
        void what_is_printed_is_what_would_be_used() {
            assertThat(run("run", "--set", "engine.maxConcurrency=3", "--print-settings"))
                    .isZero();

            assertThat(screen.toString())
                    .contains("maxConcurrency: 3")
                    .describedAs("and says which of the four places decided it")
                    .contains("# command line");
        }

        @Test
        @DisplayName("what is printed is a file the tool reads back as the very same values, "
                + "which is what makes it something to save and change one line of")
        void what_is_printed_can_be_handed_back(@TempDir Path directory) throws Exception {
            run("run", "--print-settings");
            String first = screen.toString();
            Path saved = directory.resolve("mine.yaml");
            Files.writeString(saved, first);
            screen.getBuffer().setLength(0);

            assertThat(run("run", "--settings", saved.toString(), "--print-settings")).isZero();

            // The notes change, and only the notes: every value now comes from the file, because
            // the file states every one of them. What each value *is* has to be untouched, and
            // that is what is compared.
            assertThat(valuesIn(screen.toString()))
                    .describedAs("a value printed one way and read back another would make the "
                            + "printed file a trap rather than a starting point")
                    .isEqualTo(valuesIn(first));
            assertThat(screen.toString()).contains("# file");
        }

        /** Every {@code key: value} line of a printed file, with the notes and comments dropped. */
        private static List<String> valuesIn(String printed) {
            return printed.lines()
                    .map(line -> line.replaceAll("\\s*#.*$", "").stripTrailing())
                    .filter(line -> !line.isBlank())
                    .toList();
        }

        @Test
        @DisplayName("a value with a line break in it comes back out of the printed file as "
                + "itself, rather than splitting one setting across two lines")
        void an_awkward_value_survives_the_file(@TempDir Path directory) throws Exception {
            String awkward = "mine/1.0\ninjected: 99";
            run("run", "--set", "engine.userAgent=" + awkward, "--print-settings");
            Path saved = directory.resolve("mine.yaml");
            Files.writeString(saved, screen.toString());
            screen.getBuffer().setLength(0);

            assertThat(run("run", "--settings", saved.toString(), "--print-settings")).isZero();

            assertThat(screen.toString())
                    .contains("userAgent: \"mine/1.0\\ninjected: 99\"")
                    .describedAs("and the break has not become a setting of its own")
                    .doesNotContain("\ninjected: 99");
        }

        @Test
        @DisplayName("asking for the plan and the settings at once is refused, since printing "
                + "either alone would read as an answer to both")
        void both_questions_at_once() {
            assertThat(run("run", "--print-settings", "--print-campaign"))
                    .isEqualTo(ExitCode.BAD_COMMAND_LINE);
            assertThat(problems.toString()).contains("one or the other");
        }
    }

    @Nested
    @DisplayName("a run that could not be configured as asked does not start")
    class Refusals {

        @Test
        @DisplayName("a setting that does not exist answers 2, naming the one it looks like")
        void a_misspelt_setting(@TempDir Path directory) {
            assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--set", "engine.maxConcurrancy=8",
                    "--out", directory.resolve("out").toString()))
                    .isEqualTo(ExitCode.BAD_COMMAND_LINE);

            assertThat(problems.toString())
                    .contains("there is no setting called 'engine.maxConcurrancy'")
                    .contains("engine.maxConcurrency");
        }

        @Test
        @DisplayName("a value the setting cannot take answers 2, saying what it wanted")
        void a_value_of_the_wrong_kind(@TempDir Path directory) {
            assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--set", "engine.maxConcurrency=lots",
                    "--out", directory.resolve("out").toString()))
                    .isEqualTo(ExitCode.BAD_COMMAND_LINE);

            assertThat(problems.toString()).contains("takes a whole number");
        }

        @Test
        @DisplayName("a setting that does not exist is refused even when the command was going to "
                + "print a plan rather than test anything, so the mistake is not left to be found "
                + "later")
        void a_bad_setting_is_refused_before_anything_is_printed() {
            assertThat(run("run", "--print-campaign", "--set", "engine.nonsense=1"))
                    .isEqualTo(ExitCode.BAD_COMMAND_LINE);

            assertThat(screen.toString()).doesNotContain("strategies:");
            assertThat(problems.toString()).contains("there is no setting called");
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {
                "engine.slowdownFactor=1e400",
                "engine.readTimeout=9223372036854775807",
                "engine.readTimeout=9223372036854775807h",
                "engine.readTimeout=PT0.0005S",
                "generation.lowestNumber=1e999999999",
        })
        @DisplayName("a value larger or finer than the tool can write down answers 2 like any "
                + "other, rather than a stack trace and 4")
        void values_the_tool_cannot_write_down(String typed) {
            assertThat(run("run", "--set", typed, "--print-settings"))
                    .isEqualTo(ExitCode.BAD_COMMAND_LINE);

            assertThat(problems.toString())
                    .describedAs("a person who typed a number sees a sentence, not a stack trace")
                    .startsWith("restest: ")
                    .doesNotContain("\tat io.restest");
            assertThat(screen.toString()).isEmpty();
        }

        @Test
        @DisplayName("a settings file that cannot be read answers 2, and nothing is sent")
        void a_file_that_cannot_be_read(@TempDir Path directory) {
            assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--settings", directory.resolve("nowhere.yaml").toString(),
                    "--out", directory.resolve("out").toString()))
                    .isEqualTo(ExitCode.BAD_COMMAND_LINE);

            assertThat(problems.toString()).contains("could not be read");
            assertThat(directory.resolve("out")).doesNotExist();
        }
    }

    @Nested
    @DisplayName("a run that was configured")
    class ARunThatWasConfigured {

        @Test
        @DisplayName("one request at a time is one option, and the run happens")
        void one_request_at_a_time(@TempDir Path directory) throws Exception {
            Path out = directory.resolve("out");

            assertThat(run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--set", "engine.maxConcurrency=1", "--out", out.toString()))
                    .isBetween(0, 1);

            assertThat(settingsIn(out.resolve("report.json")))
                    .describedAs("and the report says so, for whoever reads the results later")
                    .contains("engine.maxConcurrency 1 command line");
        }

        @Test
        @DisplayName("the report carries every setting and where each one came from, so a "
                + "directory of results carries the configuration that produced it")
        void the_report_carries_the_settings(@TempDir Path directory) throws Exception {
            Path out = directory.resolve("out");
            Path file = directory.resolve("mine.yaml");
            Files.writeString(file, """
                    report:
                      writeUpsInTotal: 7
                    """);

            run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--settings", file.toString(), "--out", out.toString());

            List<String> written = settingsIn(out.resolve("report.json"));
            assertThat(written).hasSameSizeAs(SettingKey.all());
            assertThat(written).contains("report.writeUpsInTotal 7 file");
            assertThat(written).contains("engine.maxConcurrency 16 default");
        }

        @Test
        @DisplayName("the run says on the screen that it was configured, because an environment "
                + "variable is invisible in the command somebody typed")
        void the_run_says_it_was_configured(@TempDir Path directory) {
            run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--set", "engine.maxConcurrency=8",
                    "--out", directory.resolve("out").toString());

            assertThat(screen.toString())
                    .contains("1 setting(s) are not what RESTest does by default")
                    .contains("--print-settings");
        }

        @Test
        @DisplayName("and says nothing about settings when nobody changed any")
        void a_plain_run_says_nothing(@TempDir Path directory) {
            run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "1s",
                    "--out", directory.resolve("out").toString());

            assertThat(screen.toString()).doesNotContain("not what RESTest does by default");
        }

        @Test
        @DisplayName("a setting the run actually obeys: the same run writes findings at the usual "
                + "setting and none when told to quote none, while counting the same faults")
        void a_setting_the_run_obeys(@TempDir Path directory) throws Exception {
            Path usual = directory.resolve("usual");
            Path quiet = directory.resolve("quiet");
            // An API that answers 200 with a body its own description does not allow, so every
            // reply is a fault and there is certainly something for the report to quote.
            api.resetAll();
            api.stubFor(any(urlMatching(".*")).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("\"not the shape the document promised\"")));
            try {
                run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "2s",
                        "--seed", "5", "--out", usual.toString());
                run("run", "pet-shelter.yaml", "--url", api.baseUrl(), "--budget", "2s",
                        "--seed", "5", "--set", "report.writeUpsInTotal=0",
                        "--out", quiet.toString());
            } finally {
                answersEverythingWith("{}");
            }

            assertThat(findingsIn(usual))
                    .describedAs("the setting is only worth testing against a run that would "
                            + "otherwise have written something")
                    .isNotZero();
            assertThat(findingsIn(quiet)).isZero();
            assertThat(faultsIn(quiet))
                    .describedAs("nothing is quoted, and everything is still counted")
                    .isNotZero();
        }
    }

    /** Puts the shared API back the way every test here but one expects to find it. */
    private static void answersEverythingWith(String body) {
        api.resetAll();
        api.stubFor(any(urlMatching(".*")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    /** How many faults a run wrote out whole. */
    private static int findingsIn(Path out) throws Exception {
        JsonValue report = JsonText.read(Files.readString(out.resolve("report.json")));
        return ((JsonValue.JsonArray) member(report, "findings")).elements().size();
    }

    /** And how many it found, whether or not it wrote them out. */
    private static int faultsIn(Path out) throws Exception {
        JsonValue report = JsonText.read(Files.readString(out.resolve("report.json")));
        return ((JsonValue.JsonNumber) member(member(report, "totals"), "faults"))
                .value().intValueExact();
    }

    /** Every setting the report states, as "name value source", for reading in a test. */
    private static List<String> settingsIn(Path report) throws Exception {
        JsonValue written = JsonText.read(Files.readString(report));
        JsonValue.JsonArray settings = (JsonValue.JsonArray) member(written, "settings");
        return settings.elements().stream()
                .map(row -> text(member(row, "key")) + " " + text(member(row, "value")) + " "
                        + text(member(row, "source")))
                .toList();
    }

    private static JsonValue member(JsonValue held, String name) {
        return ((JsonValue.JsonObject) held).members().get(name);
    }

    private static String text(JsonValue value) {
        return ((JsonValue.JsonString) value).value();
    }

    private int run(String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);
        try {
            return Restest.run(arguments, out, err);
        } finally {
            out.flush();
            err.flush();
        }
    }
}
