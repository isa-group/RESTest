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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.auth.Place;
import io.restest.core.event.RunEvent;
import io.restest.core.event.Unauthorized;
import io.restest.core.event.Waited;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Interaction;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.oracle.Finding;
import io.restest.core.settings.ReportSettings;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsInEffect;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
        assertThat(text(object(report, "faultCatalogue"), "version")).isEqualTo("0.9.0");
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
        assertThat(report.members().keySet())
                .describedAs("how the run was started beside what was tested; whether it was cut "
                        + "short, what it amounts to and whether the API stopped it, before any "
                        + "number they qualify")
                .startsWith("tool", "faultCatalogue", "createdAt", "api", "run", "cutShort",
                        "verdict", "unauthorized", "toldToWait", "totals");
        assertThat(report.members().keySet())
                .describedAs("every operation, then what could not be tried, read or checked")
                .containsSubsequence("operations", "skippedOperations", "documentIssues",
                        "keysNotGiven", "notChecked", "dictionaries");
        assertThat(object(report, "run").members().keySet())
                .containsExactly("plan", "settingsFile", "stored");
        assertThat(report.members().keySet())
                .describedAs("the run over time straight after its stretches, which are marked on it")
                .containsSubsequence("phases", "timeline", "replies");
        assertThat(object(report, "timeline").members().keySet())
                .containsExactly("startedAt", "budget", "interval", "intervals");
    }

    @Test
    @DisplayName("a run told to wait says how much of its idle time that was, how often and how long "
            + "the whole API waited, and each operation told to wait; one never told says so with "
            + "zeros; one that does not wait when told says nothing")
    void a_run_told_to_wait_says_who_and_for_how_long() {
        assertThat(run(JsonReport.inMemory(fixedClock())).member("toldToWait"))
                .describedAs("a run that does not wait when told to")
                .contains(JsonValue.NULL);

        JsonValue.JsonObject never = waitedRun(Waited.never());
        assertThat(JsonText.write(never.member("toldToWait").orElseThrow()))
                .isEqualTo("{\"idle\":\"PT0S\",\"wholeApi\":{\"times\":0,\"waited\":\"PT0S\"},"
                        + "\"operations\":[]}");

        JsonValue.JsonObject told = waitedRun(new Waited(Duration.ofMillis(2500),
                new Waited.WholeApi(1, Duration.ofSeconds(3)),
                List.of(new Waited.ForOperation(OperationId.of("POST /tans"), 9, 1,
                        Duration.ofSeconds(57), 54260))));
        assertThat(JsonText.write(told.member("toldToWait").orElseThrow()))
                .isEqualTo("{\"idle\":\"PT2.5S\",\"wholeApi\":{\"times\":1,\"waited\":\"PT3S\"},"
                        + "\"operations\":[{\"operation\":\"POST /tans\",\"times\":9,\"stated\":1,"
                        + "\"waited\":\"PT57S\",\"turnsSkipped\":54260}]}");
        assertThat(text(told, "verdict")).describedAs("waiting changes no verdict")
                .isEqualTo(text(never, "verdict"));
    }

    @Test
    @DisplayName("a run that ended by itself says it was not cut short; one stopped from outside says it was")
    void a_run_stopped_from_outside_says_so() {
        assertThat(run(JsonReport.inMemory(fixedClock())).member("cutShort"))
                .contains(JsonValue.of(false));

        JsonReport stopped = JsonReport.inMemory(fixedClock());
        stopped.on(Runs.started(WHEN));
        stopped.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));
        stopped.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(3), Runs.engine(), true));
        JsonValue.JsonObject written = (JsonValue.JsonObject) stopped.document().orElseThrow();

        assertThat(written.member("cutShort")).contains(JsonValue.of(true));
        assertThat(number(object(written, "totals"), "requests"))
                .describedAs("and what it counts is what it found until then")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a run the API stopped by refusing its whole opening lap says when, how many "
            + "requests that lap sent and where each key it held went, and ends as unauthorized; "
            + "one stopped from outside as well is stopped, and still says it")
    void a_run_the_api_refused_says_so() {
        JsonValue.JsonObject ordinary = run(JsonReport.inMemory(fixedClock()));
        assertThat(ordinary.member("unauthorized")).contains(JsonValue.NULL);

        Unauthorized stop = new Unauthorized(WHEN, 2);
        JsonValue.JsonObject refused = refusedRun(false, stop);
        assertThat(text(refused, "verdict")).isEqualTo("unauthorized");
        JsonValue.JsonObject block = object(refused, "unauthorized");
        assertThat(block.members().keySet()).containsExactly("at", "lapRequests", "keysHeld");
        assertThat(text(block, "at")).isEqualTo(WHEN.toString());
        assertThat(number(block, "lapRequests")).isEqualTo(2);
        assertThat(JsonText.write(block.member("keysHeld").orElseThrow()))
                .isEqualTo("[{\"in\":\"header\",\"name\":\"X-Shelter-Key\"}]");
        assertThat(refused.member("cutShort")).contains(JsonValue.of(false));

        JsonValue.JsonObject both = refusedRun(true, stop);
        assertThat(text(both, "verdict"))
                .describedAs("stopped from outside while it waited for its last answers")
                .isEqualTo("stopped");
        assertThat(both.member("unauthorized")).isPresent().get()
                .isNotEqualTo(JsonValue.NULL);
    }

    private static JsonValue.JsonObject refusedRun(boolean thenCutShort, Unauthorized stop) {
        JsonReport report = JsonReport.inMemory(fixedClock(),
                SettingsInEffect.of(Settings.defaults()),
                new JsonReport.Invocation(Optional.empty(), Optional.empty(), false, List.of(),
                        List.of(new Place(Place.Where.HEADER, "X-Shelter-Key"))));
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 401)));
        report.on(new RunEvent.InteractionCompleted(WHEN,
                Runs.attempt("GET /pets/{petId}", "/pets/1", 401)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(1), Runs.engine(),
                thenCutShort, Optional.of(stop)));
        return (JsonValue.JsonObject) report.document().orElseThrow();
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
    @DisplayName("the report says how many replies each check could not judge and why, on every "
            + "operation they came from, most first")
    void what_could_not_be_checked_is_written() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));
        report.on(Runs.notJudged("GET /vets", Runs.NO_SHAPE));
        report.on(Runs.notJudged("GET /pets", Runs.NO_SHAPE, Runs.NO_TYPE));
        report.on(Runs.notJudged("GET /pets", Runs.NO_SHAPE));
        report.on(Runs.notJudged("GET /owners", Runs.NO_TYPE));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));

        JsonValue.JsonArray notChecked =
                array((JsonValue.JsonObject) report.document().orElseThrow(), "notChecked");

        assertThat(notChecked.elements()).hasSize(2);
        JsonValue.JsonObject shape = (JsonValue.JsonObject) notChecked.elements().get(0);
        assertThat(shape.members().keySet()).containsExactly("code", "id", "label", "what",
                "reason", "why", "replies", "operations");
        assertThat(number(shape, "code")).isEqualTo(200);
        assertThat(text(shape, "id")).isEqualTo("SCHEMA_INVALID_RESPONSE");
        assertThat(text(shape, "what")).isEqualTo("the shape");
        assertThat(text(shape, "reason")).isEqualTo("noShapeDeclared");
        assertThat(text(shape, "why")).isEqualTo("the document gives no shape");
        assertThat(number(shape, "replies")).isEqualTo(3);
        assertThat(array(shape, "operations").elements())
                .describedAs("most replies first")
                .containsExactly(
                        JsonValue.object(Map.of("operation", JsonValue.of("GET /pets"),
                                "replies", JsonValue.of(2))),
                        JsonValue.object(Map.of("operation", JsonValue.of("GET /vets"),
                                "replies", JsonValue.of(1))));
        JsonValue.JsonObject type = (JsonValue.JsonObject) notChecked.elements().get(1);
        assertThat(number(type, "code")).isEqualTo(921);
        assertThat(text(type, "reason")).isEqualTo("noContentTypeDeclared");
        assertThat(array(type, "operations").elements())
                .describedAs("as many on each, so by name")
                .extracting(operation -> text((JsonValue.JsonObject) operation, "operation"))
                .containsExactly("GET /owners", "GET /pets");
    }

    @Test
    @DisplayName("a run that checked everything it could says so with an empty list")
    void a_run_that_checked_everything_writes_an_empty_list() {
        assertThat(array(run(JsonReport.inMemory(fixedClock())), "notChecked").elements())
                .isEmpty();
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
    @DisplayName("the report names every operation the run could not try, with the reason, in the "
            + "order it was told of them")
    void every_operation_that_could_not_be_tested_is_named_with_its_reason() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        // Not in alphabetical order, so that an order made up on the way would show.
        report.on(new RunEvent.OperationSkipped(WHEN, OperationId.of("uploadPhoto"),
                "its body can only be sent as multipart/form-data"));
        report.on(new RunEvent.OperationSkipped(WHEN, OperationId.of("filterPets"),
                "its parameter 'filter' is written in the deepObject style"));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));

        List<JsonValue.JsonObject> skipped = array(
                (JsonValue.JsonObject) report.document().orElseThrow(), "skippedOperations")
                .elements().stream().map(JsonValue.JsonObject.class::cast).toList();

        assertThat(skipped).extracting(each -> text(each, "operation"))
                .containsExactly("uploadPhoto", "filterPets");
        assertThat(skipped).extracting(each -> text(each, "reason")).containsExactly(
                "its body can only be sent as multipart/form-data",
                "its parameter 'filter' is written in the deepObject style");
        assertThat(skipped.get(0).members().keySet())
                .describedAs("in one fixed order, like every other object here")
                .containsExactly("operation", "reason");
    }

    @Test
    @DisplayName("the report names all of them, however few of them the screen was told to name")
    void the_report_names_every_one_whatever_the_screen_names() {
        io.restest.core.settings.Settings told = io.restest.core.settings.Settings.from(
                java.util.Map.of("report.skippedOperationsShownOnTheConsole", "1"));
        JsonReport report = JsonReport.inMemory(fixedClock(),
                io.restest.core.settings.SettingsInEffect.of(told));
        for (int operation = 0; operation < 12; operation++) {
            report.on(new RunEvent.OperationSkipped(WHEN, OperationId.of("operation" + operation),
                    "a reason"));
        }
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));

        assertThat(array((JsonValue.JsonObject) report.document().orElseThrow(),
                "skippedOperations").elements()).hasSize(12);
    }

    @Test
    @DisplayName("a run that could try every operation says so with an empty list, rather than by "
            + "leaving the list out")
    void a_run_that_skipped_nothing_writes_an_empty_list() {
        assertThat(array(run(JsonReport.inMemory(fixedClock())), "skippedOperations").elements())
                .isEmpty();
    }

    @Test
    @DisplayName("the report names every list of values the run held, with where it came from, and "
            + "every one it could not read, with the reason")
    void the_lists_read_and_refused_are_named() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.DictionaryRead(WHEN, "fuzzing", "built in"));
        report.on(new RunEvent.DictionaryRead(WHEN, "petshop-ids", "dictionaries/ids.yaml"));
        report.on(new RunEvent.DictionaryRefused(WHEN, "dictionaries/broken.yaml",
                "dictionaries/broken.yaml could not be read: unclosed list"));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));

        JsonValue.JsonObject dictionaries =
                object((JsonValue.JsonObject) report.document().orElseThrow(), "dictionaries");

        List<JsonValue.JsonObject> read = array(dictionaries, "read").elements().stream()
                .map(JsonValue.JsonObject.class::cast).toList();
        assertThat(read).extracting(each -> text(each, "name"))
                .containsExactly("fuzzing", "petshop-ids");
        assertThat(read).extracting(each -> text(each, "from"))
                .describedAs("by file as well as by name, since a name can be shared and a file "
                        + "is what whoever ran it knows")
                .containsExactly("built in", "dictionaries/ids.yaml");
        assertThat(read.get(0).members().keySet()).containsExactly("name", "from");
        List<JsonValue.JsonObject> refused = array(dictionaries, "refused").elements().stream()
                .map(JsonValue.JsonObject.class::cast).toList();
        assertThat(refused).singleElement().satisfies(each -> {
            assertThat(text(each, "from")).isEqualTo("dictionaries/broken.yaml");
            assertThat(text(each, "reason")).contains("unclosed list");
            assertThat(each.members().keySet()).containsExactly("from", "reason");
        });
        assertThat(dictionaries.members().keySet()).containsExactly("read", "refused");
    }

    @Test
    @DisplayName("a run that was handed nothing it could not read says so with an empty list")
    void a_run_that_refused_no_list_writes_an_empty_list() {
        JsonValue.JsonObject dictionaries =
                object(run(JsonReport.inMemory(fixedClock())), "dictionaries");

        assertThat(array(dictionaries, "refused").elements()).isEmpty();
    }

    @Test
    @DisplayName("how much of the run was spent waiting for the API is in the report")
    void the_engine_statistics_are_reported() {
        JsonValue.JsonObject engine = object(run(JsonReport.inMemory(fixedClock())), "engine");

        assertThat(number(engine, "requestsSent")).isEqualTo(20);
        assertThat(text(engine, "wallClock")).isEqualTo("PT10S");
        assertThat(text(engine, "idle")).isEqualTo("PT3S");
        assertThat(text(engine, "idleKeepingToTheRate"))
                .describedAs("an engine that kept to no rate").isEqualTo("PT0S");
        assertThat(number(engine, "peakConcurrency")).isEqualTo(4);
    }

    @Test
    @DisplayName("each fault carries its kind, its operation, what is wrong and how to repeat it")
    void each_fault_is_complete_on_its_own() {
        JsonValue.JsonArray findings =
                array(run(JsonReport.inMemory(fixedClock())), "findings");

        JsonValue.JsonObject first = (JsonValue.JsonObject) findings.elements().get(0);
        assertThat(number(object(first, "category"), "code")).isEqualTo(100);
        assertThat(text(object(first, "category"), "id")).isEqualTo("HTTP_STATUS_500");
        assertThat(text(object(first, "category"), "label")).isEqualTo("F100:HTTP Status 500");
        assertThat(text(object(first, "category"), "testCaseLabel"))
                .isEqualTo("causes500_internalServerError");
        assertThat(text(first, "operation")).isEqualTo("GET /pets");
        assertThat(text(first, "curl")).startsWith("curl -i -X GET 'https://api.example/pets'");
        assertThat(findings.elements().stream()
                .map(finding -> object((JsonValue.JsonObject) finding, "category"))
                .map(category -> number(category, "code") + " " + text(category, "id"))
                .distinct())
                .containsExactlyInAnyOrder("100 HTTP_STATUS_500", "200 SCHEMA_INVALID_RESPONSE");
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
        assertThat(byCategory.elements().stream()
                .map(row -> (JsonValue.JsonObject) row)
                .map(row -> number(row, "code") + " " + text(row, "id")))
                .describedAs("each kind carries the catalogue's key beside its number")
                .containsExactly("100 HTTP_STATUS_500", "200 SCHEMA_INVALID_RESPONSE");
    }

    @Test
    @DisplayName("the report is written to a file when the run finishes, and can be read back")
    void the_report_is_written_to_a_file(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("report.json");

        run(JsonReport.to(file));

        assertThat(file).exists();
        JsonValue written = JsonText.read(Files.readString(file));
        assertThat(((JsonValue.JsonObject) written).member("findings")).isPresent();
        assertThat(JsonReport.whileBeingWritten(file))
                .describedAs("the file it was written into before it was given its name is gone")
                .isEqualTo(directory.resolve("report.json.partial"))
                .doesNotExist();
    }

    @Test
    @DisplayName("a report is written whole under its name or not at all, never half of it")
    void a_report_is_whole_or_absent(@TempDir Path directory) throws IOException {
        Path earlier = directory.resolve("earlier").resolve("report.json");
        Files.createDirectories(earlier.getParent());
        Files.writeString(earlier, "{\"an\": \"earlier report\"}");
        run(JsonReport.to(earlier));
        assertThat(((JsonValue.JsonObject) JsonText.read(Files.readString(earlier)))
                .member("findings")).describedAs("one already there is replaced whole").isPresent();

        // A name that cannot be given, because a directory holding something already has it.
        Path taken = directory.resolve("taken").resolve("report.json");
        Files.createDirectories(taken);
        Files.writeString(taken.resolve("something"), "not ours");
        assertThatThrownBy(() -> run(JsonReport.to(taken)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("the report could not be written to " + taken);
        assertThat(JsonReport.whileBeingWritten(taken))
                .describedAs("what could not be given its name is not left behind either")
                .doesNotExist();
    }

    @Test
    @DisplayName("nothing has been written before the run finishes")
    void nothing_is_written_before_the_end() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));

        assertThat(report.document()).isEmpty();
        assertThat(report.asText()).isEmpty();
    }

    @Test
    @DisplayName("a run that found nothing still produces a report, with nothing in it")
    void a_clean_run_still_reports() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(1), Runs.engine()));

        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();
        assertThat(array(written, "findings").elements()).isEmpty();
        assertThat(number(object(written, "totals"), "faults")).isZero();
        assertThat(report.asText()).isPresent();
    }

    @Test
    @DisplayName("each stretch of the run is written with how long it took and what it achieved")
    void each_stretch_of_the_run_is_written() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        io.restest.core.execution.TestCase first = io.restest.core.execution.TestCase.of(
                io.restest.core.model.OperationId.of("POST /pets"), java.util.List.of());
        io.restest.core.execution.TestCase second = io.restest.core.execution.TestCase.of(
                io.restest.core.model.OperationId.of("GET /pets/{petId}"), java.util.List.of());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.PhaseStarted(WHEN, "opening lap"));
        report.on(new RunEvent.TestCasePlanned(WHEN, first));
        report.on(new RunEvent.TestCasePlanned(WHEN, second));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.answering(first, 201)));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.neverAnswering(second)));
        report.on(new RunEvent.PhaseFinished(WHEN.plusMillis(850), "opening lap", false));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));

        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();
        JsonValue.JsonArray phases = array(written, "phases");
        assertThat(phases.elements()).hasSize(1);
        JsonValue.JsonObject lap = (JsonValue.JsonObject) phases.elements().get(0);
        assertThat(text(lap, "name")).isEqualTo("opening lap");
        assertThat(text(lap, "startedAt")).isEqualTo(WHEN.toString());
        assertThat(text(lap, "finishedAt")).isEqualTo(WHEN.plusMillis(850).toString());
        assertThat(text(lap, "elapsed")).isEqualTo("PT0.85S");
        assertThat(lap.member("cutShort")).contains(JsonValue.of(false));
        assertThat(number(lap, "requests")).isEqualTo(2);
        assertThat(number(lap, "operations")).isEqualTo(2);
        assertThat(number(lap, "operationsAnswering2xx")).isEqualTo(1);
        JsonValue.JsonObject byClass = object(object(lap, "replies"), "byClass");
        assertThat(number(byClass, "2xx")).isEqualTo(1);
        assertThat(number(byClass, "noReply")).isEqualTo(1);
    }

    @Test
    @DisplayName("a run with no stretch of its own writes an empty list, not nothing")
    void a_run_without_stretches_writes_an_empty_list() {
        JsonValue.JsonArray phases = array(run(JsonReport.inMemory(fixedClock())), "phases");

        assertThat(phases.elements()).isEmpty();
    }

    @Test
    @DisplayName("the run over time says when the run began, the time it had, and what came back "
            + "in each slice of it, the slices adding up to the replies of the whole run")
    void the_run_over_time_is_written() {
        Instant start = Instant.parse("2026-10-25T10:00:00Z");
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(start, Duration.ofMinutes(1)));
        for (Interaction attempt : List.of(
                Runs.sentAt(start.plusMillis(1_200), 200),
                Runs.sentAt(start.plusMillis(1_400), 404),
                Runs.sentAt(start.plusSeconds(30), 500),
                Runs.unansweredSentAt(start.plusSeconds(59)))) {
            report.on(new RunEvent.InteractionCompleted(attempt.sentAt(), attempt));
        }
        report.on(new RunEvent.RunFinished(start.plusSeconds(61), Duration.ofSeconds(61),
                Runs.engine()));

        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();
        JsonValue.JsonObject timeline = object(written, "timeline");
        assertThat(text(timeline, "startedAt")).isEqualTo("2026-10-25T10:00:00Z");
        assertThat(text(timeline, "budget")).isEqualTo("PT1M");
        assertThat(text(timeline, "interval")).isEqualTo("PT1S");
        List<JsonValue> slices = array(timeline, "intervals").elements();
        assertThat(slices).hasSize(60);
        assertThat(slices.get(1))
                .isEqualTo(JsonText.read("{\"byClass\": {\"2xx\": 1, \"4xx\": 1}}"));

        Map<String, Integer> added = new TreeMap<>();
        slices.forEach(slice -> {
            JsonValue.JsonObject byClass = object((JsonValue.JsonObject) slice, "byClass");
            byClass.members().keySet().forEach(family ->
                    added.merge(family, number(byClass, family), Integer::sum));
        });
        JsonValue.JsonObject wholeRun = object(object(written, "replies"), "byClass");
        assertThat(added).containsOnlyKeys(wholeRun.members().keySet())
                .allSatisfy((family, count) -> assertThat(count)
                        .isEqualTo(number(wholeRun, family)));
    }

    @Test
    @DisplayName("how many slices the run's time is cut into is a setting")
    void how_finely_the_time_is_cut_is_a_setting() {
        JsonReport report = JsonReport.inMemory(fixedClock(),
                SettingsInEffect.of(Settings.from(Map.of("report.timelineIntervals", "4"))));
        report.on(Runs.started(WHEN, Duration.ofMinutes(1)));
        report.on(new RunEvent.RunFinished(WHEN.plusSeconds(60), Duration.ofSeconds(60),
                Runs.engine()));

        JsonValue.JsonObject timeline = object(
                (JsonValue.JsonObject) report.document().orElseThrow(), "timeline");
        assertThat(text(timeline, "interval")).isEqualTo("PT15S");
        assertThat(array(timeline, "intervals").elements()).hasSize(4);
    }

    @Test
    @DisplayName("every operation the document describes is listed with what happened to it: "
            + "tested, never reached, could not be tested, or left alone by the plan, in the "
            + "document's order with the unreadable ones last")
    void every_operation_is_listed_with_what_happened_to_it() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.DocumentRead(WHEN, List.of(
                new Operation(OperationId.synthesised(HttpMethod.GET, "/pets"), HttpMethod.GET,
                        "/pets", List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        Optional.of("List the pets"), Optional.empty(), false),
                Operation.of(HttpMethod.POST, "/pets"),
                Operation.of(HttpMethod.DELETE, "/pets"),
                Operation.of(HttpMethod.GET, "/owners")), List.of()));
        report.on(new RunEvent.OperationSkipped(WHEN, OperationId.of("POST /pets"),
                "a file upload, which RESTest cannot send yet"));
        report.on(new RunEvent.OperationSkipped(WHEN, OperationId.of("FOO /x"),
                "the document could not be read for it"));
        report.on(new RunEvent.OperationSetAside(WHEN, OperationId.of("DELETE /pets")));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 500)));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        List<JsonValue.JsonObject> operations = array(written, "operations").elements().stream()
                .map(JsonValue.JsonObject.class::cast).toList();
        assertThat(operations).extracting(row -> text(row, "operation"))
                .containsExactly("GET /pets", "POST /pets", "DELETE /pets", "GET /owners",
                        "FOO /x");
        assertThat(operations).extracting(row -> text(row, "state"))
                .containsExactly("tested", "couldNotBeTested", "leftAloneByThePlan", "notReached",
                        "couldNotBeTested");
        JsonValue.JsonObject tested = operations.get(0);
        assertThat(text(tested, "method")).isEqualTo("GET");
        assertThat(text(tested, "path")).isEqualTo("/pets");
        assertThat(text(tested, "summary")).isEqualTo("List the pets");
        assertThat(number(tested, "requests")).isEqualTo(2);
        assertThat(object(object(tested, "replies"), "byClass").members())
                .containsOnlyKeys("2xx", "5xx");
        assertThat(text(operations.get(1), "reason"))
                .isEqualTo("a file upload, which RESTest cannot send yet");
        assertThat(operations.get(3).members()).doesNotContainKey("summary");
        assertThat(operations.get(4).members())
                .describedAs("what could not be read has no method or path to give")
                .doesNotContainKeys("method", "path");
        assertThat(operations.stream().mapToInt(row -> number(row, "requests")).sum())
                .describedAs("the requests of every operation add up to the requests of the run")
                .isEqualTo(number(object(written, "totals"), "requests"));
        Map<String, Integer> classes = new TreeMap<>();
        operations.forEach(row -> classesOf(object(row, "replies"))
                .forEach((statusClass, count) -> classes.merge(statusClass, count, Integer::sum)));
        assertThat(classes).describedAs("and their classes add up to the run's")
                .isEqualTo(classesOf(object(written, "replies")));
    }

    /** How many replies of each class of status code a {@code replies} object counts. */
    private static Map<String, Integer> classesOf(JsonValue.JsonObject replies) {
        Map<String, Integer> classes = new TreeMap<>();
        object(replies, "byClass").members().forEach((statusClass, count) -> classes.put(
                statusClass, ((JsonValue.JsonNumber) count).value().intValueExact()));
        return classes;
    }

    @Test
    @DisplayName("a run whose document was never said to have been read still lists every "
            + "operation it skipped, then every one a request went to in the order of the first "
            + "request, so that the operations add up to the totals")
    void operations_add_up_without_the_document() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.OperationSkipped(WHEN, OperationId.of("FOO /x"),
                "the document could not be read for it"));
        report.on(new RunEvent.InteractionCompleted(WHEN,
                Runs.attempt("GET /zebras", "/zebras", 200)));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /ants", "/ants", 500)));
        report.on(new RunEvent.InteractionCompleted(WHEN,
                Runs.attempt("GET /zebras", "/zebras", 404)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        List<JsonValue.JsonObject> operations = array(written, "operations").elements().stream()
                .map(JsonValue.JsonObject.class::cast).toList();
        assertThat(operations).extracting(row -> text(row, "operation"))
                .containsExactly("FOO /x", "GET /zebras", "GET /ants");
        assertThat(operations).extracting(row -> text(row, "state"))
                .containsExactly("couldNotBeTested", "tested", "tested");
        assertThat(operations).extracting(row -> number(row, "requests")).containsExactly(0, 2, 1);
        assertThat(operations).allSatisfy(row -> assertThat(row.members())
                .doesNotContainKeys("method", "path"));
    }

    @Test
    @DisplayName("every part of the document that could not be used is written, with where it is, "
            + "the operation it belongs to when it belongs to one, and what it cost")
    void every_part_of_the_document_that_could_not_be_used_is_written() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.DocumentRead(WHEN, List.of(), List.of(
                SpecificationIssue.document("info", "attribute info.license.extensions is "
                        + "unexpected"),
                SpecificationIssue.skipped("paths./x.foo", OperationId.of("FOO /x"),
                        "FOO is not a method RESTest can send"))));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        List<JsonValue.JsonObject> issues = array(written, "documentIssues").elements().stream()
                .map(JsonValue.JsonObject.class::cast).toList();
        assertThat(issues).extracting(row -> text(row, "effect"))
                .containsExactly("document", "operationSkipped");
        assertThat(text(issues.get(0), "location")).isEqualTo("info");
        assertThat(issues.get(0).members()).doesNotContainKey("operation");
        assertThat(text(issues.get(1), "operation")).isEqualTo("FOO /x");
        assertThat(text(issues.get(1), "message")).isEqualTo("FOO is not a method RESTest can send");
    }

    @Test
    @DisplayName("how the run was started is written: the plan and the file of settings as they "
            + "were named, whether it was kept, and the keys nobody handed over; and nothing of the "
            + "kind when it was started with none of them")
    void how_the_run_was_started_is_written() {
        JsonValue.JsonObject handed = run(JsonReport.inMemory(fixedClock(),
                SettingsInEffect.of(Settings.defaults()),
                new JsonReport.Invocation(Optional.of("plans/no-deletes.yaml"),
                        Optional.of("restest.yaml"), true,
                        List.of(new JsonReport.KeyNotGiven("api_key",
                                new Place(Place.Where.HEADER, "X-API-Key"), 3)))));
        JsonValue.JsonObject run = object(handed, "run");
        assertThat(text(run, "plan")).isEqualTo("plans/no-deletes.yaml");
        assertThat(text(run, "settingsFile")).isEqualTo("restest.yaml");
        assertThat(run.member("stored")).contains(JsonValue.of(true));
        JsonValue.JsonObject key = (JsonValue.JsonObject) array(handed, "keysNotGiven")
                .elements().getFirst();
        assertThat(text(key, "scheme")).isEqualTo("api_key");
        assertThat(text(key, "in")).isEqualTo("header");
        assertThat(text(key, "name")).isEqualTo("X-API-Key");
        assertThat(number(key, "operations")).isEqualTo(3);

        JsonValue.JsonObject plain = run(JsonReport.inMemory(fixedClock()));
        assertThat(object(plain, "run").member("plan")).contains(JsonValue.NULL);
        assertThat(object(plain, "run").member("settingsFile")).contains(JsonValue.NULL);
        assertThat(object(plain, "run").member("stored")).contains(JsonValue.of(false));
        assertThat(array(plain, "keysNotGiven").elements()).isEmpty();
    }

    @Test
    @DisplayName("the verdict is the word for what the run amounts to: errors found, none, only "
            + "warnings that were told to fail it, nothing answered, nothing sent, or stopped")
    void the_verdict_says_what_the_run_amounts_to() {
        assertThat(verdictOf(JsonReport.inMemory(fixedClock()), true, false))
                .isEqualTo("errorsFound");
        assertThat(verdictOf(JsonReport.inMemory(fixedClock()), false, false))
                .isEqualTo("noErrorsFound");
        assertThat(verdictOf(JsonReport.inMemory(fixedClock(), SettingsInEffect.of(
                Settings.from(Map.of("checks.failOnWarnings", "true")))), false, false))
                .isEqualTo("failedOnWarnings");
        assertThat(verdictOf(JsonReport.inMemory(fixedClock()), true, true))
                .isEqualTo("stopped");

        JsonReport unanswered = JsonReport.inMemory(fixedClock());
        unanswered.on(Runs.started(WHEN));
        unanswered.on(new RunEvent.InteractionCompleted(WHEN, Runs.unansweredSentAt(WHEN)));
        unanswered.on(new RunEvent.FaultFound(WHEN, Runs.neverAnswered("GET /pets")));
        unanswered.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        assertThat(text((JsonValue.JsonObject) unanswered.document().orElseThrow(), "verdict"))
                .describedAs("an API that answered nothing found no errors in itself, whatever was "
                        + "counted against it")
                .isEqualTo("nothingAnswered");

        JsonReport silent = JsonReport.inMemory(fixedClock());
        silent.on(Runs.started(WHEN));
        silent.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10),
                EngineStatistics.none()));
        assertThat(text((JsonValue.JsonObject) silent.document().orElseThrow(), "verdict"))
                .isEqualTo("nothingTested");
    }

    /** The verdict of a run that answered, with an error or only a warning, maybe stopped. */
    private static String verdictOf(JsonReport report, boolean withAnError, boolean stopped) {
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.FaultFound(WHEN, withAnError ? Runs.fellOver() : Runs.wrongShape(1)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine(), stopped));
        return text((JsonValue.JsonObject) report.document().orElseThrow(), "verdict");
    }

    /** A run of one answered request that said, at its end, what it waited. */
    private static JsonValue.JsonObject waitedRun(Waited waited) {
        JsonReport report = JsonReport.inMemory(fixedClock());
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine(), false,
                Optional.empty(), Optional.of(waited)));
        return (JsonValue.JsonObject) report.document().orElseThrow();
    }

    private static JsonValue.JsonObject run(JsonReport report) {
        report.on(Runs.started(WHEN));
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
    @DisplayName("how many write-ups a file holds is a setting: told two, it writes two, and says "
            + "in its own limits block that two is what it was told")
    void how_much_is_written_out_is_a_setting() {
        io.restest.core.settings.Settings told = io.restest.core.settings.Settings.from(
                java.util.Map.of("report.writeUpsPerOperationAndKind", "2"));
        JsonReport report = JsonReport.inMemory(fixedClock(),
                io.restest.core.settings.SettingsInEffect.of(told));
        for (int found = 0; found < 20; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver("GET /pets")));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject written = (JsonValue.JsonObject) report.document().orElseThrow();

        assertThat(array(written, "findings").elements())
                .describedAs("at the usual setting this would be five")
                .hasSize(2);
        assertThat(number(object(written, "limits"), "writeUpsPerOperationAndKind")).isEqualTo(2);
        assertThat(number(object(written, "totals"), "faults"))
                .describedAs("and every one of them is still counted")
                .isEqualTo(20);
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
                .hasSize(ReportSettings.defaults().writeUpsPerOperationAndKind());
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
                        + text((JsonValue.JsonObject) row, "id") + " "
                        + number((JsonValue.JsonObject) row, "count")))
                .describedAs("the counting is exact and complete even where the writing is not: "
                        + "this list is as long as the API, not as long as the run")
                .containsExactlyInAnyOrder("GET /pets HTTP_STATUS_500 300",
                        "GET /shelters SCHEMA_INVALID_RESPONSE 7");
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
                .isEqualTo(ReportSettings.defaults().writeUpsPerOperationAndKind());
        assertThat(number(limits, "writeUpsInTotal")).isEqualTo(ReportSettings.defaults().writeUpsInTotal());
        assertThat(number(limits, "mostBodyBytesKept"))
                .describedAs("a reader who finds a reply cut short should be able to tell it was us "
                        + "who cut it, and at what length, without knowing which version wrote this")
                .isEqualTo((int) ReportSettings.defaults().mostBodyBytesKept());
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
                .isLessThanOrEqualTo((int) ReportSettings.defaults().mostBodyBytesKept());
        assertThat(number(body, "wireLength"))
                .describedAs("and it says how much really arrived, so our trimming is never "
                        + "mistaken for the API having sent less than it did")
                .isEqualTo(sent);
        assertThat(report.asText().orElseThrow().length())
                .describedAs("one enormous reply no longer decides the size of the file")
                .isLessThan(sent);
    }

    @Test
    @DisplayName("the status codes answered most are listed commonest first, as many as the "
            + "setting allows")
    void the_commonest_status_codes_are_listed_as_far_as_the_setting_allows() {
        JsonReport report = JsonReport.inMemory(fixedClock(), SettingsInEffect.of(
                Settings.from(Map.of("report.commonestStatusCodesListed", "2"))));
        for (int status : new int[] {404, 200, 404, 500, 404, 200}) {
            report.on(new RunEvent.InteractionCompleted(WHEN,
                    Runs.attempt("GET /pets", "/pets", status)));
        }
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject replies =
                object((JsonValue.JsonObject) report.document().orElseThrow(), "replies");

        List<JsonValue> commonest = array(replies, "commonest").elements();
        assertThat(commonest).hasSize(2);
        assertThat(number((JsonValue.JsonObject) commonest.get(0), "status")).isEqualTo(404);
        assertThat(number((JsonValue.JsonObject) commonest.get(0), "count")).isEqualTo(3);
        assertThat(number((JsonValue.JsonObject) commonest.get(1), "status")).isEqualTo(200);
        assertThat(number(object(replies, "byClass"), "5xx"))
                .describedAs("a status left off the list is still counted by its class")
                .isEqualTo(1);
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
                .map(row -> "F" + number(row, "code") + " " + text(row, "id") + " "
                        + text(row, "statusClass") + " = " + number(row, "count")))
                .describedAs("a developer who only watches their logs for 500s is not seeing the "
                        + "fault that came back as a perfectly ordinary 200, and this is the table "
                        + "that shows it. An attempt that got no reply has no code, so it has a "
                        + "name of its own rather than being lost")
                .containsExactlyInAnyOrder("F100 HTTP_STATUS_500 5xx = 3",
                        "F200 SCHEMA_INVALID_RESPONSE 2xx = 1", "F100 HTTP_STATUS_500 noReply = 1");
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

    @Test
    @DisplayName("a request that got no reply in time says, where it is written out, what RESTest "
            + "was still waiting for")
    void a_request_with_no_reply_in_time_says_what_was_awaited() {
        JsonValue.JsonObject written = afterFinding(1, Runs::satOn);

        JsonValue.JsonObject finding =
                (JsonValue.JsonObject) array(written, "findings").elements().get(0);
        JsonValue.JsonObject outcome = object(object(finding, "interaction"), "outcome");
        assertThat(finding.member("status")).isEmpty();
        assertThat(text(finding, "statusClass")).isEqualTo("noReply");
        assertThat(text(outcome, "kind")).isEqualTo("failed");
        assertThat(text(outcome, "timedOutWaitingFor")).isEqualTo("reply");
    }

    @Test
    @DisplayName("every row counting faults says whether its kind is an error or a warning, and what "
            + "RESTest calls it; what to look at is said once, for each kind")
    void each_fault_row_says_its_severity_and_title() {
        JsonValue.JsonObject written = run(JsonReport.inMemory(fixedClock()));

        for (String table : List.of("faultsByCategory", "faultsByStatus", "faultsByOperation")) {
            assertThat(array(written, table).elements().stream()
                    .map(row -> (JsonValue.JsonObject) row)
                    .map(row -> number(row, "code") + " " + text(row, "severity") + " "
                            + text(row, "title")))
                    .describedAs("in %s", table)
                    .containsExactlyInAnyOrder("100 error Internal server error",
                            "200 warning Reply off schema");
        }
        JsonValue.JsonObject byCategory =
                (JsonValue.JsonObject) array(written, "faultsByCategory").elements().get(0);
        assertThat(text(byCategory, "advice")).startsWith("look in the API's log");
        assertThat(array(written, "faultsByOperation").elements())
                .allSatisfy(row -> assertThat(((JsonValue.JsonObject) row).member("advice"))
                        .describedAs("the advice is the kind's, said once, in faultsByCategory")
                        .isEmpty());
        JsonValue.JsonObject category = object(
                (JsonValue.JsonObject) array(written, "findings").elements().get(0), "category");
        assertThat(text(category, "severity")).isEqualTo("error");
        assertThat(text(category, "title")).isEqualTo("Internal server error");
        assertThat(category.member("advice"))
                .describedAs("not repeated under each of up to a thousand faults written out")
                .isEmpty();
    }

    @Test
    @DisplayName("the totals count errors and warnings apart, and an operation with both under both")
    void the_totals_count_errors_and_warnings_apart() {
        JsonReport report = JsonReport.inMemory(fixedClock());
        Interaction accepted = Runs.attempt("addPet", "/pets", 202);
        report.on(new RunEvent.InteractionCompleted(WHEN, accepted));
        report.on(new RunEvent.FaultFound(WHEN, Runs.acceptedBroken(accepted)));
        report.on(new RunEvent.FaultFound(WHEN, Runs.undeclaredStatus(accepted)));
        report.on(new RunEvent.FaultFound(WHEN, Runs.fellOver("getPet")));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
        JsonValue.JsonObject totals =
                object((JsonValue.JsonObject) report.document().orElseThrow(), "totals");

        assertThat(number(totals, "faults")).isEqualTo(3);
        assertThat(number(totals, "errors")).isEqualTo(2);
        assertThat(number(totals, "warnings")).isEqualTo(1);
        assertThat(number(totals, "operationsWithFaults")).isEqualTo(2);
        assertThat(number(totals, "operationsWithErrors")).isEqualTo(2);
        assertThat(number(totals, "operationsWithWarnings")).isEqualTo(1);
        assertThat(totals.members().keySet()).containsSubsequence("operationsWithFaults",
                "errors", "warnings", "operationsWithErrors", "operationsWithWarnings",
                "faultsWrittenInFull");
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
