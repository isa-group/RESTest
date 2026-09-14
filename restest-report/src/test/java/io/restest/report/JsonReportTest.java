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

import io.restest.core.event.RunEvent;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReportTest {

    private static final Instant WHEN = Instant.parse("2026-09-13T10:30:00Z");

    @Test
    @DisplayName("the report says what found it, against which catalogue, and when")
    void the_report_names_itself() {
        JsonValue.JsonObject report = run(JsonReport.inMemory(fixedClock()));

        assertThat(text(object(report, "tool"), "name")).isEqualTo("RESTest");
        assertThat(text(object(report, "faultCatalogue"), "name"))
                .isEqualTo("Web Fuzzing Commons");
        assertThat(text(object(report, "faultCatalogue"), "version")).isEqualTo("0.7.0");
        assertThat(text(report, "createdAt")).isEqualTo("2026-09-13T10:30:00Z");
        assertThat(text(object(report, "api"), "title")).isEqualTo("Pets");
        assertThat(text(object(report, "api"), "baseUrl")).isEqualTo(Runs.BASE);
    }

    @Test
    @DisplayName("the totals say how much was attempted and how much was found")
    void the_totals_are_counted() {
        JsonValue.JsonObject totals = object(run(JsonReport.inMemory(fixedClock())), "totals");

        assertThat(number(totals, "requests")).isEqualTo(2);
        assertThat(number(totals, "operations")).isEqualTo(2);
        assertThat(number(totals, "faults")).isEqualTo(2);
        assertThat(text(totals, "elapsed")).isEqualTo("PT10S");
    }

    @Test
    @DisplayName("how much of the run was spent waiting for the API is in the report")
    void the_engine_statistics_are_reported() {
        JsonValue.JsonObject engine = object(run(JsonReport.inMemory(fixedClock())), "engine");

        assertThat(number(engine, "requestsSent")).isEqualTo(20);
        assertThat(text(engine, "wallClock")).isEqualTo("PT10S");
        assertThat(text(engine, "idle")).isEqualTo("PT3S");
        assertThat(number(engine, "peakConcurrency")).isEqualTo(4);
    }

    @Test
    @DisplayName("each fault carries its kind, its operation, what is wrong and how to repeat it")
    void each_fault_is_complete_on_its_own() {
        JsonValue.JsonArray findings =
                array(run(JsonReport.inMemory(fixedClock())), "findings");

        JsonValue.JsonObject first = (JsonValue.JsonObject) findings.elements().get(0);
        assertThat(number(object(first, "category"), "code")).isEqualTo(100);
        assertThat(text(object(first, "category"), "label")).isEqualTo("F100:HTTP Status 500");
        assertThat(text(object(first, "category"), "testCaseLabel"))
                .isEqualTo("causes500_internalServerError");
        assertThat(text(first, "operation")).isEqualTo("GET /pets");
        assertThat(text(first, "curl")).startsWith("curl -i -X GET 'https://api.example/pets'");
    }

    @Test
    @DisplayName("each fault carries the whole attempt, in the same shape a stored run uses")
    void each_fault_carries_its_evidence() {
        JsonValue.JsonArray findings =
                array(run(JsonReport.inMemory(fixedClock())), "findings");

        JsonValue.JsonObject interaction =
                object((JsonValue.JsonObject) findings.elements().get(0), "interaction");
        JsonValue.JsonObject request = object(interaction, "request");

        assertThat(text(request, "method")).isEqualTo("GET");
        assertThat(text(request, "url")).isEqualTo(Runs.BASE + "/pets");
        assertThat(interaction.member("outcome")).isPresent();
    }

    @Test
    @DisplayName("faults are counted by kind, so a build server can decide on one number")
    void faults_are_counted_by_kind() {
        JsonValue.JsonArray byCategory =
                array(run(JsonReport.inMemory(fixedClock())), "faultsByCategory");

        assertThat(byCategory.elements()).hasSize(2);
        JsonValue.JsonObject first = (JsonValue.JsonObject) byCategory.elements().get(0);
        assertThat(number(first, "code")).isEqualTo(100);
        assertThat(number(first, "count")).isEqualTo(1);
    }

    @Test
    @DisplayName("the report is written to a file when the run finishes, and can be read back")
    void the_report_is_written_to_a_file(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("report.json");

        run(JsonReport.to(file));

        assertThat(file).exists();
        JsonValue written = JsonText.read(Files.readString(file));
        assertThat(((JsonValue.JsonObject) written).member("findings")).isPresent();
    }

    @Test
    @DisplayName("nothing has been written before the run finishes")
    void nothing_is_written_before_the_end() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(new RunEvent.RunStarted(WHEN, "Pets", Runs.BASE));

        assertThat(report.document()).isEmpty();
        assertThat(report.asText()).isEmpty();
    }

    @Test
    @DisplayName("a run that found nothing still produces a report, with nothing in it")
    void a_clean_run_still_reports() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(new RunEvent.RunStarted(WHEN, "Pets", Runs.BASE));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(1), Runs.engine()));

        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();
        assertThat(array(written, "findings").elements()).isEmpty();
        assertThat(number(object(written, "totals"), "faults")).isZero();
        assertThat(report.asText()).isPresent();
    }

    private static JsonValue.JsonObject run(JsonReport report) {
        report.on(new RunEvent.RunStarted(WHEN, "Pets", Runs.BASE));
        report.on(new RunEvent.TestCasePlanned(WHEN,
                Runs.attempt("GET /pets", "/pets", 200).testCase()));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 500)));
        report.on(new RunEvent.InteractionCompleted(WHEN,
                Runs.attempt("GET /pets/{petId}", "/pets/7", 200)));
        report.on(new RunEvent.FaultFound(WHEN, Runs.fellOver()));
        report.on(new RunEvent.FaultFound(WHEN, Runs.wrongShape(1)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        return (JsonValue.JsonObject) report.document().orElseThrow();
    }

    private static Clock fixedClock() {
        return Clock.fixed(WHEN, ZoneOffset.UTC);
    }

    private static JsonValue.JsonObject object(JsonValue.JsonObject parent, String name) {
        return (JsonValue.JsonObject) parent.member(name).orElseThrow();
    }

    private static JsonValue.JsonArray array(JsonValue.JsonObject parent, String name) {
        return (JsonValue.JsonArray) parent.member(name).orElseThrow();
    }

    private static String text(JsonValue.JsonObject parent, String name) {
        return ((JsonValue.JsonString) parent.member(name).orElseThrow()).value();
    }

    private static int number(JsonValue.JsonObject parent, String name) {
        return ((JsonValue.JsonNumber) parent.member(name).orElseThrow()).value().intValueExact();
    }

    @Test
    @DisplayName("a run finding faults by the thousand writes a bounded file, and says so")
    void very_many_faults_are_counted_in_full_and_written_in_part() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < JsonReport.FINDINGS_WRITTEN + 250; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();
        JsonValue.JsonObject totals = (JsonValue.JsonObject) written.member("totals").orElseThrow();
        JsonValue.JsonArray findings =
                (JsonValue.JsonArray) written.member("findings").orElseThrow();

        assertThat(number(totals, "faults"))
                .describedAs("every fault is counted")
                .isEqualTo(JsonReport.FINDINGS_WRITTEN + 250);
        assertThat(findings.elements())
                .describedAs("but the file does not grow without limit")
                .hasSize(JsonReport.FINDINGS_WRITTEN);
        assertThat(number(totals, "faultsWrittenInFull"))
                .describedAs("and it says how many of them it wrote out, so nothing is quietly "
                        + "missing")
                .isEqualTo(JsonReport.FINDINGS_WRITTEN);
    }
}
