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

import io.restest.core.model.ApiModel;
import io.restest.core.model.Server;
import io.restest.core.model.ServerVariable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BaseAddressTest {

    private static final ApiModel NO_SERVERS = ApiModel.of("Pets", "1.0", List.of());

    @Test
    @DisplayName("what a person typed wins over whatever the document claims")
    void the_person_wins() {
        ApiModel model = declaring(Server.at("https://production.example/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080/api/v3", model))
                .isEqualTo("http://localhost:8080/api/v3");
    }

    @Test
    @DisplayName("with nothing typed, the first address the document declares is used")
    void the_document_answers_when_nobody_else_does() {
        ApiModel model = declaring(Server.at("https://api.example/v2"),
                Server.at("http://api.example/v2"));

        assertThat(BaseAddress.resolve(null, model)).isEqualTo("https://api.example/v2");
    }

    @Test
    @DisplayName("an address the document leaves half-written is skipped in favour of a whole one")
    void a_template_nobody_filled_in_is_skipped() {
        Server template = new Server("https://{region}.example.com/v2",
                Map.of("zone", new ServerVariable("eu", List.of(), Optional.empty())),
                Optional.empty());
        ApiModel model = declaring(template, Server.at("https://fallback.example/v2"));

        assertThat(BaseAddress.resolve(null, model)).isEqualTo("https://fallback.example/v2");
    }

    @Test
    @DisplayName("a document's variables are filled in from its own defaults")
    void a_template_with_defaults_is_usable() {
        Server template = new Server("https://{region}.example.com/v2",
                Map.of("region", new ServerVariable("eu", List.of("eu", "us"), Optional.empty())),
                Optional.empty());

        assertThat(BaseAddress.resolve(null, declaring(template)))
                .isEqualTo("https://eu.example.com/v2");
    }

    @Test
    @DisplayName("a document that declares nowhere to send requests asks for --url by name")
    void nothing_declared_asks_for_the_option() {
        assertThatThrownBy(() -> BaseAddress.resolve(null, NO_SERVERS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the document names none")
                .hasMessageContaining("--url");
    }

    @Test
    @DisplayName("the lone slash a document with no servers block is given is not an address")
    void the_parsers_placeholder_is_refused() {
        assertThatThrownBy(() -> BaseAddress.resolve(null, declaring(Server.at("/"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the document names '/'")
                .hasMessageContaining("--url");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost:8080",
        "ftp://api.example",
        "/api/v3",
        "http:///api",
        "http://api.example/v2?key=abc",
        "http://api.example/v2#section",
        "not an address at all",
    })
    @DisplayName("an address requests could not be sent to is refused before the run opens anything")
    void an_unusable_address_is_refused(String typed) {
        assertThatThrownBy(() -> BaseAddress.resolve(typed, NO_SERVERS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not somewhere requests can be sent");
    }

    @Test
    @DisplayName("a blank --url is the same as not saying anything")
    void blank_is_the_same_as_silence() {
        assertThat(BaseAddress.resolve("   ", declaring(Server.at("https://api.example/v2"))))
                .isEqualTo("https://api.example/v2");
    }

    @Test
    @DisplayName("surrounding spaces in a pasted address are not a mistake")
    void spaces_are_forgiven() {
        assertThat(BaseAddress.resolve("  http://localhost:8080  ", NO_SERVERS))
                .isEqualTo("http://localhost:8080");
    }

    private static ApiModel declaring(Server... servers) {
        return new ApiModel("Pets", "1.0", List.of(servers), List.of(), Map.of(), List.of(),
                Optional.empty());
    }
}
