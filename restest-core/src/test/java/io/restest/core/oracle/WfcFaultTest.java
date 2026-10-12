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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks RESTest's copy of the shared fault catalogue against the catalogue itself.
 *
 * <p>The whole reason for using somebody else's numbering is that a fault count from RESTest can be
 * put beside a fault count from another tool. That only holds while the two lists agree, and a list
 * copied by hand drifts silently. So the published file is kept beside this test and compared with
 * what RESTest ships, entry by entry. The file the catalogue's own version is declared in is kept
 * beside it, because the version of the collection it is published in is a different number. Both
 * are kept byte for byte as published. Refreshing them is a deliberate act: this test failing means
 * the catalogue has moved on, which is news rather than a nuisance.
 *
 * <p>It moved once already. Between faults 0.7.0 and 0.8.0 the numbers were rearranged, not merely
 * added to: a schema mismatch went from 101 to 200 and every security weakness from the 200s to the
 * 300s. A count of "F101" published before the move and one published after name different faults,
 * which is exactly why a report states the version it counted under. Faults 0.9.0 moved no number:
 * it added five kinds, and gave every kind a key in words that the catalogue says is meant never to
 * change.
 */
// Where the two pinned files come from: WebFuzzing/Commons, tag v0.11.0, commit
// 962e1f75bbfc64bc51de2a34498dc6565dfeaf44, fetched on 2026-10-12 with
//   gh api -H 'Accept: application/vnd.github.raw' 'repos/WebFuzzing/Commons/contents/<path>?ref=<commit>'
// for src/main/resources/wfc/faults/fault_categories.json (faults 0.9.0) and
// src/main/java/com/webfuzzing/commons/VersionNumbers.java. git hash-object gives
// a883045a40971671365a4865053c1d779e1f637c and d32103bb847104f5973a945418891a548ce5a2a4, the hashes
// the catalogue's own repository gives them.
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
            assertThat(fault.id()).isEqualTo(text(entry, "id"));
            assertThat(fault.descriptiveName()).isEqualTo(text(entry, "descriptiveName"));
            assertThat(fault.testCaseLabel()).isEqualTo(text(entry, "testCaseLabel"));
            assertThat(fault.label()).isEqualTo(text(entry, "label"));
        }
    }

    @Test
    @DisplayName("every number in the catalogue finds the kind it names, and an unknown number finds "
            + "nothing")
    void a_fault_is_found_by_its_number() {
        for (JsonValue.JsonObject entry : published()) {
            int code = number(entry, "code");
            assertThat(WfcFault.byCode(code).map(FaultCategory::id))
                    .describedAs("the kind numbered %d", code)
                    .contains(text(entry, "id"));
        }
        assertThat(WfcFault.byCode(999)).isEmpty();
    }

    @Test
    @DisplayName("the two faults this release reports are named as the catalogue names them")
    void the_two_implemented_faults_are_named_as_the_catalogue_names_them() {
        assertThat(WfcFault.HTTP_STATUS_500.label()).isEqualTo("F100:HTTP Status 500");
        assertThat(WfcFault.SCHEMA_INVALID_RESPONSE.code()).isEqualTo(200);
        assertThat(WfcFault.SCHEMA_INVALID_RESPONSE.testCaseLabel())
                .isEqualTo("returnsMismatchResponseWithSchema");
    }

    @Test
    @DisplayName("the catalogue's version travels with the copy, so a report can say which it used")
    void the_catalogue_version_is_recorded() {
        // Read from the file the catalogue's authors state it in, not written out here. Asserting a
        // number against the constant that holds it proves only that somebody typed it twice, and
        // the version is the one fact every published count of ours depends on.
        assertThat(WfcFault.CATALOGUE_VERSION).isEqualTo(publishedFaultsVersion());
        assertThat(WfcFault.CATALOGUE_NAME).isEqualTo("Web Fuzzing Commons");
    }

    @Test
    @DisplayName("the catalogue publishes exactly the fields RESTest expects it to")
    void the_catalogue_has_the_shape_this_test_assumes() {
        // Two of these are deliberately not copied into the enum, and 'id' is the name of each
        // entry. 'fullDescription' is a paragraph for a person to read and belongs in the catalogue
        // rather than in our source; 'group' is the catalogue's own filing, which our code ranges
        // already carry. A field appearing or
        // disappearing changes what "copied faithfully" means, so it is checked rather than
        // assumed.
        assertThat(published()).allSatisfy(entry ->
                assertThat(entry.members().keySet()).containsExactlyInAnyOrder(
                        "code", "id", "descriptiveName", "testCaseLabel", "label",
                        "fullDescription", "group"));
    }

    /**
     * The version the catalogue's own authors give the fault list, read from the file they state it
     * in.
     *
     * <p>Not the version of the release it ships in, which is a different number. Web Fuzzing
     * Commons publishes four things together, they began on one version and have drifted apart, and
     * the release tagged 0.11.0 carries faults 0.9.0. That is exactly the sort of thing a person
     * copies wrongly once and nobody notices for a year, so it is read from the pinned file rather
     * than typed here.
     */
    private static String publishedFaultsVersion() {
        try (InputStream file = WfcFaultTest.class
                .getResourceAsStream("/wfc/VersionNumbers.java.txt")) {
            assertThat(file).describedAs("the file the versions are declared in is kept beside "
                    + "this test").isNotNull();
            String source = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            Matcher declared = Pattern
                    .compile("String\\s+FAULTS\\s*=\\s*\"([^\"]+)\"")
                    .matcher(source);
            assertThat(declared.find())
                    .describedAs("the faults version is declared in the pinned file").isTrue();
            return declared.group(1);
        } catch (IOException e) {
            throw new AssertionError("the pinned version file could not be read", e);
        }
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
