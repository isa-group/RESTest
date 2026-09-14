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

import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.json.InteractionDocument;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Writes what a run found as one JSON file, for anything that reads a run rather than looks at it.
 *
 * <p>The report on the screen is for a person; this one is for a build server deciding whether to
 * fail, a spreadsheet counting faults across versions, a script opening the ones that are new. It
 * says the same things, with nothing left out and nothing shortened.
 *
 * <p>Each fault carries its whole attempt with it - the request as it was sent, the reply as it
 * came back, and where every value in the request came from - written in the same shape a stored
 * run uses, so a fault in a report and the same fault in a stored run read identically. The {@code
 * curl} command is included too, so nothing has to reassemble one.
 *
 * <p>Fault kinds are named by number from a catalogue several testing tools share, and the report
 * says which version of that catalogue it used, because a fault code only means something next to
 * the list it was taken from.
 */
public final class JsonReport implements RunListener {

    /**
     * How many faults are written out in full before the file stops growing.
     *
     * <p>A run keeps testing for as long as it was given, and an API that is broken is broken every
     * time it is asked, so a minute against a badly behaved API produces faults by the hundred
     * thousand. Each one carries the whole attempt with it - the request, and as much of the reply
     * as was kept - so holding all of them until the run ends is how a report turns into a file
     * nobody can open, or into a run that stops for want of memory before it has written anything
     * at all.
     *
     * <p>The count is never capped, only what is written out in full: the totals and the tally by
     * kind are of every fault found. How many were written is stated in the file, so nothing is
     * quietly missing.
     */
    static final int FINDINGS_WRITTEN = 1_000;

    private final Optional<Path> file;
    private final Clock clock;
    private final List<Finding> findings = new ArrayList<>();
    private final Map<FaultCategory, Integer> counts = new LinkedHashMap<>();
    private final Set<OperationId> operations = new LinkedHashSet<>();
    private String api = "";
    private String baseUrl = "";
    private int attempts;
    private int faults;
    private Duration elapsed = Duration.ZERO;
    private EngineStatistics engine = EngineStatistics.none();
    private JsonValue written;

