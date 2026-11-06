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
package io.restest.arch;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.json.JsonValue.JsonArray;
import io.restest.core.json.JsonValue.JsonObject;
import io.restest.core.json.JsonValue.JsonString;
import io.restest.core.settings.SettingSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps the run that the manual and the website show as the run they say it is.
 *
 * <p>The manual shows what a report looks like with pictures of one real run: the practice clinic,
 * tested for a minute with nothing but its document, by a released RESTest. The run's own report
 * file is kept beside the pictures, for a reader to try questions on. The website's first page
 * shows the same pictures, and takes its figures and the faults it shows from that report file, as
 * the run RESTest makes "with nothing to configure". A released version never changes: had it kept
 * another run, the website would show that one as such for as long as the version is the latest.
 *
 * <p>So the kept run is checked here, on every change:
 *
 * <ul>
 *   <li>its report was written by a version numbered as a release, like {@code 2.4.0}, not by a
 *       snapshot build on its way to one, and the manual's chapter on reading a run names it;
 *   <li>the run was not cut short, and was given the minute a run takes when no time is given;
 *   <li>no plan, no file of settings and no list of values were handed over, and every setting the
 *       report lists was left as RESTest ships it;
 *   <li>the six pictures are there, as PNG files: the top of the page, an operation opened at a
 *       fault, and what was found, each in a light and a dark version.
 * </ul>
 *
 * <p>The run and its pictures are made again when a release changes the page or the kinds of fault,
 * as the release steps say. The chapter shows the pictures with HTML, whose links
 * {@link DocumentedLinksTest} does not follow: that they are there is checked here, and the website's
 * own build checks the links.
 */
class DocumentedRunTest {

    /** Where the run is kept, from the root of the repository. */
    private static final String RUN = "docs/manual/images/clinic-run";

    /** The chapter that shows the run, and names the version that made it. */
    private static final String CHAPTER = "docs/manual/04-reading-what-a-run-says.md";

    /** The pictures of its page: each view, light and dark. */
    private static final List<String> PICTURES = List.of(
            "summary-light.png", "summary-dark.png",
            "fault-light.png", "fault-dark.png",
            "found-light.png", "found-dark.png");

    private static final Pattern RELEASED = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    /** How every PNG file begins. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    /**
     * The time a run is given when none is, as {@code report.json} writes it. Should the default of
     * {@code --budget} change, the run is made again with it.
     */
    private static final String DEFAULT_BUDGET = "PT1M";

    /** The parts of a kept run's report that are checked, as they should be. */
    private static final String REPORT = """
            {
              "tool": {"name": "RESTest", "version": "2.4.0"},
              "run": {"plan": null, "settingsFile": null},
              "cutShort": false,
              "settings": [{"key": "engine.readTimeout", "value": "30s", "source": "default"}],
              "dictionaries": {"read": [{"name": "fuzzing", "from": "built in"}], "refused": []},
              "timeline": {"budget": "PT1M"}
            }
            """;

    @Test
    @DisplayName("The run the manual and the website show is a whole default run of a version numbered as a release, with its six pictures")
    void theKeptRunIsTheOneItIsSaidToBe() throws IOException {
        Path root = RepositoryRoot.locate();
        assertThat(problems(root, Files.readString(root.resolve(CHAPTER))))
                .as("what is wrong with the run kept in %s", RUN)
                .isEmpty();
    }

    @Test
    @DisplayName("A report kept as it should be has nothing wrong with it")
    void aWellKeptReportPasses() {
        assertThat(problems(JsonText.read(REPORT))).isEmpty();
    }

    @Test
    @DisplayName("Each way a report can be of the wrong run is told apart, naming what is wrong")
    void eachWrongReportIsReported() {
        Map<String, String> wrong = Map.of(
                "made by 2.5.0-SNAPSHOT", REPORT.replace("\"2.4.0\"", "\"2.5.0-SNAPSHOT\""),
                "cut short", REPORT.replace("\"cutShort\": false", "\"cutShort\": true"),
                "given PT30S", REPORT.replace("\"PT1M\"", "\"PT30S\""),
                "a plan was handed over", REPORT.replace("\"plan\": null", "\"plan\": \"plan.yaml\""),
                "a file of settings was handed over", REPORT.replace("\"settingsFile\": null", "\"settingsFile\": \"s.yaml\""),
                "engine.readTimeout is not as RESTest ships it", REPORT.replace("\"default\"", "\"command line\""),
                "2 lists of values were read", REPORT.replace("\"read\": [", "\"read\": [{\"name\": \"mine\", \"from\": \"mine.json\"}, "),
                "1 refused", REPORT.replace("\"refused\": []", "\"refused\": [{\"from\": \"bad.json\", \"reason\": \"no\"}]"),
                "lists no settings", REPORT.replace("[{\"key\": \"engine.readTimeout\", \"value\": \"30s\", \"source\": \"default\"}]", "[]"));
        wrong.forEach((what, report) -> assertThat(problems(JsonText.read(report)))
                .as(what)
                .anySatisfy(p -> assertThat(p).contains(what)));
    }

