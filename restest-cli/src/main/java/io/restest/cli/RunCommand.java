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
package io.restest.cli;

import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.exec.EngineSettings;
import io.restest.core.exec.HttpEngine;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.spec.SpecificationParser;
import io.restest.core.store.InteractionStore;
import io.restest.exec.OkHttpEngine;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.oracles.OracleListener;
import io.restest.report.ConsoleReport;
import io.restest.report.JsonReport;
import io.restest.spec.SwaggerSpecificationParser;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Tests an API from one command, and says what is wrong with it.
 *
 * <p>This is the whole tool seen from outside. Given an API's description and an address, it reads
 * the description, invents requests from it, sends them for as long as it was given, judges every
 * reply against what the description promised, and prints each disagreement together with a command
 * anybody can paste into a terminal to see it happen again. What it found is also left behind as two
 * files: one describing the faults, for a program to read, and one holding every request and reply,
 * so the run can be examined later without asking the API anything.
 *
 * <p>It holds no cleverness of its own. Reading, inventing, sending, judging and reporting each
 * belong to a different part of the tool; what is decided here is the order they happen in, how long
 * they are given, and what the answer means to whoever ran the command.
 */
@Command(
        name = "run",
        description = "Tests an API described by an OpenAPI document and reports what is wrong.",
        mixinStandardHelpOptions = true,
        versionProvider = ToolVersion.class,
        sortOptions = false)
final class RunCommand implements Callable<Integer> {

    /** How many unreadable parts of a document to name before saying how many are left. */
    private static final int ISSUES_SHOWN = 5;

    @Parameters(
            index = "0",
            paramLabel = "<specification>",
            description = "The OpenAPI document describing the API: a file, a web address, or "
                    + "something on the class path.")
    private String specification;

    @Option(
            names = "--url",
            paramLabel = "<base>",
            description = "Where the API is running, for instance http://localhost:8080/api/v3. "
                    + "Taken from the document when it names a usable address and this is omitted.")
    private String baseUrl;

    @Option(
            names = "--budget",
            paramLabel = "<duration>",
            defaultValue = "60s",
            converter = BudgetDuration.class,
            description = "How long to keep testing: 30s, 5m, 2h, or a plain number of seconds. "
                    + "All of it is used. Default: ${DEFAULT-VALUE}.")
    private Duration budget;

    @Option(
            names = "--seed",
            paramLabel = "<number>",
            description = "Fixes the random choices, so the same command makes the same requests. "
                    + "One is chosen, and printed, when this is omitted.")
    private Long seed;

    @Option(
            names = "--out",
            paramLabel = "<directory>",
            defaultValue = "restest-out",
            description = "Where to write report.json and run.sqlite. An earlier run in the same "
                    + "directory is replaced. Default: ${DEFAULT-VALUE}.")
    private Path outputDirectory;

    @Spec
    private CommandSpec spec;

    /** What the run is built from. Replaced only by tests that need to control them. */
    private SpecificationParser parser = new SwaggerSpecificationParser();
    private EngineSettings engineSettings = EngineSettings.defaults();

    @Override
    public Integer call() throws IOException {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        Instant startedAt = Instant.now();
        // The engine is built before a single line of the document has been read, and that ordering
        // is the point rather than an accident. The engine starts its own clock when it is built,
        // and everything this run reports about how much of its time nothing was happening is
        // measured against that clock. Reading a large document takes real time, during which the
        // API is being asked nothing - and a tool that spends its time preparing instead of testing
        // is exactly what that measurement exists to catch. Start the clock afterwards, and such a
        // run reports itself as flawless.
        try (HttpEngine engine = new OkHttpEngine(engineSettings)) {
            ApiModel model = parser.parse(specification);
            RandomTestCaseGenerator generator = seed == null
                    ? new RandomTestCaseGenerator(model)
                    : new RandomTestCaseGenerator(model, seed);
            List<Operation> testable = generator.testableOperations();
            if (testable.isEmpty()) {
                return nothingToTest(err, model, generator);
            }
            String address;
            try {
                address = BaseAddress.resolve(baseUrl, model);
            } catch (IllegalArgumentException nowhereToSendThem) {
                err.println("restest: " + nowhereToSendThem.getMessage());
                return ExitCode.NOTHING_TO_TEST;
            }
            return testing(model, generator, testable, address, startedAt, engine, out, err);
        }
    }

