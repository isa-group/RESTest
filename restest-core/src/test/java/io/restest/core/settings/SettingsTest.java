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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every number somebody decided about how RESTest behaves, gathered into one value a run is handed.
 *
 * <p>Three promises are checked here, and they are the ones everything built on settings depends
 * on. A name that is not a setting is refused rather than ignored; a value that could not be used
 * is refused with the reason; and the order values were typed in never decides whether they are
 * accepted.
 */
class SettingsTest {

    static List<SettingKey> everySetting() {
        return SettingKey.all();
    }

    @Nested
    @DisplayName("what a run does when nobody has said otherwise")
    class Defaults {

        @Test
        @DisplayName("there is a usable answer for every group without anybody configuring one")
        void every_group_has_defaults() {
            Settings settings = Settings.defaults();

            assertThat(settings.engine()).isNotNull();
            assertThat(settings.schedule()).isNotNull();
            assertThat(settings.generation()).isNotNull();
            assertThat(settings.memory()).isNotNull();
            assertThat(settings.document()).isNotNull();
            assertThat(settings.report()).isNotNull();
        }

        @Test
        @DisplayName("naming nothing is the same as naming nothing in particular")
        void naming_nothing_gives_the_defaults() {
            assertThat(Settings.from(Map.of())).isEqualTo(Settings.defaults());
        }

        @ParameterizedTest
        @MethodSource("io.restest.core.settings.SettingsTest#everySetting")
        @DisplayName("every setting has a value, and it is the one the code decided")
        void every_setting_has_a_default(SettingKey key) {
            assertThat(Settings.defaults().written(key))
                    .describedAs("%s has no value to print", key.fullName())
                    .isNotBlank();
        }
    }

    @Nested
    @DisplayName("the list of settings and the settings themselves agree")
    class NothingIsUnreachable {

        /**
         * Every value inside every group is reachable by a name somebody can type.
         *
         * <p>This is the test that stops the list of settings drifting away from the settings. A
         * number added to a group without a key beside it would be configurable by nobody and
         * invisible to {@code --print-settings}, and nothing else in the build would notice.
         */
        @Test
        @DisplayName("every value inside every group has a name somebody can type")
        void every_value_has_a_key() {
            Map<String, List<String>> missing = new LinkedHashMap<>();
            groups().forEach((group, held) -> {
                for (RecordComponent value : held.getRecordComponents()) {
                    if (SettingKey.named(group + "." + value.getName()).isEmpty()) {
                        missing.computeIfAbsent(group, ignored -> new ArrayList<>())
                                .add(value.getName());
                    }
                }
            });
            assertThat(missing)
                    .describedAs("a number inside a group with no key beside it can be changed by "
                            + "nobody, and --print-settings would never mention it")
                    .isEmpty();
        }

        @ParameterizedTest
        @MethodSource("io.restest.core.settings.SettingsTest#everySetting")
        @DisplayName("and every name somebody can type is a value inside a group")
        void every_key_is_a_value(SettingKey key) {
            Class<?> held = groups().get(key.group());
            assertThat(held)
                    .describedAs("%s names a group that does not exist", key.fullName())
                    .isNotNull();
            assertThat(List.of(held.getRecordComponents()))
                    .describedAs("%s is listed as a setting and is not one", key.fullName())
                    .anySatisfy(value -> assertThat(value.getName()).isEqualTo(key.name()));
        }

        private Map<String, Class<?>> groups() {
            Map<String, Class<?>> byName = new LinkedHashMap<>();
            for (RecordComponent group : Settings.class.getRecordComponents()) {
                byName.put(group.getName(), group.getType());
            }
            return byName;
        }
    }

    @Nested
    @DisplayName("values somebody named")
    class ValuesGiven {

        @Test
        @DisplayName("a value given is the value used, and everything else is left alone")
        void one_value_changes_one_thing() {
            Settings changed = Settings.from(Map.of("engine.maxConcurrency", "32"));

            assertThat(changed.engine().maxConcurrency()).isEqualTo(32);
            assertThat(changed.engine().minConcurrency())
                    .isEqualTo(Settings.defaults().engine().minConcurrency());
            assertThat(changed.report()).isEqualTo(Settings.defaults().report());
        }

        @Test
        @DisplayName("a length of time is read in the spelling people write, and a yes-or-no in "
                + "either case")
        void kinds_are_read_as_they_are_written() {
            Settings changed = Settings.from(Map.of(
                    "engine.readTimeout", "2m",
                    "schedule.stragglerGrace", "500ms",
                    "engine.followRedirects", "TRUE",
                    "engine.userAgent", "mine/1.0",
                    "engine.slowdownFactor", "1.5",
                    "generation.roomAboveIt", "12345.5"));

            assertThat(changed.engine().readTimeout()).isEqualTo(Duration.ofMinutes(2));
            assertThat(changed.schedule().stragglerGrace()).isEqualTo(Duration.ofMillis(500));
            assertThat(changed.engine().followRedirects()).isTrue();
            assertThat(changed.engine().userAgent()).isEqualTo("mine/1.0");
            assertThat(changed.engine().slowdownFactor()).isEqualTo(1.5);
            assertThat(changed.generation().roomAboveIt().doubleValue()).isEqualTo(12345.5);
        }