    private JsonReport(Optional<Path> file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    /** A report written to this file when the run finishes. */
    public static JsonReport to(Path file) {
        return new JsonReport(Optional.of(Objects.requireNonNull(file, "file")), Clock.systemUTC());
    }

    /** A report that is built but written nowhere, for anyone who only wants to read it back. */
    public static JsonReport inMemory() {
        return new JsonReport(Optional.empty(), Clock.systemUTC());
    }

    /** The same, with the clock the report's timestamp comes from, so a test can fix it. */
    public static JsonReport inMemory(Clock clock) {
        return new JsonReport(Optional.empty(), Objects.requireNonNull(clock, "clock"));
    }

    /** What was written, once the run has finished. Empty before that. */
    public Optional<JsonValue> document() {
        return Optional.ofNullable(written);
    }

    /** What was written, as the text that went into the file. Empty before the run has finished. */
    public Optional<String> asText() {
        return document().map(JsonText::write);
    }

    @Override
    public void on(RunEvent event) {
        switch (event) {
            case RunEvent.RunStarted started -> {
                api = started.api();
                baseUrl = started.baseUrl();
            }
            case RunEvent.InteractionCompleted completed -> {
                attempts++;
                operations.add(completed.interaction().testCase().operation());
            }
            case RunEvent.FaultFound found -> {
                faults++;
                if (findings.size() < FINDINGS_WRITTEN) {
                    findings.add(found.finding());
                }
                counts.merge(found.finding().category(), 1, Integer::sum);
            }
            case RunEvent.RunFinished finished -> {
                elapsed = finished.elapsed();
                engine = finished.engine();
                finish();
            }
            case RunEvent.TestCasePlanned ignored -> {
                // What was planned and then not sent leaves no evidence to report on.
            }
        }
    }

    private void finish() {
        written = build();
        file.ifPresent(path -> {
            try {
                Files.writeString(path, JsonText.write(written));
            } catch (IOException e) {
                throw new UncheckedIOException("the report could not be written to " + path, e);
            }
        });
    }

    private JsonValue build() {
        Map<String, JsonValue> report = new LinkedHashMap<>();
        report.put("tool", JsonValue.object(Map.of("name", JsonValue.of("RESTest"),
                "version", JsonValue.of(toolVersion()))));
        report.put("faultCatalogue", JsonValue.object(Map.of(
                "name", JsonValue.of(WfcFault.CATALOGUE_NAME),
                "version", JsonValue.of(WfcFault.CATALOGUE_VERSION))));
        report.put("createdAt", JsonValue.of(clock.instant().toString()));
        report.put("api", JsonValue.object(Map.of("title", JsonValue.of(api),
                "baseUrl", JsonValue.of(baseUrl))));
        report.put("totals", totals());
        report.put("engine", engineStatistics());
        report.put("faultsByCategory", JsonValue.array(counts.entrySet().stream()
                .map(entry -> {
                    Map<String, JsonValue> counted = new LinkedHashMap<>(
                            ((JsonValue.JsonObject) categoryOf(entry.getKey())).members());
                    counted.put("count", JsonValue.of(entry.getValue()));
                    return (JsonValue) JsonValue.object(counted);
                })
                .toList()));
        report.put("findings", JsonValue.array(findings.stream().map(this::findingOf).toList()));
        return JsonValue.object(report);
    }

    private JsonValue totals() {
        Map<String, JsonValue> totals = new LinkedHashMap<>();
        totals.put("requests", JsonValue.of(attempts));
        totals.put("operations", JsonValue.of(operations.size()));
        totals.put("faults", JsonValue.of(faults));
        totals.put("faultsWrittenInFull", JsonValue.of(findings.size()));
        totals.put("elapsed", JsonValue.of(elapsed.toString()));
        return JsonValue.object(totals);
    }

    private JsonValue engineStatistics() {
        Map<String, JsonValue> statistics = new LinkedHashMap<>();
        statistics.put("requestsSent", JsonValue.of(engine.requestsSent()));
        statistics.put("wallClock", JsonValue.of(engine.wallClock().toString()));
        statistics.put("idle", JsonValue.of(engine.idle().toString()));
        statistics.put("idleFraction", JsonValue.of(BigDecimal.valueOf(
                Math.round(engine.idleFraction() * 1000) / 1000.0)));
        statistics.put("meanResponseTime", JsonValue.of(engine.meanResponseTime().toString()));
        statistics.put("peakConcurrency", JsonValue.of(engine.peakConcurrency()));
        statistics.put("concurrencyLimit", JsonValue.of(engine.concurrencyLimit()));
        return JsonValue.object(statistics);
    }

    private JsonValue findingOf(Finding finding) {
        Map<String, JsonValue> entry = new LinkedHashMap<>();
        entry.put("category", categoryOf(finding.category()));
        finding.context().ifPresent(context -> entry.put("context", JsonValue.of(context)));
        entry.put("operation", JsonValue.of(finding.operation().value()));
        entry.put("summary", JsonValue.of(finding.summary()));
        entry.put("details", JsonValue.array(
                finding.details().stream().map(JsonValue::of).map(JsonValue.class::cast).toList()));
        entry.put("curl", JsonValue.of(CurlCommand.of(finding.interaction().request())));
        entry.put("interaction", InteractionDocument.of(finding.interaction()));
        return JsonValue.object(entry);
    }

    private static JsonValue categoryOf(FaultCategory category) {
        Map<String, JsonValue> written = new LinkedHashMap<>();
        written.put("code", JsonValue.of(category.code()));
        written.put("descriptiveName", JsonValue.of(category.descriptiveName()));
        written.put("testCaseLabel", JsonValue.of(category.testCaseLabel()));
        written.put("label", JsonValue.of(category.label()));
        return JsonValue.object(written);
    }

    /**
     * Which RESTest wrote this, taken from the packaged build. A copy built from source and run
     * straight out of its build directory carries no such stamp, and says so rather than inventing
     * one.
     */
    private static String toolVersion() {
        String version = JsonReport.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }
}
