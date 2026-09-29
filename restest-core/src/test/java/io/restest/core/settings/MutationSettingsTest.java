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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MutationSettingsTest {

    @Test
    @DisplayName("by default every change that breaks what the document states is made")
    void everything_is_on_by_default() {
        MutationSettings defaults = MutationSettings.defaults();

        assertThat(defaults.violations()).isTrue();
        assertThat(defaults.acceptedKept()).isEqualTo(16);
        assertThat(defaults.oversizedLength()).isEqualTo(10_000);
        assertThat(defaults.oversizedItems()).isEqualTo(1_000);
        assertThat(defaults.wrongRoot() && defaults.emptyBody() && defaults.notJson()
                && defaults.wrongContentType() && defaults.beyondItsWidth())
                .describedAs("the changes to a body as a whole each have a switch of their own, "
                        + "on")
                .isTrue();
    }

    @Test
    @DisplayName("turning every change off leaves nothing to change, and the rest as it was")
    void every_change_off_changes_nothing() {
        MutationSettings off = MutationSettings.defaults().withNothingChanged();

        assertThat(off.violations()).isFalse();
        assertThat(off.dropRequired())
                .describedAs("each kind keeps its own switch, so turning them all back on "
                        + "brings back what each had")
                .isTrue();
        assertThat(Settings.from(Map.of("mutation.violations", "false")).mutation())
                .isEqualTo(off);
    }

    @Test
    @DisplayName("a switch for a change that is no longer made is refused, rather than ignored")
    void the_probes_are_gone() {
        for (String gone : new String[] {"probes", "oversizeWithNoLimit", "emptyWithNoRule",
                "deepNesting", "extremeNumber", "nestingDepth"}) {
            assertThatExceptionOfType(SettingsException.class)
                    .describedAs("mutation.%s", gone)
                    .isThrownBy(() -> Settings.from(Map.of("mutation." + gone, "true")))
                    .withMessageContaining("there is no setting called");
        }
    }

    @Test
    @DisplayName("keeping none of the accepted requests, or oversizing to nothing, is refused")
    void sizes_are_at_least_one() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MutationSettings(true, true,
                true, true, true, true, true, true, true, true, true, true, true, true, true,
                0, 1, 1))
                .withMessageContaining("acceptedKept");
        assertThatExceptionOfType(SettingsException.class)
                .isThrownBy(() -> Settings.from(Map.of("mutation.oversizedLength", "0")))
                .withMessageContaining("mutation")
                .withMessageContaining("oversizedLength");
        assertThatExceptionOfType(SettingsException.class)
                .isThrownBy(() -> Settings.from(Map.of("mutation.oversizedItems", "-3")))
                .withMessageContaining("oversizedItems");
    }
}
