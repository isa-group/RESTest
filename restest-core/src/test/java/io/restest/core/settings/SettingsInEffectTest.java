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

import java.util.LinkedHashMap;
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

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("every setting gets a row, whether or not anybody changed it, so two runs can be "
            + "compared line by line")
    void every_setting_gets_a_row(SettingKey key) {
        assertThat(SettingsInEffect.of(Settings.defaults()).rows())
                .anySatisfy(row -> assertThat(row.key()).isEqualTo(key));
    }

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

    @Test
    @DisplayName("text with a quotation mark in it survives being printed")
    void awkward_text_is_escaped() {
        Settings odd = Settings.from(Map.of("engine.userAgent", "a \"quoted\" name\\here"));

        assertThat(SettingsInEffect.of(odd).asAFile())
                .contains("userAgent: \"a \\\"quoted\\\" name\\\\here\"");
    }
}
