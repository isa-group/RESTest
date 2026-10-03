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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.restest.cli.Restest;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.oracle.WfcFault;
import io.restest.core.settings.SettingKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps the two pages that explain what a run leaves behind in step with what a run really writes.
 *
 * <p>When RESTest has finished testing an API it writes a file, {@code report.json}, for a build
 * server or a script to read, and it names every fault it found by a number from a catalogue other
 * testing tools share. One page lists every key of that file and says what it holds; another lists
 * the kinds of fault a run can report and what each one means. Somebody reading a report looks a key
 * or a fault up on those pages rather than in the code, so a key the page does not explain is one
 * nobody can use, and a kind of fault missing from the other page is one nobody can interpret.
 *
 * <p>So the pages are checked against the tool rather than trusted. A small API is started inside
 * this test - one that falls over on some requests and answers others in a shape its own document
 * does not allow - and RESTest is run against it for a few seconds through the command line
 * ({@link Restest}). Every key of the report that run writes has to be explained on the page, and
 * the keys at the top of the file have to come in the order the page gives them. The kinds of fault
 * the page lists have to be exactly the kinds the rules RESTest judges replies by can report, with
 * the names the shared catalogue gives them ({@link WfcFault}). And every setting the two pages
 * name has to be one the tool has ({@link SettingKey}).
 */
class DocumentedReportTest {

    /** Where the documents this test holds to the tool are, from the root of the repository. */
    private static final String REPORT_PAGE = "docs/report.md";

    private static final String FAULTS_PAGE = "docs/faults.md";

    /** The heading the page lists the top of the file under; its table is the file's first level. */
    private static final String THE_TOP_OF_THE_FILE = "`report.json`";

    /** The heading the faults page lists the kinds this version reports under. */
    private static final String THE_KINDS_REPORTED = "The kinds this version reports";

    /**
     * Places in the report whose members are data rather than keys: counts by the class of status
     * code, where any class an API answers with becomes a member, and the values a request carried,
     * which are whatever the API was sent.
     */
    private static final Set<String> HOLDING_DATA = Set.of(
            "replies.byClass",
            "phases[].replies.byClass",
            "findings[].interaction.testCase.parameters[].value",
            "findings[].interaction.testCase.body.value");

    /**
     * Keys the small API below is built to make a run write, each in a part of the file a plainer
     * run leaves out. If a change to RESTest stops a run against it writing one of them, the page is
     * no longer checked for that part, and the test says so rather than passing on less.
     */
    private static final List<String> THE_RUN_IS_MEANT_TO_WRITE = List.of(
            "skippedOperations[].reason",
            "dictionaries.read[].from",
            "dictionaries.refused[].reason",
            "phases[].replies",
            "findings[].interaction.testCase.mutation.operator",
            "findings[].interaction.testCase.sequence.shape",
            "findings[].interaction.testCase.body.origin",
            "findings[].interaction.outcome.body.text");

    /** One row of a table: the first cell, when it is a single name in backticks. */
    private static final Pattern ROW = Pattern.compile("^\\| `([^`]+)` \\|");

    /** A row of the faults page's table of kinds: the code, then the catalogue's name. */
    private static final Pattern KIND_ROW = Pattern.compile("^\\| `F(\\d+)` \\| ([^|]+?) \\|");

    /** A name in backticks in a heading: the place in the file the table under it describes. */
    private static final Pattern IN_BACKTICKS = Pattern.compile("`([^`]+)`");

    /** What begins like the name of a setting and is not one: a file the pages mention. */
    private static final Set<String> FILE_NAMES = Set.of("report.json");