    @Test
    @DisplayName("A missing picture, a report that is missing or not JSON, or a chapter not naming the version, is reported")
    void missingFilesAreReported(@TempDir Path root) throws IOException {
        Path folder = Files.createDirectories(root.resolve(RUN));
        Files.writeString(folder.resolve("report.json"), REPORT);
        for (String picture : PICTURES) {
            Files.write(folder.resolve(picture), PNG);
        }
        assertThat(problems(root, "tested by RESTest 2.4.0")).isEmpty();
        assertThat(problems(root, "tested by RESTest 2.3.0"))
                .containsExactly(CHAPTER + " does not name RESTest 2.4.0, the version that made the run it pictures");

        Files.write(folder.resolve("found-light.png"), new byte[] {'G', 'I', 'F', '8'});
        Files.delete(folder.resolve("fault-dark.png"));
        assertThat(problems(root, "RESTest 2.4.0")).containsExactly(
                RUN + "/fault-dark.png is missing", RUN + "/found-light.png is not a PNG picture");

        Files.writeString(folder.resolve("report.json"), "{not json");
        assertThat(problems(root, "RESTest 2.4.0")).anySatisfy(p -> assertThat(p).startsWith(RUN + "/report.json is not JSON"));

        Files.delete(folder.resolve("report.json"));
        assertThat(problems(root, "RESTest 2.4.0")).contains(RUN + "/report.json is missing");
    }

    /** What is wrong with the run kept under {@code root}, shown by a chapter that reads {@code chapter}. */
    private static List<String> problems(Path root, String chapter) throws IOException {
        Path folder = root.resolve(RUN);
        List<String> problems = new ArrayList<>();
        for (String picture : PICTURES) {
            Path file = folder.resolve(picture);
            if (!Files.isRegularFile(file)) {
                problems.add(RUN + "/" + picture + " is missing");
            } else if (!startsWith(Files.readAllBytes(file), PNG)) {
                problems.add(RUN + "/" + picture + " is not a PNG picture");
            }
        }
        Path file = folder.resolve("report.json");
        if (!Files.isRegularFile(file)) {
            problems.add(RUN + "/report.json is missing");
            return problems;
        }
        JsonValue report;
        try {
            report = JsonText.read(Files.readString(file));
        } catch (JsonException e) {
            problems.add(RUN + "/report.json is not JSON: " + e.getMessage());
            return problems;
        }
        problems.addAll(problems(report));
        text(report, "tool", "version")
                .filter(version -> !chapter.contains("RESTest " + version))
                .ifPresent(version -> problems.add(
                        CHAPTER + " does not name RESTest " + version + ", the version that made the run it pictures"));
        return problems;
    }

    /** What is wrong with a report, for the run the manual and the website say it is. */
    private static List<String> problems(JsonValue report) {
        List<String> problems = new ArrayList<>();
        String version = text(report, "tool", "version").orElse("no version");
        if (!RELEASED.matcher(version).matches()) {
            problems.add("the run was made by " + version + ", which is not numbered as a release");
        }
        if (!at(report, "cutShort").equals(Optional.of(JsonValue.FALSE))) {
            problems.add("the run was cut short, or does not say whether it was");
        }
        String budget = text(report, "timeline", "budget").orElse("no time");
        if (!budget.equals(DEFAULT_BUDGET)) {
            problems.add("the run was given " + budget + ", not the minute a run takes when no time is given");
        }
        if (!at(report, "run", "plan").equals(Optional.of(JsonValue.NULL))) {
            problems.add("a plan was handed over to the run");
        }
        if (!at(report, "run", "settingsFile").equals(Optional.of(JsonValue.NULL))) {
            problems.add("a file of settings was handed over to the run");
        }
        List<JsonValue> settings = elements(report, "settings");
        if (settings.isEmpty()) {
            problems.add("the report lists no settings, so nothing says they were left as RESTest ships them");
        }
        for (JsonValue setting : settings) {
            String source = text(setting, "source").orElse("nowhere");
            if (!source.equals(SettingSource.DEFAULT.written())) {
                problems.add(text(setting, "key").orElse("a setting") + " is not as RESTest ships it: it came from "
                        + source);
            }
        }
        int read = elements(report, "dictionaries", "read").size();
        int refused = elements(report, "dictionaries", "refused").size();
        if (read != 1 || refused != 0) {
            problems.add(read + " lists of values were read and " + refused
                    + " refused, where a run given none reads only the one RESTest carries");
        }
        return problems;
    }

    private static boolean startsWith(byte[] bytes, byte[] start) {
        return bytes.length >= start.length && Arrays.equals(bytes, 0, start.length, start, 0, start.length);
    }

    private static Optional<JsonValue> at(JsonValue value, String... path) {
        Optional<JsonValue> here = Optional.of(value);
        for (String name : path) {
            here = here.flatMap(v -> v instanceof JsonObject o ? o.member(name) : Optional.empty());
        }
        return here;
    }

    private static Optional<String> text(JsonValue value, String... path) {
        return at(value, path).flatMap(v -> v instanceof JsonString(String s) ? Optional.of(s) : Optional.empty());
    }

    private static List<JsonValue> elements(JsonValue value, String... path) {
        return at(value, path).map(v -> v instanceof JsonArray(List<JsonValue> all) ? all : List.<JsonValue>of())
                .orElse(List.of());
    }
}
