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

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The list of everything about the tool that somebody can change.
 *
 * <p>The list is a promise made to whoever reads {@code --print-settings}: these names, spelt this
 * way, in a file, on the command line and in the environment.
 */
class SettingKeyTest {

    static List<SettingKey> everySetting() {
        return SettingKey.all();
    }

    @Test
    @DisplayName("there are settings, in groups, and every group has some")
    void there_are_settings() {
        assertThat(SettingKey.all()).isNotEmpty();
        assertThat(SettingKey.groups()).isNotEmpty();
        assertThat(SettingKey.all()).extracting(SettingKey::group)
                .containsAll(SettingKey.groups());
    }

    @Test
    @DisplayName("every setting belongs to one of the groups that are printed, so none is hidden")
    void every_setting_is_in_a_printed_group() {
        assertThat(SettingKey.all()).allSatisfy(key ->
                assertThat(SettingKey.groups())
                        .describedAs("%s is in a group --print-settings never writes out",
                                key.fullName())
                        .contains(key.group()));
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("every setting can be looked up by the name a person types")
    void every_setting_is_found_by_name(SettingKey key) {
        assertThat(SettingKey.named(key.fullName())).contains(key);
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("every setting says what it does, in words rather than in a name repeated")
    void every_setting_explains_itself(SettingKey key) {
        assertThat(key.meaning()).isNotBlank();
        assertThat(key.meaning().split(" ").length)
                .describedAs("%s explains itself in one line, not in one word", key.fullName())
                .isGreaterThan(3);
    }

    @ParameterizedTest
    @CsvSource({
            "engine.maxConcurrency, RESTEST_ENGINE_MAX_CONCURRENCY",
            "engine.userAgent, RESTEST_ENGINE_USER_AGENT",
            "memory.asDeepAsAReplyIsRead, RESTEST_MEMORY_AS_DEEP_AS_A_REPLY_IS_READ",
            "report.writeUpsInTotal, RESTEST_REPORT_WRITE_UPS_IN_TOTAL",
            "document.mostBytesRead, RESTEST_DOCUMENT_MOST_BYTES_READ",
    })
    @DisplayName("the same words name a setting in the environment, in the spelling environments "
            + "use")
    void the_environment_spelling(String fullName, String expected) {
        assertThat(SettingKey.named(fullName)).get()
                .extracting(SettingKey::environmentName)
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("no two settings share an environment name, which would make one of them "
            + "unreachable from a container")
    void environment_names_are_distinct(SettingKey key) {
        assertThat(SettingKey.all().stream()
                .filter(other -> other.environmentName().equals(key.environmentName()))
                .toList())
                .describedAs("%s shares its environment name with another setting",
                        key.fullName())
                .hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("an environment name is built out of capitals and underscores and nothing else")
    void environment_names_are_shouted(SettingKey key) {
        assertThat(key.environmentName())
                .startsWith(SettingKey.ENVIRONMENT_PREFIX)
                .isEqualTo(key.environmentName().toUpperCase(Locale.ROOT))
                .matches("[A-Z0-9_]+");
    }

    @Test
    @DisplayName("a misspelt name is answered with the one it most looks like")
    void a_misspelling_is_answered_with_the_nearest() {
        assertThat(SettingKey.nearestTo("engine.maxConcurrancy")).get()
                .extracting(SettingKey::fullName).isEqualTo("engine.maxConcurrency");
        assertThat(SettingKey.nearestTo("engine.maxconcurrency")).get()
                .extracting(SettingKey::fullName).isEqualTo("engine.maxConcurrency");
        assertThat(SettingKey.nearestTo("report.writeUpsInTotals")).get()
                .extracting(SettingKey::fullName).isEqualTo("report.writeUpsInTotal");
    }

    @Test
    @DisplayName("nothing is offered when nothing is close, because a wrong suggestion is worse "
            + "than none")
    void nothing_is_offered_when_nothing_is_close() {
        assertThat(SettingKey.nearestTo("engine.threads")).isEmpty();
        assertThat(SettingKey.nearestTo("")).isEmpty();
        assertThat(SettingKey.nearestTo(null)).isEmpty();
        assertThat(SettingKey.nearestTo("what.on.earth.is.this")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("everySetting")
    @DisplayName("every real name is its own nearest match, so a correct name is never corrected")
    void a_real_name_is_its_own_nearest(SettingKey key) {
        assertThat(SettingKey.nearestTo(key.fullName())).contains(key);
    }

    @Test
    @DisplayName("a name nobody has is not a setting")
    void an_unknown_name_is_not_a_setting() {
        assertThat(SettingKey.named("engine.somethingElse")).isEmpty();
        assertThat(SettingKey.named("")).isEmpty();
    }
}
