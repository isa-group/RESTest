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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.model.HttpMethod;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpRequestRecordTest {

    @Test
    @DisplayName("a request with no headers and no body is buildable directly")
    void a_bare_request_is_buildable() {
        HttpRequestRecord request = HttpRequestRecord.of(HttpMethod.GET,
                "https://api.example.com/pets/42");

        assertThat(request.method()).isEqualTo(HttpMethod.GET);
        assertThat(request.headers()).isEmpty();
        assertThat(request.body()).isEmpty();
    }

    @Test
    @DisplayName("repeated headers are kept, in the order they were sent")
    void repeated_headers_are_kept_in_order() {
        HttpRequestRecord request = new HttpRequestRecord(HttpMethod.GET,
                "https://api.example.com/pets", List.of(
                        Header.of("X-Trace", "a"),
                        Header.of("X-Trace", "b")), java.util.Optional.empty());

        assertThat(request.headerValues("X-Trace")).containsExactly("a", "b");
    }

    @Test
    @DisplayName("a header is found however either side capitalises its name")
    void headers_are_found_case_insensitively() {
        HttpRequestRecord request = new HttpRequestRecord(HttpMethod.GET,
                "https://api.example.com/pets",
                List.of(Header.of("Authorization", "Bearer x")), java.util.Optional.empty());

        assertThat(request.headerValues("authorization")).containsExactly("Bearer x");
        assertThat(request.headerValues("AUTHORIZATION")).containsExactly("Bearer x");
    }

    @Test
    @DisplayName("a request must have a URL")
    void a_request_has_a_url() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpRequestRecord.of(HttpMethod.GET, " "));
    }

    @Test
    @DisplayName("changing the list a request was built from does not change the request")
    void headers_are_copied() {
        List<Header> headers = new java.util.ArrayList<>(List.of(Header.of("X-Trace", "a")));

        HttpRequestRecord request = new HttpRequestRecord(HttpMethod.GET,
                "https://api.example.com/pets", headers, java.util.Optional.empty());
        headers.clear();

        assertThat(request.headers()).hasSize(1);
    }
}