    private int testing(ApiModel model, RandomTestCaseGenerator generator, List<Operation> testable,
            String address, Instant startedAt, HttpEngine engine, PrintWriter out, PrintWriter err)
            throws IOException {
        Path directory = prepared(outputDirectory);
        Path reportFile = directory.resolve("report.json");
        Path runFile = directory.resolve("run.sqlite");
        ConsoleReport console = ConsoleReport.to(out);

        RunLoop.Outcome outcome = null;
        EventStream events = new EventStream();
        try (InteractionStore store = SqliteInteractionStore.at(runFile)) {
            // Closed first, and separately, so that everything announced has reached the reports and
            // the stored run before either of them is shut - and so that what the stream says about
            // itself afterwards is a finished run's answer rather than a mid-flight one.
            try (EventStream drained = events) {
                drained.subscribe(event -> {
                    if (event instanceof RunEvent.InteractionCompleted completed) {
                        store.record(completed.interaction());
                    }
                });
                drained.subscribe(OracleListener.standard(model, drained));
                drained.subscribe(console);
                drained.subscribe(JsonReport.to(reportFile));

                drained.publish(new RunEvent.RunStarted(startedAt, model.title(), address));
                describe(out, model, generator, testable.size());

                try {
                    outcome = RunLoop.run(testable, generator, address, startedAt.plus(budget),
                            RunLoop.WORK_AHEAD_FACTOR * engineSettings.maxConcurrency(),
                            engine, drained);
                } finally {
                    // Even if the loop fails, what already happened is worth reporting. Without
                    // this, the run's summary and its report file would both be missing, and the
                    // evidence of whatever went wrong would go with them.
                    Instant finishedAt = Instant.now();
                    drained.publish(new RunEvent.RunFinished(finishedAt,
                            Duration.between(startedAt, finishedAt), engine.statistics()));
                }
            }
        }

        skipped(out, outcome);
        out.println("report written to " + reportFile);
        out.println("run stored in " + runFile);

        long reportsThatFailed = events.listenerFailures();
        long eventsNeverHeard = events.undelivered();
        if (reportsThatFailed > 0 || eventsNeverHeard > 0) {
            err.println("restest: " + reportsThatFailed + " report(s) failed and " + eventsNeverHeard
                    + " event(s) never arrived, so what is printed above may be incomplete");
            return ExitCode.TOOL_FAILED;
        }
        // Two ways to end a run with no evidence about the API at all. Neither may answer "nothing
        // wrong here", because that is the one thing such a run cannot know.
        if (outcome != null && outcome.sent() == 0) {
            err.println("restest: the budget of " + human(budget) + " ran out before a single "
                    + "request could be sent - reading the document and starting up takes a moment, "
                    + "and it is paid out of the budget. Give the run more time.");
            return ExitCode.NOTHING_TO_TEST;
        }
        if (outcome != null && outcome.nothingAnswered()) {
            err.println("restest: nothing at " + address + " answered any of the "
                    + outcome.sent() + " requests, so the API was never actually tested. Check the "
                    + "address, and that it is running.");
            return ExitCode.NOTHING_TO_TEST;
        }
        return console.faults() > 0 ? ExitCode.FAULTS_FOUND : ExitCode.NO_FAULTS;
    }

    /**
     * Explains a document nothing can be done with.
     *
     * <p>Having nothing to test is not the same as having found nothing wrong, and the two must not
     * produce the same answer: an empty report that exits successfully reads as "this API is fine".
     */
    private int nothingToTest(PrintWriter err, ApiModel model, RandomTestCaseGenerator generator) {
        var refused = generator.untestableOperations();
        err.println(refused.isEmpty()
                ? "restest: the document describes no operation that could be tested"
                : "restest: none of the " + refused.size() + " operations in the document can be "
                        + "tested; the first says: " + refused.values().iterator().next());
        report(err, model.issues());
        return ExitCode.NOTHING_TO_TEST;
    }

    private void describe(PrintWriter out, ApiModel model, RandomTestCaseGenerator generator,
            int testable) {
        out.println(testable + " of " + (testable + generator.untestableOperations().size())
                + " operations can be tested, seed " + generator.seed()
                + ", budget " + human(budget));
        report(out, model.issues());
        out.println();
    }

    /** Names what could not be read, because skipping something silently is the same as losing it. */
    private static void report(PrintWriter where, List<SpecificationIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }
        where.println(issues.size() + " part(s) of the document could not be read:");
        issues.stream().limit(ISSUES_SHOWN).forEach(issue -> where.println("  " + issue));
        if (issues.size() > ISSUES_SHOWN) {
            where.println("  ... and " + (issues.size() - ISSUES_SHOWN) + " more");
        }
    }

    private static void skipped(PrintWriter out, RunLoop.Outcome outcome) {
        if (outcome == null) {
            return;
        }
        long lost = outcome.notGenerated() + outcome.notAssembled();
        if (lost > 0) {
            out.println(lost + " test case(s) could not be built and were skipped");
        }
    }

    /** Makes the output directory, and clears out whatever an earlier run left in it. */
    private static Path prepared(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve("run.sqlite"));
        Files.deleteIfExists(directory.resolve("report.json"));
        return directory;
    }

    /** The budget the way it was asked for, rather than the way a machine writes it. */
    private static String human(Duration budget) {
        return budget.toMillis() % 1000 == 0 ? budget.toSeconds() + "s" : budget.toMillis() + "ms";
    }

    /** Only so a test can hand the command a parser it controls. */
    void useParser(SpecificationParser replacement) {
        this.parser = replacement;
    }

    /** Only so a test can shrink the engine to something predictable. */
    void useEngineSettings(EngineSettings replacement) {
        this.engineSettings = replacement;
    }
}