    /**
     * An API described by a short document: a list that answers in a shape its document does not
     * allow, a creation that falls over on anything but the body it asks for, a read of one thing
     * that always falls over, a deletion, and an upload that insists on a body RESTest cannot write.
     */
    private static final String THE_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Things
              version: "1"
            paths:
              /things:
                get:
                  operationId: listThings
                  parameters:
                    - name: limit
                      in: query
                      example: 3
                      schema: {type: integer, minimum: 1, maximum: 10}
                  responses:
                    "200":
                      description: the things
                      content:
                        application/json:
                          schema:
                            type: array
                            items: {$ref: "#/components/schemas/Thing"}
                post:
                  operationId: createThing
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema: {$ref: "#/components/schemas/NewThing"}
                  responses:
                    "201":
                      description: created
                      content:
                        application/json:
                          schema: {$ref: "#/components/schemas/Thing"}
              /things/{thingId}:
                parameters:
                  - name: thingId
                    in: path
                    required: true
                    schema: {type: integer}
                get:
                  operationId: getThing
                  responses:
                    "200":
                      description: the thing
                      content:
                        application/json:
                          schema: {$ref: "#/components/schemas/Thing"}
                delete:
                  operationId: deleteThing
                  responses:
                    "204": {description: deleted}
              /files:
                post:
                  operationId: uploadFile
                  requestBody:
                    required: true
                    content:
                      multipart/form-data:
                        schema:
                          type: object
                          properties:
                            file: {type: string, format: binary}
                  responses:
                    "201": {description: uploaded}
            components:
              schemas:
                NewThing:
                  type: object
                  required: [name]
                  properties:
                    name: {type: string, maxLength: 20}
                Thing:
                  type: object
                  required: [id, name]
                  properties:
                    id: {type: integer}
                    name: {type: string}
            """;

    /**
     * The plan RESTest carries with its shares moved towards series and changes to accepted requests,
     * which are what the parts of a written-out fault only some requests have come from. Under the
     * shipped shares a series is a tenth of the run, which a slow machine may not reach in time.
     */
    private static final String A_PLAN_FOR_EVERY_PART = """
            version: 1
            strategies:
              - name: nominal
                share: 20
                sources:
                  - source: enum
                  - weighted:
                      - source: example
                        weight: 15
                      - source: random
                        weight: 20
                      - source: observed
                        weight: 20
                      - dictionaries: given
                        weight: 40
                      - source: default
                        weight: 5
              - name: sequences
                share: 40
                sends: sequences
                sources:
                  - source: enum
                  - source: random
              - name: mutation
                share: 30
                mutates: accepted
                sources:
                  - source: enum
                  - source: random
              - name: fuzzing
                share: 10
                sources:
                  - dictionary: fuzzing
                  - source: random
            """;

    /** A list of values in a version of the format nobody has written yet, which a run refuses. */
    private static final String A_LIST_NOBODY_CAN_READ = """
            version: 99
            name: unreadable
            keyedBy: name
            values: {}
            """;

    private static String reportPage;
    private static String faultsPage;

    @BeforeAll
    static void readThePages() {
        // Line endings taken out on the way in: a checkout on Windows ends every line of a document
        // differently, and what is compared here is what the pages say.
        reportPage = page(REPORT_PAGE);
        faultsPage = page(FAULTS_PAGE);
    }

    @Test
    @DisplayName("every key a run writes into report.json is explained on the page, and the keys "
            + "at the top come in the page's order")
    void every_key_of_the_report_is_on_the_page(@TempDir Path directory) throws IOException {
        JsonValue report = aRealReport(directory);

        Map<String, List<String>> explained = keysOnThePage();
        Set<String> written = new TreeSet<>();
        keysIn(report, "", written);

        assertThat(written)
                .describedAs("the run against the small API in this test no longer writes some of "
                        + "the parts of the report it is there to make a run write, so the page is "
                        + "not checked for them")
                .containsAll(THE_RUN_IS_MEANT_TO_WRITE);
        assertThat(written).allSatisfy(key -> assertThat(explained.getOrDefault(parentOf(key),
                        List.of()))
                .describedAs("report.json has %s, and %s explains no %s under a heading naming "
                        + "`%s`", key, REPORT_PAGE, nameOf(key), shownAs(parentOf(key)))
                .contains(nameOf(key)));
        assertThat(((JsonValue.JsonObject) report).members().keySet())
                .describedAs("the keys at the top of report.json, against the table under the "
                        + "heading %s", THE_TOP_OF_THE_FILE)
                .containsExactlyElementsOf(explained.get(""));
    }

    @Test
    @DisplayName("the faults page lists exactly the kinds of fault RESTest's rules can report, by "
            + "the catalogue's names")
    void the_faults_page_lists_what_the_rules_report() {
        JavaClasses rules = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.restest.oracles");
        Set<String> kinds = Arrays.stream(WfcFault.values()).map(Enum::name)
                .collect(Collectors.toSet());
        Map<Integer, String> reported = new TreeMap<>();
        rules.forEach(rule -> rule.getFieldAccessesFromSelf().stream()
                .filter(access -> access.getTargetOwner().isEquivalentTo(WfcFault.class))
                .map(access -> access.getTarget().getName())
                .filter(kinds::contains)
                .map(WfcFault::valueOf)
                .forEach(kind -> reported.put(kind.code(), kind.descriptiveName())));

        assertThat(reported)
                .describedAs("RESTest's rules report no kind of fault at all, which means this "
                        + "test is no longer reading them")
                .isNotEmpty();
        assertThat(kindsOnThePage())
                .describedAs("the table under '%s' in %s, against the kinds the rules in "
                        + "io.restest.oracles report", THE_KINDS_REPORTED, FAULTS_PAGE)
                .containsExactlyEntriesOf(reported);
    }

    @Test
    @DisplayName("both pages give the version of the fault catalogue the report says it used")
    void both_pages_give_the_catalogues_version() {
        assertThat(faultsPage)
                .describedAs("%s should say which version of the catalogue the codes come from",
                        FAULTS_PAGE)
                .contains("**version " + WfcFault.CATALOGUE_VERSION + "**")
                .contains(WfcFault.CATALOGUE_NAME);
        assertThat(reportPage)
                .describedAs("%s should give the version faultCatalogue holds", REPORT_PAGE)
                .contains("`" + WfcFault.CATALOGUE_VERSION + "`")
                .contains("`" + WfcFault.CATALOGUE_NAME + "`");
    }

    @Test
    @DisplayName("every setting the two pages name is one the tool has")
    void every_setting_named_is_real() {
        Pattern named = Pattern.compile(
                "`((?:" + String.join("|", SettingKey.groups()) + ")\\.[A-Za-z]+)`");
        for (String page : List.of(REPORT_PAGE, FAULTS_PAGE)) {
            Matcher setting = named.matcher(page(page));
            while (setting.find()) {
                String name = setting.group(1);
                if (FILE_NAMES.contains(name)) {
                    continue;
                }
                assertThat(SettingKey.named(name))
                        .describedAs("%s names %s, which is not a setting", page, name)
                        .isPresent();
            }
        }
    }

    // --- the run ------------------------------------------------------------------------------

    /**
     * Runs RESTest for a few seconds against the small API and reads back the report it wrote.
     *
     * <p>Every fault is written out whole, rather than the first few of each kind, so that the parts
     * of a written-out fault only some requests have - a change made to an accepted request, a step
     * of a series - are in the file whichever requests happened to fail first.
     */
    private static JsonValue aRealReport(Path directory) throws IOException {
        Path document = directory.resolve("things.yaml");
        Files.writeString(document, THE_DOCUMENT);
        Path unreadable = directory.resolve("unreadable.yaml");
        Files.writeString(unreadable, A_LIST_NOBODY_CAN_READ);
        Path plan = directory.resolve("plan.yaml");
        Files.writeString(plan, A_PLAN_FOR_EVERY_PART);
        Path out = directory.resolve("out");

        ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
        HttpServer api = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        api.setExecutor(threads);
        AtomicInteger made = new AtomicInteger();
        api.createContext("/", exchange -> answer(exchange, made));
        api.start();
        try {
            StringWriter screen = new StringWriter();
            StringWriter problems = new StringWriter();
            int answer = Restest.run(new String[] {
                "run", document.toString(),
                "--url", "http://127.0.0.1:" + api.getAddress().getPort(),
                "--budget", "3s",
                "--seed", "20261003",
                "--out", out.toString(),
                "--dictionary", unreadable.toString(),
                "--campaign", plan.toString(),
                "--set", "engine.maxConcurrency=2",
                "--set", "report.writeUpsPerOperationAndKind=100000",
                "--set", "report.writeUpsInTotal=100000"},
                    new PrintWriter(screen, true), new PrintWriter(problems, true));

            assertThat(answer)
                    .describedAs("a run against an API that answers 500 should find a fault; it "
                            + "printed:%n%s%nand said:%n%s", screen, problems)
                    .isEqualTo(1);
        } finally {
            api.stop(0);
            threads.shutdownNow();
        }
        return JsonText.read(Files.readString(out.resolve("report.json")));
    }

    /** What the small API answers. */
    private static void answer(HttpExchange exchange, AtomicInteger made) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            if (path.equals("/things") && method.equals("GET")) {
                // An identifier written as text and a required name left out: not the shape its
                // document gives a thing.
                reply(exchange, 200, "application/json", "[{\"id\": \"one\"}]");
            } else if (path.equals("/things") && method.equals("POST")) {
                if (aNamedThing(body)) {
                    reply(exchange, 201, "application/json",
                            "{\"id\": " + made.incrementAndGet() + ", \"name\": \"made\"}");
                } else {
                    reply(exchange, 500, "text/plain", "could not read the thing");
                }
            } else if (path.startsWith("/things/") && method.equals("GET")) {
                reply(exchange, 500, "text/plain", "it fell over");
            } else if (path.startsWith("/things/") && method.equals("DELETE")) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                reply(exchange, 404, "text/plain", "nothing here");
            }
        }
    }

    /** Whether a body is the one the creation asks for: an object whose name is a word. */
    private static boolean aNamedThing(byte[] body) {
        try {
            return JsonText.read(new String(body, StandardCharsets.UTF_8))
                    instanceof JsonValue.JsonObject thing
                    && thing.member("name").filter(JsonValue.JsonString.class::isInstance)
                            .isPresent();
        } catch (RuntimeException notJson) {
            return false;
        }
    }

    private static void reply(HttpExchange exchange, int status, String mediaType, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", mediaType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // --- the report and the page --------------------------------------------------------------

    /**
     * Every key in a piece of the report, written as the way down to it: {@code totals.faults},
     * {@code findings[].interaction.request.method}. A list's items are all under the same name,
     * since the page explains an item once for all of them.
     */
    private static void keysIn(JsonValue value, String at, Set<String> keys) {
        if (HOLDING_DATA.contains(at)) {
            return;
        }
        switch (value) {
            case JsonValue.JsonObject object -> object.members().forEach((name, member) -> {
                String key = at.isEmpty() ? name : at + "." + name;
                keys.add(key);
                keysIn(member, key, keys);
            });
            case JsonValue.JsonArray array ->
                    array.elements().forEach(item -> keysIn(item, at + "[]", keys));
            default -> {
                // A number, a word, true or false, or null: nothing further down.
            }
        }
    }

    /**
     * The keys the page explains, by the place in the file they belong to: the names in the first
     * column of each table between the heading for the top of the file and the next section, filed
     * under every place the heading above the table names in backticks. The top of the file is
     * filed under the empty name.
     */
    private static Map<String, List<String>> keysOnThePage() {
        Map<String, List<String>> explained = new HashMap<>();
        List<String> places = List.of();
        boolean inTheFile = false;
        for (String line : reportPage.split("\n")) {
            if (line.startsWith("## ")) {
                inTheFile = line.equals("## " + THE_TOP_OF_THE_FILE);
                places = inTheFile ? List.of("") : List.of();
            } else if (inTheFile && line.startsWith("#")) {
                List<String> named = new ArrayList<>();
                Matcher place = IN_BACKTICKS.matcher(line);
                while (place.find()) {
                    named.add(place.group(1));
                }
                places = named;
            } else if (inTheFile) {
                Matcher row = ROW.matcher(line);
                if (row.find()) {
                    for (String place : places) {
                        explained.computeIfAbsent(place, nothing -> new ArrayList<>())
                                .add(row.group(1));
                    }
                }
            }
        }
        assertThat(explained)
                .describedAs("%s should have a section headed %s with a table of the keys at the "
                        + "top of the file", REPORT_PAGE, THE_TOP_OF_THE_FILE)
                .containsKey("");
        return explained;
    }

    /** The kinds of fault the faults page says this version reports, by code. */
    private static Map<Integer, String> kindsOnThePage() {
        Map<Integer, String> kinds = new TreeMap<>();
        boolean inTheTable = false;
        for (String line : faultsPage.split("\n")) {
            if (line.startsWith("#")) {
                inTheTable = line.equals("## " + THE_KINDS_REPORTED);
            } else if (inTheTable) {
                Matcher row = KIND_ROW.matcher(line);
                if (row.find()) {
                    kinds.put(Integer.parseInt(row.group(1)), row.group(2).trim());
                }
            }
        }
        return kinds;
    }

    private static String parentOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? "" : key.substring(0, dot);
    }

    private static String nameOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    private static String shownAs(String place) {
        return place.isEmpty() ? "report.json" : place;
    }

    private static String page(String fromTheRoot) {
        return RepositoryRoot.read(RepositoryRoot.locate().resolve(fromTheRoot))
                .replace("\r\n", "\n");
    }
}
