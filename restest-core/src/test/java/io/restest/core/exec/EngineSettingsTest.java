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
package io.restest.core.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineSettingsTest {

    @Test
    @DisplayName("the defaults work against an unknown API with nothing configured")
    void defaults_are_usable_without_configuration() {
        EngineSettings settings = EngineSettings.defaults();

        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.writeTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.minConcurrency()).isEqualTo(1);
        assertThat(settings.initialConcurrency()).isEqualTo(4);
        assertThat(settings.maxConcurrency()).isEqualTo(16);
        assertThat(settings.maxRetainedResponseBytes())
                .isEqualTo(EngineSettings.DEFAULT_MAX_RETAINED_RESPONSE_BYTES);
        assertThat(settings.userAgent()).isEqualTo("RESTest/2.0");
    }

    @Test
    @DisplayName("redirections are not followed by default, so a 302 is reported as a 302")
    void redirects_are_not_followed_by_default() {
        assertThat(EngineSettings.defaults().followRedirects()).isFalse();
        assertThat(EngineSettings.defaults().withFollowRedirects(true).followRedirects()).isTrue();
    }

    @Test
    @DisplayName("changing one setting leaves the others alone")
    void with_methods_change_only_what_they_name() {
        EngineSettings changed = EngineSettings.defaults()
                .withReadTimeout(Duration.ofSeconds(5))
                .withUserAgent("RESTest/2.0 (smoke test)")
                .withMaxRetainedResponseBytes(64);

        assertThat(changed.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(changed.userAgent()).isEqualTo("RESTest/2.0 (smoke test)");
        assertThat(changed.maxRetainedResponseBytes()).isEqualTo(64);
        assertThat(changed.connectTimeout()).isEqualTo(EngineSettings.defaults().connectTimeout());
        assertThat(changed.maxConcurrency()).isEqualTo(EngineSettings.defaults().maxConcurrency());
    }

    @Test
    @DisplayName("an API too fragile for two questions at once gets one request at a time")
    void without_concurrency_pins_the_range_to_one() {
        EngineSettings single = EngineSettings.defaults().withoutConcurrency();

        assertThat(single.minConcurrency()).isEqualTo(1);
        assertThat(single.initialConcurrency()).isEqualTo(1);
        assertThat(single.maxConcurrency()).isEqualTo(1);
    }

    @Test
    @DisplayName("a concurrency range that leaves no room to send anything is refused")
    void impossible_concurrency_ranges_are_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EngineSettings.defaults().withConcurrency(0, 1, 4))
                .withMessageContaining("minConcurrency must be at least 1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EngineSettings.defaults().withConcurrency(4, 4, 2))
                .withMessageContaining("leaves no room");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EngineSettings.defaults().withConcurrency(2, 9, 4))
                .withMessageContaining("outside the range");
    }

    @Test
    @DisplayName("a timeout of zero, which would give up before starting, is refused")
    void non_positive_timeouts_are_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EngineSettings.defaults().withReadTimeout(Duration.ZERO))
                .withMessageContaining("readTimeout");
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        EngineSettings.defaults().withConnectTimeout(Duration.ofSeconds(-1)))
                .withMessageContaining("connectTimeout");
        assertThatNullPointerException()
                .isThrownBy(() -> EngineSettings.defaults().withWriteTimeout(null));
    }

    @Test
    @DisplayName("an engine that would keep nothing of a reply, or have no name, is refused")
    void empty_retention_and_blank_user_agent_are_refused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EngineSettings.defaults().withMaxRetainedResponseBytes(0))
                .withMessageContaining("nothing to judge");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EngineSettings.defaults().withUserAgent("  "))
                .withMessageContaining("User-Agent");
    }
}
