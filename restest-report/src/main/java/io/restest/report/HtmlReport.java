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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The run's report as a page: one file that anybody can open in a browser - from what a build kept,
 * from an e-mail, on a laptop with no network - to see in a minute how the run went: the verdict,
 * the errors and warnings in all and on each operation, the request behind each kind of fault, and
 * what could not be tested.
 *
 * <p>The page shows the run {@link JsonReport} writes, and nothing that report does not hold: the
 * whole of that file goes into the page as it was written, and the page draws itself from it in the
 * browser. So this report goes around the JSON report. It hands it every event and, once the run is
 * over, writes the page around the text the JSON report has just written, without writing the run
 * out a second time. The page needs nothing outside itself, and tells the browser to let it fetch
 * nothing at all.
 */
public final class HtmlReport implements RunListener {

    /** Where the run goes, written once in the page RESTest carries. */
    private static final String WHERE_THE_RUN_GOES = "REPORT_JSON_GOES_HERE";

    private static final char LINE_SEPARATOR = 0x2028;
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    /** The page RESTest carries, cut in two where the run goes. */
    private record Page(String before, String after) {
    }

    private final JsonReport json;
    private final Path file;
    private final Page page;

    private HtmlReport(JsonReport json, Path file, Page page) {
        this.json = json;
        this.file = file;
        this.page = page;
    }

    /**
     * A report that writes the page to this file once the run is over, around a JSON report that
     * hears every event through it.
     *
     * @param json the report whose file the page shows; it is not to be subscribed on its own as
     *     well, or it hears every event twice
     * @param file where the page goes
     * @return the report
     */
    public static HtmlReport around(JsonReport json, Path file) {
        return new HtmlReport(Objects.requireNonNull(json, "json"),
                Objects.requireNonNull(file, "file"), carried());
    }

    @Override
    public void on(RunEvent event) {
        json.on(event);
        if (event instanceof RunEvent.RunFinished) {
            String run = json.asText().orElseThrow(() -> new IllegalStateException(
                    "the JSON report heard the run finish and wrote nothing"));
            WholeFile.write(file, "the page", out -> {
                out.write(page.before());
                writeForAScript(run, out);
                out.write(page.after());
            });
        }
    }

    /**
     * Writes the run's text so that it can sit inside an element of the page and be read back
     * exactly as it was.
     *
     * <p>The browser ends that element at the first {@code </script}, and a {@code <!--} inside it
     * changes how that is looked for. Both can be in the run, since a reply quoted in it may hold
     * anything. JSON lets any character inside quotes be written as a backslash, the letter u and
     * the character's four hexadecimal digits, and a {@code <} can only be inside quotes, so every
     * one is written that way; so are the line and paragraph separators, which some programs that
     * read JavaScript take for the end of a line. Nothing else changes, and the text is written as
     * it is read, never held twice.
     */
    static void writeForAScript(String text, Writer out) throws IOException {
        int from = 0;
        for (int at = 0; at < text.length(); at++) {
            String instead = switch (text.charAt(at)) {
                case '<' -> "\\u003c";
                case LINE_SEPARATOR -> "\\u2028";
                case PARAGRAPH_SEPARATOR -> "\\u2029";
                default -> null;
            };
            if (instead != null) {
                out.write(text, from, at - from);
                out.write(instead);
                from = at + 1;
            }
        }
        out.write(text, from, text.length() - from);
    }

    /** The page RESTest carries, cut where the run goes. */
    private static Page carried() {
        String text = template();
        int at = text.indexOf(WHERE_THE_RUN_GOES);
        if (at < 0 || text.indexOf(WHERE_THE_RUN_GOES, at + 1) >= 0) {
            throw new IllegalStateException("the page RESTest writes has to say once, and only "
                    + "once, where the run goes");
        }
        return new Page(text.substring(0, at), text.substring(at + WHERE_THE_RUN_GOES.length()));
    }

    /** The page RESTest carries, as it is read from beside this class, before any run is put in. */
    static String template() {
        try (InputStream in = HtmlReport.class.getResourceAsStream("report.html")) {
            if (in == null) {
                throw new IllegalStateException("this RESTest was built without the page it writes");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the page RESTest writes could not be read", e);
        }
    }
}
