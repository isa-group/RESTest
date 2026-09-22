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
package io.restest.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The settings a run used, together with where each of their values came from.
 *
 * <p>The half that matters is the second one. "How many requests at once" is only half a question
 * when four places can answer it, and a results directory that does not carry the answer to the
 * other half is one nobody can reproduce.
 */
class SettingsInEffectTest {

    static java.util.List<SettingKey> everySetting() {
        return SettingKey.all();
    }

    private static final SettingKey MAX_CONCURRENCY =
            SettingKey.named("engine.maxConcurrency").orElseThrow();

    private static final SettingKey READ_TIMEOUT =
            SettingKey.named("engine.readTimeout").orElseThrow();

    @Test
    @DisplayName("a setting nobody named came from the code itself")
    void unnamed_settings_came_from_the_code() {
        SettingsInEffect nothing = SettingsInEffect.of(Settings.defaults());

        assertThat(nothing.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.DEFAULT);
        assertThat(nothing.rows()).allSatisfy(row ->
                assertThat(row.source()).isEqualTo(SettingSource.DEFAULT));
    }

    @Test
    @DisplayName("a setting somebody named says where it was named")
    void a_named_setting_says_where_from() {
        SettingsInEffect configured = new SettingsInEffect(
                Settings.from(Map.of("engine.maxConcurrency", "8")),
                Map.of("engine.maxConcurrency", SettingSource.COMMAND_LINE));

        assertThat(configured.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.COMMAND_LINE);
        assertThat(configured.sourceOf(READ_TIMEOUT))
                .describedAs("its neighbours are still what the code decided")
                .isEqualTo(SettingSource.DEFAULT);
    }

    @Test
    @DisplayName("a value nobody named and nobody defaulted says it was worked out, rather than "
            + "claiming to be what the tool does by default")
    void a_worked_out_value_says_so() {
        SettingKey start = SettingKey.named("engine.initialConcurrency").orElseThrow();
        SettingsInEffect careful = new SettingsInEffect(
                Settings.from(Map.of("engine.maxConcurrency", "1")),
                Map.of("engine.maxConcurrency", SettingSource.COMMAND_LINE));

        assertThat(careful.settings().engine().initialConcurrency()).isEqualTo(1);
        assertThat(careful.sourceOf(start))
                .describedAs("calling this a default would put two different values under one word "
                        + "in two results directories, with nothing to explain the difference")
                .isEqualTo(SettingSource.WORKED_OUT);
        assertThat(careful.asAFile()).contains("initialConcurrency: 1")
                .contains("# worked out");
    }

