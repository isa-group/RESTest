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
package io.restest.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpResponseRecordTest {

    @Test
    @DisplayName("a response with no headers and no body is buildable directly")
    void a_bare_response_is_buildable() {
        HttpResponseRecord response = HttpResponseRecord.of(204);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.statusLine().reasonPhrase()).isEmpty();
        assertThat(response.statusLine().protocolVersion()).isEmpty();
        assertThat(response.headers()).isEmpty();
        assertThat(response.body()).isEmpty();
    }

    @Test
    @DisplayName("a header is found however either side capitalises its name")
    void headers_are_found_case_insensitively() {
        HttpResponseRecord response = new HttpResponseRecord(StatusLine.of(200),
                List.of(Header.of("X-Rate-Limit", "10")), Optional.empty());

        assertThat(response.headerValues("x-rate-limit")).containsExactly("10");
    }

    @Test
    @DisplayName("a status code outside 100-599 is still recorded, not refused")
    void an_unusual_status_code_is_recorded_rather_than_refused() {
        HttpResponseRecord response = HttpResponseRecord.of(999);

        assertThat(response.statusCode()).isEqualTo(999);
    }

    @Test
    @DisplayName("a body is kept when the response carried one")
    void a_body_is_kept() {
        Payload body = Payload.text("{}", "application/json");

        HttpResponseRecord response = new HttpResponseRecord(StatusLine.of(200), List.of(),
                Optional.of(body));

        assertThat(response.body()).contains(body);
    }

    @Test
    @DisplayName("the reason phrase and protocol version are kept when the engine reports them")
    void the_status_line_s_other_facts_are_kept() {
        StatusLine statusLine = new StatusLine(404, Optional.of("Not Found"),
                Optional.of("HTTP/1.1"));

        HttpResponseRecord response = new HttpResponseRecord(statusLine, List.of(),
                Optional.empty());

        assertThat(response.statusLine().reasonPhrase()).contains("Not Found");
        assertThat(response.statusLine().protocolVersion()).contains("HTTP/1.1");
    }

    @Test
    @DisplayName("toString shows the status and header names, never a header's value")
    void to_string_does_not_leak_header_values() {
        StatusLine statusLine = new StatusLine(200, Optional.of("OK"), Optional.empty());

        HttpResponseRecord response = new HttpResponseRecord(statusLine,
                List.of(Header.of("Set-Cookie", "session=secret")), Optional.empty());

        assertThat(response).hasToString(
                "HttpResponseRecord[200 OK, headers=[Set-Cookie], body=none]");
    }
}