        @Test
        @DisplayName("one request at a time is one line, which is the simplest thing anybody will "
                + "ask these settings for")
        void one_request_at_a_time() {
            Settings careful = Settings.from(Map.of("engine.maxConcurrency", "1"));

            assertThat(careful.engine())
                    .describedAs("where the engine starts is where to begin inside the range, so "
                            + "moving the range moves it too")
                    .isEqualTo(Settings.defaults().engine().withoutConcurrency());
        }

        @Test
        @DisplayName("raising the fewest requests in flight moves the starting number up with it")
        void raising_the_floor_moves_the_start() {
            Settings busy = Settings.from(Map.of(
                    "engine.minConcurrency", "8",
                    "engine.maxConcurrency", "32"));

            assertThat(busy.engine().initialConcurrency()).isEqualTo(8);
        }

        @Test
        @DisplayName("a starting number named outright is still refused when it lies outside the "
                + "range, because that is a value somebody stated rather than one worked out")
        void a_start_named_outright_is_still_checked() {
            assertThatThrownBy(() -> Settings.from(Map.of(
                    "engine.maxConcurrency", "4",
                    "engine.initialConcurrency", "9")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("initialConcurrency (9)")
                    .hasMessageContaining("1 to 4");
        }

        /**
         * The reason every value is applied before any of them is checked.
         *
         * <p>Raising the fewest requests in flight above the current most is refused on its own and
         * fine alongside a new most. Applied one at a time, whether these three were accepted would
         * depend on the order they happened to be read in - which changes between a file, the
         * environment and the command line.
         */
        @Test
        @DisplayName("values are checked against one another only once all of them are in")
        void the_order_they_were_typed_in_does_not_decide() {
            Map<String, String> raised = new LinkedHashMap<>();
            raised.put("engine.minConcurrency", "8");
            raised.put("engine.initialConcurrency", "8");
            raised.put("engine.maxConcurrency", "32");

            assertThat(Settings.from(raised).engine().minConcurrency()).isEqualTo(8);

            Map<String, String> otherWayRound = new LinkedHashMap<>();
            otherWayRound.put("engine.maxConcurrency", "32");
            otherWayRound.put("engine.initialConcurrency", "8");
            otherWayRound.put("engine.minConcurrency", "8");

            assertThat(Settings.from(otherWayRound)).isEqualTo(Settings.from(raised));
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("a name that is not a setting is refused, with the one it most looks like")
        void a_misspelt_name_names_the_real_one() {
            assertThatThrownBy(() -> Settings.from(Map.of("engine.maxConcurrancy", "8")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("there is no setting called 'engine.maxConcurrancy'")
                    .hasMessageContaining("engine.maxConcurrency");
        }

        @Test
        @DisplayName("a name nothing looks like is refused with where the real ones are listed")
        void an_unrecognisable_name_points_at_the_list() {
            assertThatThrownBy(() -> Settings.from(Map.of("engine.threads", "8")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("there is no setting called 'engine.threads'")
                    .hasMessageContaining("--print-settings");
        }

        @ParameterizedTest
        @ValueSource(strings = {"eight", "8.5", "", " ", "0x8"})
        @DisplayName("a value of the wrong kind is refused, and the refusal says which kind it "
                + "wanted")
        void a_value_of_the_wrong_kind(String said) {
            assertThatThrownBy(() -> Settings.from(Map.of("engine.maxConcurrency", said)))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("engine.maxConcurrency takes a whole number");
        }

        @Test
        @DisplayName("each kind of value is refused in its own words")
        void every_kind_refuses_in_its_own_words() {
            assertThatThrownBy(() -> Settings.from(Map.of("report.mostBodyBytesKept", "lots")))
                    .hasMessageContaining("takes a whole number");
            assertThatThrownBy(() -> Settings.from(Map.of("engine.slowdownFactor", "quite a bit")))
                    .hasMessageContaining("takes a number");
            assertThatThrownBy(() -> Settings.from(Map.of("engine.followRedirects", "yes")))
                    .hasMessageContaining("takes true or false");
            assertThatThrownBy(() -> Settings.from(Map.of("engine.readTimeout", "a while")))
                    .hasMessageContaining("is not a length of time");
        }

        @Test
        @DisplayName("a value outside its range is refused with the range, and says which group")
        void a_value_outside_its_range() {
            assertThatThrownBy(() -> Settings.from(Map.of("engine.maxConcurrency", "0")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("engine: ")
                    .hasMessageContaining("below minConcurrency");

            assertThatThrownBy(() -> Settings.from(Map.of("engine.userAgent", "  ")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("engine: ")
                    .hasMessageContaining("User-Agent");

            assertThatThrownBy(() -> Settings.from(Map.of("generation.optionalNestingDepth", "0")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("generation: ")
                    .hasMessageContaining("at least 1");

            assertThatThrownBy(() -> Settings.from(Map.of("memory.mostNames", "-1")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("memory: ")
                    .hasMessageContaining("cannot be negative");

            assertThatThrownBy(() -> Settings.from(Map.of("document.fetchTimeout", "0s")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("document: ")
                    .hasMessageContaining("greater than zero");

            assertThatThrownBy(() -> Settings.from(Map.of("report.writeUpsInTotal", "-1")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("report: ")
                    .hasMessageContaining("cannot be negative");

            assertThatThrownBy(() -> Settings.from(Map.of("schedule.workAheadFactor", "0")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageStartingWith("schedule: ")
                    .hasMessageContaining("at least 1");
        }

        /**
         * The values that used to get past every check and then kill the run.
         *
         * <p>Each of these is an ordinary thing to type. A number larger than the machine can hold
         * became infinity and satisfied "greater than one"; a length of time longer than
         * milliseconds can count wrapped round; a number written in eleven characters became a
         * thousand million when written out. All three were accepted, and the run then died with a
         * stack trace somewhere far away from the line that caused it.
         */
        @Test
        @DisplayName("a value too large for the machine to hold is refused here, rather than "
                + "accepted and then killing the run somewhere else")
        void values_too_large_to_hold() {
            assertThatThrownBy(() -> Settings.from(Map.of("engine.slowdownFactor", "1e400")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("written down");

            assertThatThrownBy(() -> Settings.from(
                    Map.of("engine.readTimeout", "9223372036854775807")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("longer than any length of time");

            assertThatThrownBy(() -> Settings.from(
                    Map.of("engine.readTimeout", "9223372036854775807h")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("longer than any length of time");

            assertThatThrownBy(() -> Settings.from(
                    Map.of("generation.lowestNumber", "1e999999999")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("characters to write down");

            assertThatThrownBy(() -> Settings.from(Map.of("engine.readTimeout", "PT0.0005S")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("finer than a millisecond");
        }

        @Test
        @DisplayName("two values that disagree with each other are refused naming both")
        void two_values_that_disagree() {
            assertThatThrownBy(() -> Settings.from(Map.of(
                    "generation.usualLongestString", "500",
                    "generation.longestString", "100")))
                    .isInstanceOf(SettingsException.class)
                    .hasMessageContaining("longestString (100)")
                    .hasMessageContaining("usualLongestString (500)");
        }
    }

    @Nested
    @DisplayName("reading a setting back out")
    class Written {

        @ParameterizedTest
        @MethodSource("io.restest.core.settings.SettingsTest#everySetting")
        @DisplayName("every setting can be written out and read straight back in, which is what "
                + "makes a printed file one the tool accepts")
        void every_setting_survives_the_round_trip(SettingKey key) {
            String written = Settings.defaults().written(key);

            assertThat(Settings.from(Map.of(key.fullName(), written)))
                    .describedAs("%s printed as '%s' did not read back as itself",
                            key.fullName(), written)
                    .isEqualTo(Settings.defaults());
        }

        @Test
        @DisplayName("a number is written the way somebody would type it, without an exponent")
        void numbers_are_written_plainly() {
            Settings small = Settings.from(Map.of("generation.optionalPropertyChance", "0.0001"));

            assertThat(small.written(SettingKey.named("generation.optionalPropertyChance")
                    .orElseThrow())).isEqualTo("0.0001");
        }

    }

    @Nested
    @DisplayName("one group changed at a time")
    class Replacing {

        @Test
        @DisplayName("each group can be replaced without disturbing the others")
        void each_group_can_be_replaced() {
            Settings from = Settings.defaults();

            assertThat(from.withEngine(from.engine().withoutConcurrency()).engine()
                    .maxConcurrency()).isEqualTo(1);
            assertThat(from.withSchedule(new ScheduleSettings(3, 10, Duration.ofSeconds(1), true,
                    Duration.ofSeconds(2)))
                    .schedule().workAheadFactor()).isEqualTo(3);
            assertThat(from.withGeneration(GenerationSettings.defaults()).generation())
                    .isEqualTo(GenerationSettings.defaults());
            assertThat(from.withMemory(new MemorySettings(0, 0, 0, 0, 0)).memory()
                    .mostNames()).isZero();
            assertThat(from.withDocument(new DocumentSettings(Duration.ofSeconds(1), 10))
                    .document().mostBytesRead()).isEqualTo(10);
            assertThat(from.withReport(new ReportSettings(0, 0, 0, 0)).report()
                    .writeUpsInTotal()).isZero();
            assertThat(from.withReport(from.report()))
                    .describedAs("replacing a group with itself changes nothing at all")
                    .isEqualTo(from);
        }
    }
}
