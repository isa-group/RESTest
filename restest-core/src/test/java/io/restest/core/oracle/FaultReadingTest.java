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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.settings.SettingKey;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checks how RESTest reads each kind of fault it reports: severity, title and advice. */
class FaultReadingTest {

    /** Every kind RESTest's own rules report, in the order of their numbers. */
    private static final List<FaultCategory> REPORTED = List.of(
            WfcFault.HTTP_STATUS_500, WfcFault.HTTP_OTHER_STATUS_5XX,
            WfcFault.SCHEMA_INVALID_RESPONSE, WfcFault.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED,
            WfcFault.SCHEMA_VALIDATION_BYPASS, WfcFault.SECURITY_CALL_TIMEOUT,
            RestestFault.UNDECLARED_STATUS_CODE, RestestFault.UNDECLARED_CONTENT_TYPE);

    /** A setting written inside an advice: two words in camel case joined by a dot. */
    private static final Pattern A_SETTING = Pattern.compile("\\b([a-z]+)\\.([a-z][A-Za-z]+)\\b");

    @Test
    @DisplayName("the API failing at its job is an error: falling over, never answering, accepting "
            + "what its document forbids")
    void the_errors() {
        assertThat(Stream.of(WfcFault.HTTP_STATUS_500, WfcFault.HTTP_OTHER_STATUS_5XX,
                        WfcFault.SCHEMA_VALIDATION_BYPASS, WfcFault.SECURITY_CALL_TIMEOUT)
                .map(FaultReading::of))
                .allSatisfy(reading -> assertThat(reading.severity()).isEqualTo(Severity.ERROR));
    }

    @Test
    @DisplayName("the API answering something its document does not describe is a warning")
    void the_warnings() {
        assertThat(Stream.of(WfcFault.SCHEMA_INVALID_RESPONSE,
                        WfcFault.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED,
                        RestestFault.UNDECLARED_STATUS_CODE, RestestFault.UNDECLARED_CONTENT_TYPE)
                .map(FaultReading::of))
                .allSatisfy(reading -> assertThat(reading.severity()).isEqualTo(Severity.WARNING));
    }

    @Test
    @DisplayName("each kind has a title of RESTest's own, never the catalogue's name, and no two "
            + "kinds share one")
    void each_title_is_restests_own() {
        assertThat(REPORTED).allSatisfy(kind ->
                assertThat(FaultReading.of(kind).title()).isNotEqualTo(kind.descriptiveName()));
        assertThat(REPORTED.stream().map(kind -> FaultReading.of(kind).title()))
                .doesNotHaveDuplicates();
        assertThat(FaultReading.of(WfcFault.HTTP_STATUS_500).title())
                .isEqualTo("Internal server error");
        assertThat(FaultReading.of(WfcFault.SCHEMA_INVALID_RESPONSE).title())
                .isEqualTo("Reply off schema");
    }

    @Test
    @DisplayName("each kind says what to look at, in a line that starts as a sentence's middle")
    void each_kind_has_advice() {
        assertThat(REPORTED).allSatisfy(kind -> assertThat(FaultReading.of(kind).advice())
                .hasValueSatisfying(advice -> {
                    assertThat(advice).isNotBlank();
                    assertThat(Character.isLowerCase(advice.charAt(0))).isTrue();
                    assertThat(advice).doesNotEndWith(".");
                }));
    }

    @Test
    @DisplayName("a setting an advice names is one the tool has, so nobody is sent to a setting "
            + "that was renamed")
    void every_setting_named_in_an_advice_exists() {
        List<String> named = REPORTED.stream()
                .flatMap(kind -> FaultReading.of(kind).advice().stream())
                .flatMap(advice -> A_SETTING.matcher(advice).results())
                .map(found -> found.group(1) + "." + found.group(2))
                .toList();

        assertThat(named).contains("engine.readTimeout");
        assertThat(named).allSatisfy(name -> assertThat(SettingKey.named(name))
                .describedAs("an advice names %s, and there is no such setting", name)
                .isPresent());
    }

    @Test
    @DisplayName("a kind RESTest has no words for is an error, under its own name, with nothing to "
            + "advise")
    void an_unknown_kind_is_an_error_under_its_own_name() {
        FaultCategory somebodyElses = new FaultCategory() {
            @Override
            public int code() {
                return 950;
            }

            @Override
            public String descriptiveName() {
                return "Somebody Else's Kind";
            }

            @Override
            public String testCaseLabel() {
                return "somebodyElses";
            }
        };

        assertThat(FaultReading.of(somebodyElses))
                .isEqualTo(new FaultReading(Severity.ERROR, "Somebody Else's Kind",
                        Optional.empty()));
    }

    @Test
    @DisplayName("a kind of the catalogue no rule reports is read as one RESTest does not know")
    void a_kind_no_rule_reports_is_not_known() {
        assertThat(FaultReading.of(WfcFault.HTTP_NONWORKING_DELETE))
                .isEqualTo(new FaultReading(Severity.ERROR,
                        WfcFault.HTTP_NONWORKING_DELETE.descriptiveName(), Optional.empty()));
    }

    @Test
    @DisplayName("a reading needs a title")
    void a_reading_needs_a_title() {
        assertThatThrownBy(() -> new FaultReading(Severity.ERROR, " ", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("each severity is written as one plain word")
    void each_severity_has_a_word() {
        assertThat(Severity.ERROR.word()).isEqualTo("error");
        assertThat(Severity.WARNING.word()).isEqualTo("warning");
    }
}
