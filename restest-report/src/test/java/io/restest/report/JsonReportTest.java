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
import io.restest.core.oracle.Finding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
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
    @DisplayName("a thousand faults of one kind get a handful of write-ups rather than a thousand")
    void very_many_faults_are_counted_in_full_and_written_in_part() {
        JsonValue.JsonObject written = afterFinding(1250, Runs::fellOver);
        JsonValue.JsonObject totals = object(written, "totals");

        assertThat(number(totals, "faults"))
                .describedAs("every fault is counted")
                .isEqualTo(1250);
        assertThat(array(written, "findings").elements())
                .describedAs("but the file does not grow without limit")
                .hasSize(JsonReport.FULL_EVIDENCE_PER_OPERATION_AND_KIND);
        assertThat(number(totals, "faultsWrittenInFull"))
                .describedAs("and it says how many of them it wrote out, so nothing is quietly "
                        + "missing")
                .isEqualTo(JsonReport.FULL_EVIDENCE_PER_OPERATION_AND_KIND);
        assertThat(array(written, "otherFaults").elements())
                .describedAs("the rest are named rather than vanishing, up to their own limit")
                .hasSize(JsonReport.NAMES_PER_OPERATION_AND_KIND);
        assertThat(number(totals, "faultsWrittenInFull") + number(totals, "faultsNamed")
                + number(totals, "faultsCountedOnly"))
                .describedAs("written out, named, or only counted: every fault is exactly one of "
                        + "the three, and the three add up to all of them")
                .isEqualTo(1250);
    }

    @Test
    @DisplayName("write-ups are spread across operations rather than spent on the first to repeat")
    void the_allowance_is_per_operation_and_kind() {
        JsonReport report = JsonReport.inMemory();
        // The first operation misbehaves five hundred times before the second is even tried, which
        // is exactly the order that used to consume the whole file.
        for (int found = 0; found < 500; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver("GET /pets")));
        }
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver("GET /shelters")));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        assertThat(array(written, "findings").elements().stream()
                .map(finding -> text((JsonValue.JsonObject) finding, "operation"))
                .distinct())
                .describedAs("the operation that faulted once still gets its attempt written out; "
                        + "a reader must not have to guess what went wrong there")
                .containsExactlyInAnyOrder("GET /pets", "GET /shelters");
    }

    @Test
    @DisplayName("a kind of fault first seen at the end of a run is still written out")
    void a_kind_first_seen_late_is_not_crowded_out() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < 500; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver("GET /pets")));
        }
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.wrongShape("GET /pets")));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        assertThat(array(written, "findings").elements().stream()
                .map(finding -> number(object((JsonValue.JsonObject) finding, "category"), "code"))
                .distinct())
                .describedAs("a second kind of fault appearing late in a run is a discovery, and "
                        + "the report it never reaches is the report that hid it")
                .containsExactlyInAnyOrder(100, 101);
    }

    @Test
    @DisplayName("every operation and kind that faulted is listed, however few were written out")
    void every_pair_is_counted_exactly() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < 300; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver("GET /pets")));
        }
        for (int found = 0; found < 7; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.wrongShape("GET /shelters")));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        List<JsonValue> rows = array(written, "faultsByOperation").elements();
        assertThat(rows).hasSize(2);
        assertThat(rows.stream()
                .map(row -> text((JsonValue.JsonObject) row, "operation") + " "
                        + number((JsonValue.JsonObject) row, "count")))
                .describedAs("the counting is exact and complete even where the writing is not: "
                        + "this list is as long as the API, not as long as the run")
                .containsExactlyInAnyOrder("GET /pets 300", "GET /shelters 7");
        JsonValue.JsonObject busiest = (JsonValue.JsonObject) rows.get(0);
        assertThat(number(busiest, "writtenInFull") + number(busiest, "named")
                + number(busiest, "countedOnly"))
                .describedAs("and each row's own three numbers account for all of its faults")
                .isEqualTo(300);
        assertThat(number(object(written, "totals"), "faultKinds")).isEqualTo(2);
        assertThat(number(object(written, "totals"), "operationsWithFaults")).isEqualTo(2);
    }

    @Test
    @DisplayName("a fault past its allowance is named by the request that caused it")
    void named_faults_carry_the_request() {
        JsonValue.JsonObject written = afterFinding(50, Runs::fellOver);

        JsonValue.JsonObject named =
                (JsonValue.JsonObject) array(written, "otherFaults").elements().get(0);
        assertThat(text(named, "url"))
                .describedAs("a run that kept no database has nothing to look an identifier up in, "
                        + "so the line has to carry the request itself to be worth anything")
                .startsWith(Runs.BASE);
        assertThat(text(named, "method")).isEqualTo("GET");
        assertThat(number(named, "status")).isEqualTo(500);
        assertThat(named.member("interactionId")).isPresent();
    }

    @Test
    @DisplayName("a fault whose reply never arrived is named without a status code being invented")
    void a_fault_with_no_reply_is_named_without_a_status() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < 20; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.neverAnswered("GET /pets")));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        JsonValue.JsonObject named =
                (JsonValue.JsonObject) array(written, "otherFaults").elements().get(0);
        assertThat(named.member("status"))
                .describedAs("nothing came back, so there is no status code; writing a zero would "
                        + "be stating something about the API that never happened")
                .isEmpty();
        assertThat(text(named, "url")).startsWith(Runs.BASE);
    }

    @Test
    @DisplayName("faults that look alike are counted one by one rather than merged into one")
    void look_alike_faults_are_never_merged() {
        JsonValue.JsonObject written = afterFinding(40, Runs::fellOver);

        assertThat(number(object(written, "totals"), "faults"))
                .describedAs("deciding that two failures are the same underlying problem is "
                        + "inference this version does not do. Forty identical-looking faults are "
                        + "forty faults, and this test exists to fail the day that changes")
                .isEqualTo(40);
        assertThat(number((JsonValue.JsonObject) array(written, "faultsByOperation")
                .elements().get(0), "count"))
                .isEqualTo(40);
        assertThat(number(written, 0, "count")).isEqualTo(40);
    }

    @Test
    @DisplayName("the report says what its own limits were, so a reader knows why something is out")
    void the_report_states_its_own_limits() {
        JsonValue.JsonObject written = afterFinding(200, Runs::fellOver);

        JsonValue.JsonObject limits = object(written, "limits");
        assertThat(number(limits, "fullEvidencePerOperationAndKind"))
                .isEqualTo(JsonReport.FULL_EVIDENCE_PER_OPERATION_AND_KIND);
        assertThat(number(limits, "namesPerOperationAndKind"))
                .isEqualTo(JsonReport.NAMES_PER_OPERATION_AND_KIND);
        assertThat(limits.member("fullEvidenceStoppedEarly"))
                .describedAs("a kind having had its share and the file having run out are different "
                        + "things, and a reader should be able to tell which happened")
                .contains(JsonValue.of(false));
    }

    @Test
    @DisplayName("an API answering with large bodies produces a bounded file, not bounded faults")
    void the_file_is_bounded_in_bytes_rather_than_in_faults() {
        JsonReport report = JsonReport.inMemory();
        // Six hundred kilobytes a reply: three fit inside the file's allowance and the fourth does
        // not, so the limit that binds here is the one counted in bytes, not the one counted in
        // faults. An allowance of five would let this file past three megabytes.
        for (int found = 0; found < 20; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH,
                    Runs.fellOverWithBody("GET /pets", 600 * 1024)));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        assertThat(array(written, "findings").elements())
                .describedAs("a limit on how many unbounded things are kept is not a limit at all: "
                        + "one reply can be a megabyte, so the bytes have to be counted")
                .hasSizeLessThan(JsonReport.FULL_EVIDENCE_PER_OPERATION_AND_KIND);
        assertThat(report.asText().orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThan((int) (JsonReport.FULL_EVIDENCE_BYTES + JsonReport.NAMES_BYTES));
        assertThat(object(written, "limits").member("fullEvidenceStoppedEarly"))
                .describedAs("and the file says the limit was what stopped it, rather than leaving "
                        + "a reader to think that was all there was")
                .contains(JsonValue.of(true));
        assertThat(number(object(written, "totals"), "faults")).isEqualTo(20);
    }

    @Test
    @DisplayName("a full file stops building faults rather than building and discarding each one")
    void a_full_file_stops_doing_the_work() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < 5000; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH,
                    Runs.fellOverWithBody("GET /pets", 600 * 1024)));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        assertThat(report.converted())
                .describedAs("turning a fault into JSON means copying its whole request and reply. "
                        + "Doing that for every fault after the file is full, only to throw each one "
                        + "away, costs more than writing it would have - and it happens on the one "
                        + "thread the oracles and the reports share, so it stops the run sending "
                        + "requests. The work is bounded by how many kinds of fault there are, not "
                        + "by how many faults")
                .isLessThanOrEqualTo(JsonReport.FULL_EVIDENCE_PER_OPERATION_AND_KIND + 1);
        assertThat(number(object((JsonValue.JsonObject) report.document().orElseThrow(), "totals"),
                "faults"))
                .describedAs("and every one of them is still counted")
                .isEqualTo(5000);
    }

    @Test
    @DisplayName("a fault too large to write out is named instead of vanishing from the file")
    void a_fault_too_large_to_write_is_still_named() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < 20; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH,
                    Runs.fellOverWithBody("GET /pets", 600 * 1024)));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        assertThat(array(written, "otherFaults").elements())
                .describedAs("no room to quote it in full is not a reason to leave no trace of it: "
                        + "the request that caused it costs a line")
                .isNotEmpty();
    }

    /** One run in which the same maker is asked for a fault as many times as given. */
    private static JsonValue.JsonObject afterFinding(
            int howMany, Function<String, Finding> maker) {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < howMany; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, maker.apply("GET /pets")));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        return (JsonValue.JsonObject) report.document().orElseThrow();
    }

    private static int number(JsonValue.JsonObject parent, int index, String name) {
        return number((JsonValue.JsonObject) array(parent, "faultsByCategory")
                .elements().get(index), name);
    }
}
