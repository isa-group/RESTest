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
package io.restest.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Payload;
import io.restest.core.model.HttpMethod;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurlCommandTest {

    @Test
    @DisplayName("a plain request becomes a command that repeats it")
    void a_plain_request_is_written_out() {
        String command = CurlCommand.of(
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets?limit=10"));

        assertThat(command).isEqualTo("curl -i -X GET 'https://api.example/pets?limit=10'");
    }

    @Test
    @DisplayName("every header that was sent is in the command")
    void headers_are_carried() {
        String command = CurlCommand.of(new HttpRequestRecord(HttpMethod.GET,
                "https://api.example/pets",
                List.of(Header.of("Accept", "application/json"),
                        Header.of("X-Api-Key", "abc123")),
                Optional.empty()));

        assertThat(command)
                .contains("-H 'Accept: application/json'")
                .contains("-H 'X-Api-Key: abc123'");
    }

    @Test
    @DisplayName("the length of the body is left to curl, which works it out and would disagree")
    void content_length_is_left_out() {
        String command = CurlCommand.of(new HttpRequestRecord(HttpMethod.POST,
                "https://api.example/pets",
                List.of(Header.of("Content-Length", "9"), Header.of("Content-Type", "text/plain")),
                Optional.of(Payload.text("some body", "text/plain"))));

        assertThat(command).doesNotContain("Content-Length");
        assertThat(command).contains("-H 'Content-Type: text/plain'");
        assertThat(command).contains("--data-binary 'some body'");
    }

    @Test
    @DisplayName("a value containing a quote still produces a command a shell will accept")
    void a_quote_inside_a_value_is_escaped() {
        String command = CurlCommand.of(
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets?name=it's"));

        assertThat(command).isEqualTo("curl -i -X GET 'https://api.example/pets?name=it'\\''s'");
    }

    @Test
    @DisplayName("a body that is not text is written so that the command still runs")
    void a_body_that_is_not_text_is_written_as_letters_and_digits() {
        byte[] notText = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};

        String command = CurlCommand.of(new HttpRequestRecord(HttpMethod.PUT,
                "https://api.example/pictures/1", List.of(),
                Optional.of(Payload.of(notText, "image/png"))));

        assertThat(command).contains("base64 -d").contains("//4AAQ==");
    }

    @Test
    @DisplayName("the method is always spelled out, so the command says what it does")
    void the_method_is_always_written() {
        assertThat(CurlCommand.of(HttpRequestRecord.of(HttpMethod.DELETE,
                "https://api.example/pets/1"))).startsWith("curl -i -X DELETE ");
    }

    @Test
    @DisplayName("asking only for the headers is asked for the way curl asks for it")
    void a_head_request_uses_the_flag_curl_has_for_it() {
        // -X HEAD leaves curl waiting for a body the server will never send, so the command hangs
        // until it gives up. --head is how curl is told to expect headers and nothing else.
        String command = CurlCommand.of(
                HttpRequestRecord.of(HttpMethod.HEAD, "https://api.example/pets"));

        assertThat(command).isEqualTo("curl --head 'https://api.example/pets'");
        assertThat(command).doesNotContain("-X HEAD");
    }
}
