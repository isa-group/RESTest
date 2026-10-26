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

import io.restest.core.event.RunEvent;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsInEffect;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportTest {

    private static final Instant WHEN = Instant.parse("2026-10-25T10:30:00Z");

    /** Where the page keeps the run, and the one place it is read from. */
    private static final Pattern THE_RUN = Pattern.compile(
            "<script type=\"application/json\" id=\"report\">(.*?)</script>", Pattern.DOTALL);

    /** A reply written to break out of the element the run sits in, every way there is. */
    private static final String HOSTILE = "</script><script>alert(1)</script> <!--<script> "
            + "</SCRIPT > line\u2028paragraph\u2029 $1 \\ \\u003c <";

    @Test
    @DisplayName("the page is written beside the JSON report when the run finishes, and nothing "
            + "is left of the file it was written into first")
    void the_page_is_written(@TempDir Path directory) throws IOException {
        Path json = directory.resolve("report.json");
        Path page = directory.resolve("report.html");

        run(HtmlReport.around(JsonReport.to(json), page));

        assertThat(json).exists();
        assertThat(page).exists();
        assertThat(WholeFile.whileBeingWritten(page)).doesNotExist();
        assertThat(Files.readString(page)).startsWith("<!doctype html>");
    }

    @Test
    @DisplayName("the run in the page, read back, is the run in report.json, value for value")
    void the_page_holds_the_same_run(@TempDir Path directory) throws IOException {
        Path json = directory.resolve("report.json");
        Path page = directory.resolve("report.html");

        run(HtmlReport.around(JsonReport.to(json), page));

        assertThat(JsonText.read(theRunIn(Files.readString(page))))
                .isEqualTo(JsonText.read(Files.readString(json)));
    }

    @Test
    @DisplayName("a reply that tries to end the element the run sits in leaves exactly one such "
            + "element, and the run in it reads back intact")
    void a_hostile_reply_cannot_break_out(@TempDir Path directory) throws IOException {
        Path json = directory.resolve("report.json");
        Path page = directory.resolve("report.html");
        HtmlReport report = HtmlReport.around(JsonReport.to(json), page);
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.FaultFound(WHEN, Runs.fellOverSaying("GET /pets", HOSTILE)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(1), Runs.engine()));

        String written = Files.readString(page);
        String run = theRunIn(written);
        assertThat(Pattern.compile("<script", Pattern.CASE_INSENSITIVE).matcher(written).results()
                .count())
                .describedAs("the run's element and the page's own script, and no other")
                .isEqualTo(2);
        assertThat(run).doesNotContain("<", "\u2028", "\u2029");
        assertThat(JsonText.read(run)).isEqualTo(JsonText.read(Files.readString(json)));
        assertThat(Files.readString(json))
                .describedAs("the reply is in the run, so the page is tested against it")
                .contains("alert(1)");
    }

    @Test
    @DisplayName("what changes in the run's text is only what has to, written so that JSON reads "
            + "it back the same")
    void only_what_has_to_change_changes() throws IOException {
        StringWriter out = new StringWriter();

        HtmlReport.writeForAScript("a<b \u2028 \u2029 $1 \\ \\u003c </script>", out);

        assertThat(out.toString())
                .isEqualTo("a\\u003cb \\u2028 \\u2029 $1 \\ \\u003c \\u003c/script>");
    }

    @Test
    @DisplayName("the page tells the browser to fetch nothing, run nothing it did not bring, and "
            + "load no picture but its own")
    void the_page_fetches_nothing() {
        String page = HtmlReport.template();

        assertThat(page).contains("<meta http-equiv=\"Content-Security-Policy\" content=\""
                + "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
                + "img-src data:\">");
        assertThat(page)
                .describedAs("no address beyond the name of the drawing language its icons use")
                .doesNotContainPattern("https?://(?!www\\.w3\\.org/2000/svg')")
                .doesNotContain("fetch(", "XMLHttpRequest", "WebSocket", "sendBeacon",
                        "EventSource", "@import", "url(", "<link", "<iframe", "<object", "<embed");
        assertThat(Pattern.compile("\\b(?:src|href)=\"([^\"]*)\"").matcher(page).results()
                .map(found -> found.group(1)).toList())
                .describedAs("whatever the page points to is inside it")
                .allSatisfy(target -> assertThat(target).matches("(?s)(data:|#).*"));
    }

    @Test
    @DisplayName("the page says what each part of the document it could not use cost an operation "
            + "that was still tested")
    void the_page_says_what_every_issue_cost() {
        String page = HtmlReport.template();

        Stream.of(SpecificationIssue.Effect.values())
                .filter(effect -> effect != SpecificationIssue.Effect.OPERATION_SKIPPED
                        && effect != SpecificationIssue.Effect.DOCUMENT)
                .forEach(effect -> assertThat(page)
                        .describedAs("the words the page gives '%s'", effect.word())
                        .containsPattern("\\b" + effect.word() + ": ' \\("));
    }

    @Test
    @DisplayName("the page puts what came from the API on the screen only as text, never as markup "
            + "or as code")
    void the_page_writes_text_as_text() {
        String page = HtmlReport.template();

        assertThat(page).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML",
                "document.write", "eval(", "new Function", "setTimeout('", "setTimeout(\"",
                "javascript:");
        assertThat(page).contains("<noscript>")
                .describedAs("a page that cannot draw the run says where the run is")
                .contains("report.json, beside this file");
    }

    @Test
    @DisplayName("a page that cannot be written is not left half-written, and says where it was "
            + "going")
    void a_page_is_whole_or_absent(@TempDir Path directory) throws IOException {
        Path taken = directory.resolve("report.html");
        Files.createDirectories(taken);
        Files.writeString(taken.resolve("something"), "not ours");

        assertThatThrownBy(() -> run(HtmlReport.around(
                JsonReport.to(directory.resolve("report.json")), taken)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("the page could not be written to " + taken);
        assertThat(WholeFile.whileBeingWritten(taken)).doesNotExist();
    }

    @Test
    @DisplayName("the JSON report hears every event through the page, once")
    void the_json_report_hears_everything_once() {
        JsonReport json = JsonReport.inMemory(Clock.fixed(WHEN, ZoneOffset.UTC),
                SettingsInEffect.of(Settings.defaults()));
        HtmlReport page = HtmlReport.around(json, Path.of("never-written.html"));
        page.on(Runs.started(WHEN));
        page.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));

        JsonReport alone = JsonReport.inMemory(Clock.fixed(WHEN, ZoneOffset.UTC));
        alone.on(Runs.started(WHEN));
        alone.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 200)));
        RunEvent.RunFinished finished = new RunEvent.RunFinished(WHEN, Duration.ofSeconds(1),
                Runs.engine());
        json.on(finished);
        alone.on(finished);

        assertThat(json.asText()).isEqualTo(alone.asText());
    }

    private static void run(HtmlReport report) {
        report.on(Runs.started(WHEN));
        report.on(new RunEvent.InteractionCompleted(WHEN, Runs.attempt("GET /pets", "/pets", 500)));
        report.on(new RunEvent.FaultFound(WHEN, Runs.fellOver()));
        report.on(new RunEvent.FaultFound(WHEN, Runs.wrongShape(2)));
        report.on(new RunEvent.RunFinished(WHEN, Duration.ofSeconds(10), Runs.engine()));
    }

    private static String theRunIn(String page) {
        List<String> found = THE_RUN.matcher(page).results().map(match -> match.group(1)).toList();
        assertThat(found).describedAs("the page keeps the run in one place").hasSize(1);
        return found.getFirst();
    }
}
