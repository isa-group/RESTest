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
        assertThat(text(object(report, "faultCatalogue"), "version")).isEqualTo("0.8.0");
        assertThat(text(report, "createdAt")).isEqualTo("2026-09-13T10:30:00Z");
        assertThat(text(object(report, "api"), "title")).isEqualTo("Pets");
        assertThat(text(object(report, "api"), "baseUrl")).isEqualTo(Runs.BASE);
    }

    @Test
    @DisplayName("the report writes its members in one fixed order, so two runs can be compared")
    void the_members_are_written_in_a_fixed_order() {
        JsonValue.JsonObject report = run(JsonReport.inMemory(fixedClock()));

        // Pinned by naming the order rather than by running twice, because the short way of writing
        // these objects shuffles them once per process, not once per call: two runs inside one test
        // would agree with each other and disagree with tomorrow's. Much of what a report a machine
        // can read is for is comparing one run against the next, and a key order that moved would
        // show differences where nothing had differed.
        assertThat(object(report, "tool").members().keySet())
                .containsExactly("name", "version");
        assertThat(object(report, "faultCatalogue").members().keySet())
                .containsExactly("name", "version");
        assertThat(object(report, "api").members().keySet())
                .containsExactly("title", "baseUrl");
    }

    @Test
    @DisplayName("server errors are counted over replies and by operation, and say so")
    void server_errors_are_counted_and_the_counting_is_stated() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        for (int again = 0; again < 50; again++) {
            report.on(new RunEvent.InteractionCompleted(WHEN,
                    Runs.attempt("GET /pets", "/pets", 500)));
        }
        report.on(new RunEvent.InteractionCompleted(WHEN,
                Runs.attempt("GET /shelters", "/shelters", 503)));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /vets", "/vets", 200)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));

        JsonValue.JsonObject errors =
                object((JsonValue.JsonObject) report.document().orElseThrow(), "serverErrors");

        assertThat(number(errors, "operationsAnswering500")).isEqualTo(1);
        assertThat(number(errors, "operationsAnsweringAny5xx")).isEqualTo(2);
        // The criterion travels with the number. A fault count that does not say what it counted
        // cannot be put beside anybody else's, which is the whole reason for using a shared
        // catalogue in the first place.
        assertThat(text(errors, "countedOver")).isEqualTo("replies");
        assertThat(text(errors, "distinctBy")).isEqualTo("operation");
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
                .hasSize(JsonReport.WRITE_UPS_PER_OPERATION_AND_KIND);
        assertThat(number(totals, "faultsWrittenInFull") + number(totals, "faultsCountedOnly"))
                .describedAs("written out or only counted: every fault is one of the two, and the "
                        + "two add up to all of them")
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
                .containsExactlyInAnyOrder(100, 200);
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
        assertThat(number(busiest, "writtenInFull") + number(busiest, "countedOnly"))
                .describedAs("and each row's own numbers account for all of its faults")
                .isEqualTo(300);
        assertThat(number(object(written, "totals"), "faultKinds")).isEqualTo(2);
        assertThat(number(object(written, "totals"), "operationsWithFaults")).isEqualTo(2);
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
        assertThat(number(limits, "writeUpsPerOperationAndKind"))
                .isEqualTo(JsonReport.WRITE_UPS_PER_OPERATION_AND_KIND);
        assertThat(number(limits, "writeUpsInTotal")).isEqualTo(JsonReport.WRITE_UPS_IN_TOTAL);
        assertThat(number(limits, "mostBodyBytesKept"))
                .describedAs("a reader who finds a reply cut short should be able to tell it was us "
                        + "who cut it, and at what length, without knowing which version wrote this")
                .isEqualTo((int) JsonReport.MOST_BODY_BYTES_KEPT);
    }

    @Test
    @DisplayName("a huge reply is quoted in part, and says how much of it there really was")
    void a_huge_reply_is_quoted_in_part() {
        int sent = 600 * 1024;
        JsonReport report = JsonReport.inMemory();
        report.on(new RunEvent.FaultFound(Instant.EPOCH,
                Runs.fellOverWithBody("GET /pets", sent)));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        JsonValue.JsonObject body = object(object(object((JsonValue.JsonObject)
                array(written, "findings").elements().get(0), "interaction"), "outcome"), "body");
        assertThat(text(body, "text").length())
                .describedAs("without this a single write-up is as big as the API felt like being, "
                        + "and counting write-ups would bound nothing at all")
                .isLessThanOrEqualTo((int) JsonReport.MOST_BODY_BYTES_KEPT);
        assertThat(number(body, "wireLength"))
                .describedAs("and it says how much really arrived, so our trimming is never "
                        + "mistaken for the API having sent less than it did")
                .isEqualTo(sent);
        assertThat(report.asText().orElseThrow().length())
                .describedAs("one enormous reply no longer decides the size of the file")
                .isLessThan(sent);
    }

    @Test
    @DisplayName("every attempt is accounted for, not only the ones something was wrong with")
    void every_attempt_is_accounted_for() {
        JsonReport report = JsonReport.inMemory();
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Runs.attempt("GET /pets", "/pets", 400)));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Runs.attempt("GET /pets", "/pets", 400)));
        Finding fault = Runs.fellOver("GET /pets");
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH, fault.interaction()));
        report.on(new RunEvent.FaultFound(Instant.EPOCH, fault));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        JsonValue.JsonObject replies = object(written, "replies");
        assertThat(number(replies, "total")).isEqualTo(4);
        JsonValue.JsonObject byClass = object(replies, "byClass");
        assertThat(number(byClass, "2xx")).isEqualTo(1);
        assertThat(number(byClass, "4xx"))
                .describedAs("a run where almost everything comes back refused is a run whose "
                        + "requests are the problem, not the API - and no list of faults can say so")
                .isEqualTo(2);
        assertThat(number(byClass, "5xx")).isEqualTo(1);
        assertThat(number(replies, "withNothingWrong"))
                .describedAs("three attempts had nothing found wrong with them. This cannot be got "
                        + "by subtracting faults from attempts: one attempt can be wrong in several "
                        + "ways at once, so the two counts are of different things")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("faults are also counted by the kind of answer that carried them")
    void faults_are_counted_by_the_answer_that_carried_them() {
        JsonReport report = JsonReport.inMemory();
        for (int found = 0; found < 3; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver("GET /pets")));
        }
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.wrongShape("GET /pets")));
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.neverAnswered("GET /gone")));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        assertThat(array(written, "faultsByStatus").elements().stream()
                .map(row -> (JsonValue.JsonObject) row)
                .map(row -> "F" + number(row, "code") + " " + text(row, "statusClass") + " = "
                        + number(row, "count")))
                .describedAs("a developer who only watches their logs for 500s is not seeing the "
                        + "fault that came back as a perfectly ordinary 200, and this is the table "
                        + "that shows it. An attempt that got no reply has no code, so it has a "
                        + "name of its own rather than being lost")
                .containsExactlyInAnyOrder("F100 5xx = 3", "F200 2xx = 1", "F100 noReply = 1");
    }

    @Test
    @DisplayName("a fault says what the API answered, which is the first thing anybody asks")
    void a_fault_says_what_came_back() {
        JsonValue.JsonObject written = afterFinding(1, Runs::fellOver);

        JsonValue.JsonObject finding =
                (JsonValue.JsonObject) array(written, "findings").elements().get(0);
        assertThat(number(finding, "status")).isEqualTo(500);
        assertThat(text(finding, "statusClass")).isEqualTo("5xx");
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
