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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.settings.SettingKey;
import io.restest.core.settings.SettingSource;
import io.restest.core.settings.SettingsException;
import io.restest.core.settings.SettingsInEffect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gathering a run's settings from the four places they can be given.
 *
 * <p>What is being checked is mostly the order: which of the four wins when two of them say
 * something about the same setting. The environment is handed in rather than read, which is what
 * lets a test have one at all - and is the same reason the rest of the tool is handed its settings
 * instead of reaching for them.
 */
class SettingsFromEverywhereTest {

    private static final SettingKey MAX_CONCURRENCY =
            SettingKey.named("engine.maxConcurrency").orElseThrow();

    private static final SettingKey READ_TIMEOUT =
            SettingKey.named("engine.readTimeout").orElseThrow();

    @Test
    @DisplayName("nothing given anywhere is what RESTest does when nobody has said otherwise")
    void nothing_given() {
        SettingsInEffect gathered =
                SettingsFromEverywhere.gather(Optional.empty(), Map.of(), List.of());

        assertThat(gathered.settings())
                .isEqualTo(io.restest.core.settings.Settings.defaults());
        assertThat(gathered.rows()).allSatisfy(row ->
                assertThat(row.source()).isEqualTo(SettingSource.DEFAULT));
    }

    @Nested
    @DisplayName("each of the four places on its own")
    class OneAtATime {

        @Test
        @DisplayName("a file")
        void from_a_file(@TempDir Path directory) throws Exception {
            Path file = fileSaying(directory, """
                    engine:
                      maxConcurrency: 8
                      readTimeout: 2m
                    """);

            SettingsInEffect gathered =
                    SettingsFromEverywhere.gather(Optional.of(file), Map.of(), List.of());

            assertThat(gathered.settings().engine().maxConcurrency()).isEqualTo(8);
            assertThat(gathered.settings().engine().readTimeout())
                    .isEqualTo(Duration.ofMinutes(2));
            assertThat(gathered.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.FILE);
        }

        @Test
        @DisplayName("a file written one setting per line, the way somebody who has only seen the "
                + "command line would write it")
        void from_a_flat_file(@TempDir Path directory) throws Exception {
            Path file = fileSaying(directory, "engine.maxConcurrency: 8\n");

            assertThat(SettingsFromEverywhere.gather(Optional.of(file), Map.of(), List.of())
                    .settings().engine().maxConcurrency()).isEqualTo(8);
        }

        @Test
        @DisplayName("the environment, which is how a container is configured")
        void from_the_environment() {
            SettingsInEffect gathered = SettingsFromEverywhere.gather(Optional.empty(),
                    Map.of("RESTEST_ENGINE_MAX_CONCURRENCY", "8"), List.of());

            assertThat(gathered.settings().engine().maxConcurrency()).isEqualTo(8);
            assertThat(gathered.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.ENVIRONMENT);
        }

        @Test
        @DisplayName("the command line")
        void from_the_command_line() {
            SettingsInEffect gathered = SettingsFromEverywhere.gather(Optional.empty(), Map.of(),
                    List.of("engine.maxConcurrency=8"));

            assertThat(gathered.settings().engine().maxConcurrency()).isEqualTo(8);
            assertThat(gathered.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.COMMAND_LINE);
        }
    }

    @Nested
    @DisplayName("a value nobody gave that is not a default either")
    class WorkedOut {

        private static final SettingKey START =
                SettingKey.named("engine.initialConcurrency").orElseThrow();

        @Test
        @DisplayName("says it was worked out, because calling it a default would put two different "
                + "values under one word in two results directories")
        void a_derived_value_says_so() {
            SettingsInEffect careful = SettingsFromEverywhere.gather(Optional.empty(), Map.of(),
                    List.of("engine.maxConcurrency=1"));

            assertThat(careful.settings().engine().initialConcurrency()).isEqualTo(1);
            assertThat(careful.sourceOf(START)).isEqualTo(SettingSource.WORKED_OUT);
        }

        @Test
        @DisplayName("and a value somebody did give says where they gave it, never worked out")
        void a_given_value_is_never_called_worked_out() {
            SettingsInEffect given = SettingsFromEverywhere.gather(Optional.empty(), Map.of(),
                    List.of("engine.maxConcurrency=1", "engine.initialConcurrency=1"));

            assertThat(given.sourceOf(START)).isEqualTo(SettingSource.COMMAND_LINE);
        }

        @Test
        @DisplayName("and a run nobody configured has nothing worked out at all")
        void a_plain_run_works_nothing_out() {
            assertThat(SettingsFromEverywhere.gather(Optional.empty(), Map.of(), List.of()).rows())
                    .allSatisfy(row -> assertThat(row.source()).isEqualTo(SettingSource.DEFAULT));
        }
    }

    @Nested
    @DisplayName("when two of them say something about the same setting")
    class WhoWins {

        @Test
        @DisplayName("the command line wins over the environment, which wins over a file")
        void the_later_layer_wins(@TempDir Path directory) throws Exception {
            Path file = fileSaying(directory, """
                    engine:
                      maxConcurrency: 8
                    """);

            assertThat(SettingsFromEverywhere.gather(Optional.of(file),
                    Map.of("RESTEST_ENGINE_MAX_CONCURRENCY", "12"), List.of())
                    .settings().engine().maxConcurrency()).isEqualTo(12);

            SettingsInEffect allThree = SettingsFromEverywhere.gather(Optional.of(file),
                    Map.of("RESTEST_ENGINE_MAX_CONCURRENCY", "12"),
                    List.of("engine.maxConcurrency=20"));

            assertThat(allThree.settings().engine().maxConcurrency()).isEqualTo(20);
            assertThat(allThree.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.COMMAND_LINE);
        }

