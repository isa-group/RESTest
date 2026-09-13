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
package io.restest.core.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks RESTest's copy of the shared fault catalogue against the catalogue itself.
 *
 * <p>The whole reason for using somebody else's numbering is that a fault count from RESTest can be
 * put beside a fault count from another tool. That only holds while the two lists agree, and a list
 * copied by hand drifts silently. So the published file is kept beside this test and compared with
 * what RESTest ships, entry by entry.
 *
 * <p>The copy comes from {@code WebFuzzing/Commons}, file
 * {@code src/main/resources/wfc/faults/fault_categories.json}, at commit
 * {@code 0e45ffefd307efcedd6c6ae944326051993eaca3}, faults version {@code 0.7.0}, fetched on
 * 2026-09-13. Refreshing it is a deliberate act: this test failing means the catalogue has
 * moved on, which is news rather than a nuisance.
 */
class WfcFaultTest {

    @Test
    @DisplayName("every fault RESTest names matches the published catalogue, entry for entry")
    void the_catalogue_is_copied_faithfully() {
        List<JsonValue.JsonObject> published = published();

        assertThat(WfcFault.values())
                .describedAs("the catalogue has %d entries", published.size())
                .hasSameSizeAs(published);

        Map<Integer, JsonValue.JsonObject> byCode = published.stream().collect(
                Collectors.toMap(entry -> number(entry, "code"), entry -> entry));

        for (WfcFault fault : WfcFault.values()) {
            JsonValue.JsonObject entry = byCode.get(fault.code());
            assertThat(entry).describedAs("code %d is in the catalogue", fault.code()).isNotNull();
            assertThat(fault.descriptiveName()).isEqualTo(text(entry, "descriptiveName"));
            assertThat(fault.testCaseLabel()).isEqualTo(text(entry, "testCaseLabel"));
            assertThat(fault.label()).isEqualTo(text(entry, "label"));
        }
    }

    @Test
    @DisplayName("a fault can be found by its number, and an unknown number finds nothing")
    void a_fault_is_found_by_its_number() {
        assertThat(WfcFault.byCode(100)).contains(WfcFault.HTTP_STATUS_500);
        assertThat(WfcFault.byCode(101)).contains(WfcFault.SCHEMA_INVALID_RESPONSE);
        assertThat(WfcFault.byCode(999)).isEmpty();
    }

    @Test
    @DisplayName("the two faults this release reports are named as the catalogue names them")
    void the_two_implemented_faults_are_named_as_the_catalogue_names_them() {
        assertThat(WfcFault.HTTP_STATUS_500.label()).isEqualTo("F100:HTTP Status 500");
        assertThat(WfcFault.SCHEMA_INVALID_RESPONSE.code()).isEqualTo(101);
        assertThat(WfcFault.SCHEMA_INVALID_RESPONSE.testCaseLabel())
                .isEqualTo("returnsMismatchResponseWithSchema");
    }

    @Test
    @DisplayName("the catalogue's version travels with the copy, so a report can say which it used")
    void the_catalogue_version_is_recorded() {
        assertThat(WfcFault.CATALOGUE_VERSION).isEqualTo("0.7.0");
        assertThat(WfcFault.CATALOGUE_NAME).isEqualTo("Web Fuzzing Commons");
    }

    private static List<JsonValue.JsonObject> published() {
        try (InputStream file = WfcFaultTest.class
                .getResourceAsStream("/wfc/fault_categories.json")) {
            assertThat(file).describedAs("the published catalogue is kept beside this test")
                    .isNotNull();
            String text = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            return ((JsonValue.JsonArray) JsonText.read(text)).elements().stream()
                    .map(JsonValue.JsonObject.class::cast)
                    .toList();
        } catch (IOException e) {
            throw new AssertionError("the published catalogue could not be read", e);
        }
    }

    private static String text(JsonValue.JsonObject entry, String name) {
        return ((JsonValue.JsonString) entry.member(name).orElseThrow()).value();
    }

    private static int number(JsonValue.JsonObject entry, String name) {
        return ((JsonValue.JsonNumber) entry.member(name).orElseThrow()).value().intValueExact();
    }
}