    @Test
    @DisplayName("and a value that really is the default still says default")
    void an_untouched_value_says_default() {
        SettingKey start = SettingKey.named("engine.initialConcurrency").orElseThrow();

        assertThat(SettingsInEffect.of(Settings.defaults()).sourceOf(start))
                .isEqualTo(SettingSource.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("every setting gets a row, whether or not anybody changed it, so two runs can be "
            + "compared line by line")
    void every_setting_gets_a_row(SettingKey key) {
        assertThat(SettingsInEffect.of(Settings.defaults()).rows())
                .anySatisfy(row -> assertThat(row.key()).isEqualTo(key));
    }

    /**
     * The rule the settings live by, asked of every setting and every awkward value at once.
     *
     * <p>A value is accepted only if the tool can write it back out and read it again as itself.
     * That rule is what stops a line of a settings file being accepted and then killing the run
     * much later, where it is printed or recorded - which is how three ordinary things to type
     * behaved before it was written down. Nothing here cares which of these values are accepted;
     * it cares that every accepted one survives the trip.
     */
    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("whatever a setting accepts, it can write back out and read again as itself")
    void whatever_is_accepted_survives_being_written_down(SettingKey key) {
        for (String awkward : AWKWARD) {
            Settings accepted;
            try {
                accepted = Settings.from(Map.of(key.fullName(), awkward));
            } catch (SettingsException refused) {
                continue;
            }
            SettingsInEffect printed = SettingsInEffect.of(accepted);
            assertThat(Settings.from(readBack(printed)))
                    .describedAs("%s accepted '%s' and then could not read back what it printed:"
                            + "%n%s", key.fullName(), awkward, printed.asAFile())
                    .isEqualTo(accepted);
        }
    }

    /** Values worth trying against every setting, whatever kind of thing that setting holds. */
    private static final List<String> AWKWARD = List.of(
            "0", "1", "-1", "2", "1000000",
            "1e400", "1E+2", "1.50", "0.000001", "1e999999999",
            "9223372036854775807", "9223372036854775808", "-9223372036854775809",
            "true", "false", "TRUE",
            "0s", "1ms", "30s", "5m", "2h", "PT0.0005S", "9223372036854775807h", "PT1M30S",
            "a word", "two\nlines", "a\ttab", "a \"quote\"", "a\\backslash",
            "acontrol", "a\uD800half a pair", "reserved ￾", "an emoji 😀",
            " leading and trailing ", "#hash", "- dash", "yes:", "");

    @Test
    @DisplayName("the rows come out in the order settings are printed")
    void rows_are_in_printing_order() {
        assertThat(SettingsInEffect.of(Settings.defaults()).rows())
                .extracting(SettingsInEffect.Row::key)
                .containsExactlyElementsOf(SettingKey.all());
    }

    @Test
    @DisplayName("what is printed is grouped, says what each setting does, and notes where its "
            + "value came from")
    void what_is_printed() {
        Map<String, SettingSource> from = new LinkedHashMap<>();
        from.put("engine.maxConcurrency", SettingSource.ENVIRONMENT);
        String printed = new SettingsInEffect(
                Settings.from(Map.of("engine.maxConcurrency", "8")), from).asAFile();

        assertThat(printed)
                .contains("engine:")
                .contains("report:")
                .contains("maxConcurrency: 8")
                .describedAs("the note says which of the four places decided this one")
                .contains("# environment")
                .describedAs("and every setting says what it does, since this file is also the "
                        + "list of what can be changed at all")
                .contains("# " + MAX_CONCURRENCY.meaning())
                .describedAs("with a word on how to change one")
                .contains("--settings")
                .contains("--set engine.maxConcurrency=8")
                .contains("RESTEST_ENGINE_MAX_CONCURRENCY=8");
    }

    @Test
    @DisplayName("a length of time and a piece of text are quoted, so reading the file back gives "
            + "the same value rather than a number or a word")
    void values_that_need_quoting_are_quoted() {
        String printed = SettingsInEffect.of(Settings.defaults()).asAFile();

        assertThat(printed)
                .contains("readTimeout: \"30s\"")
                .contains("userAgent: \"RESTest/2.0\"")
                .describedAs("and nothing else is, because a number written as text would be "
                        + "read back as text")
                .contains("maxConcurrency: 16")
                .contains("followRedirects: false");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "a \"quoted\" name\\here",
            "two\nlines",
            "a\ttab and a\rreturn",
            "an invisible \u0001 character",
            "trailing space ",
    })
    @DisplayName("a value with something awkward in it comes back out of the printed file exactly "
            + "as it went in, which is what makes printing it safe")
    void awkward_text_survives_the_round_trip(String awkward) {
        Settings odd = Settings.from(Map.of("engine.userAgent", awkward));

        assertThat(readBack(SettingsInEffect.of(odd)).get("engine.userAgent"))
                .describedAs("a line break written as itself would split one setting across two "
                        + "lines, and come back with the break turned into a space")
                .isEqualTo(awkward);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "generation.lowestNumber, 1E+2",
            "generation.roomAboveIt, 1.50",
            "generation.lowestNumber, 0.000001",
            "engine.slowdownFactor, 1.500",
            "engine.readTimeout, 3600s",
            "engine.maxRetainedResponseBytes, 9223372036854775807",
    })
    @DisplayName("a value that is written to a particular number of places keeps them: how many "
            + "places a number has is itself what one of these settings decides")
    void the_exact_number_survives_the_round_trip(String key, String typed) {
        Settings asked = Settings.from(Map.of(key, typed));

        assertThat(Settings.from(Map.of(key, readBack(SettingsInEffect.of(asked)).get(key))))
                .describedAs("%s written as %s came back as something else", key, typed)
                .isEqualTo(asked);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "a\uD800lone half of a pair",
            "one of the two YAML reserves \uFFFE",
            "an emoji \uD83D\uDE00, which is a pair and is fine",
    })
    @DisplayName("a letter YAML will not carry as itself is written as an escape, so the printed "
            + "file is one the tool can still read")
    void letters_yaml_will_not_carry(String awkward) {
        Settings odd = Settings.from(Map.of("engine.userAgent", awkward));

        assertThat(readBack(SettingsInEffect.of(odd)).get("engine.userAgent"))
                .isEqualTo(awkward);
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("every setting comes back out of the printed file with the value it went in with")
    void every_value_survives_the_round_trip(SettingKey key) {
        assertThat(readBack(SettingsInEffect.of(Settings.defaults())).get(key.fullName()))
                .isEqualTo(Settings.defaults().written(key));
    }

    /** What a printed file says, read back the way the command line reads one. */
    private static Map<String, String> readBack(SettingsInEffect printed) {
        JsonValue read = YamlText.read(printed.asAFile(), "a printed file");
        Map<String, String> values = new LinkedHashMap<>();
        ((JsonValue.JsonObject) read).members().forEach((group, held) ->
                ((JsonValue.JsonObject) held).members().forEach((key, value) ->
                        values.put(group + "." + key, written(value))));
        return values;
    }

    private static String written(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString text -> text.value();
            case JsonValue.JsonNumber number -> number.value().toPlainString();
            case JsonValue.JsonBoolean yesOrNo -> String.valueOf(yesOrNo.value());
            default -> throw new AssertionError("a printed file held " + value);
        };
    }
}