        @Test
        @DisplayName("and a setting only one of them mentions keeps the value that one gave it")
        void the_others_are_left_alone(@TempDir Path directory) throws Exception {
            Path file = fileSaying(directory, """
                    engine:
                      readTimeout: 2m
                    """);

            SettingsInEffect gathered = SettingsFromEverywhere.gather(Optional.of(file),
                    Map.of(), List.of("engine.maxConcurrency=20"));

            assertThat(gathered.settings().engine().readTimeout())
                    .isEqualTo(Duration.ofMinutes(2));
            assertThat(gathered.sourceOf(READ_TIMEOUT)).isEqualTo(SettingSource.FILE);
            assertThat(gathered.sourceOf(MAX_CONCURRENCY)).isEqualTo(SettingSource.COMMAND_LINE);
        }

        @Test
        @DisplayName("the last of several --set options is the one that counts")
        void the_last_one_typed_counts() {
            assertThat(SettingsFromEverywhere.gather(Optional.empty(), Map.of(),
                    List.of("engine.maxConcurrency=8", "engine.maxConcurrency=20"))
                    .settings().engine().maxConcurrency()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("a --set without a value says how one is written")
        void a_set_without_a_value() {
            assertThatThrownBy(() -> SettingsFromEverywhere.gather(Optional.empty(), Map.of(),
                    List.of("engine.maxConcurrency")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("engine.maxConcurrency=8");

            assertThatThrownBy(() -> SettingsFromEverywhere.gather(Optional.empty(), Map.of(),
                    List.of("=8")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("--set takes a setting and the value to give it");
        }

        @Test
        @DisplayName("a file that is not there says so, naming it")
        void a_file_that_is_not_there(@TempDir Path directory) {
            Path missing = directory.resolve("nowhere.yaml");

            assertThatThrownBy(() -> SettingsFromEverywhere.gather(Optional.of(missing), Map.of(),
                    List.of()))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("could not be read");
        }

        @Test
        @DisplayName("a file that is not a list of groups says what one looks like")
        void a_file_that_is_not_settings(@TempDir Path directory) throws Exception {
            Path file = fileSaying(directory, "- engine\n- report\n");

            assertThatThrownBy(() -> SettingsFromEverywhere.gather(Optional.of(file), Map.of(),
                    List.of()))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("--print-settings");
        }

        @Test
        @DisplayName("a file naming a setting that does not exist names the one it looks like")
        void a_file_naming_nothing(@TempDir Path directory) throws Exception {
            Path file = fileSaying(directory, """
                    engine:
                      maxConcurrancy: 8
                    """);

            assertThatThrownBy(() -> SettingsFromEverywhere.gather(Optional.of(file), Map.of(),
                    List.of()))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("engine.maxConcurrency");
        }

        @Test
        @DisplayName("a setting written as a list, or left with no value, is refused rather than "
                + "quietly read as something else")
        void values_no_setting_could_take(@TempDir Path directory) throws Exception {
            assertThatThrownBy(() -> SettingsFromEverywhere.gather(
                    Optional.of(fileSaying(directory, "engine:\n  maxConcurrency: [8, 12]\n")),
                    Map.of(), List.of()))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("is a list");

            assertThatThrownBy(() -> SettingsFromEverywhere.gather(
                    Optional.of(fileSaying(directory, "engine:\n  maxConcurrency:\n")),
                    Map.of(), List.of()))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("has no value");

            assertThatThrownBy(() -> SettingsFromEverywhere.gather(
                    Optional.of(fileSaying(directory, "engine:\n  maxConcurrency:\n    more: 8\n")),
                    Map.of(), List.of()))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("grouped once");
        }
    }

    @Test
    @DisplayName("an empty file asks for nothing, which is a thing somebody may genuinely hand over")
    void an_empty_file(@TempDir Path directory) throws Exception {
        Path file = fileSaying(directory, "");

        assertThat(SettingsFromEverywhere.gather(Optional.of(file), Map.of(), List.of())
                .settings()).isEqualTo(io.restest.core.settings.Settings.defaults());
    }

    @Test
    @DisplayName("an environment variable that begins RESTEST_ and is not a setting is left alone")
    void an_unrelated_variable_is_not_refused() {
        assertThat(SettingsFromEverywhere.gather(Optional.empty(),
                Map.of("RESTEST_HOME", "/opt/restest", "RESTEST_ENGINE_MAX_CONCURRENCY", "8"),
                List.of()).settings().engine().maxConcurrency())
                .describedAs("a wrapper script's own variable, or a later version's, is not this "
                        + "version's business to refuse")
                .isEqualTo(8);
    }

    @Test
    @DisplayName("a value out of the file is read as the kind of thing its setting takes, however "
            + "the file happened to write it")
    void the_file_writes_a_value_either_way(@TempDir Path directory) throws Exception {
        Path quoted = fileSaying(directory, "engine:\n  maxConcurrency: \"8\"\n");

        assertThat(SettingsFromEverywhere.gather(Optional.of(quoted), Map.of(), List.of())
                .settings().engine().maxConcurrency()).isEqualTo(8);
    }

    private static Path fileSaying(Path directory, String text) throws Exception {
        Path file = Files.createTempFile(directory, "settings", ".yaml");
        Files.writeString(file, text);
        return file;
    }
}
