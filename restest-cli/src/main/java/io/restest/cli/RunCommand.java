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

import io.restest.core.auth.AuthGiven;
import io.restest.core.auth.CredentialPlan;
import io.restest.core.auth.CredentialedEngine;
import io.restest.core.auth.Place;
import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.exec.EngineSettings;
import io.restest.core.exec.HttpEngine;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.SecurityScheme;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsException;
import io.restest.core.settings.SettingsInEffect;
import io.restest.core.store.InteractionStore;
import io.restest.gen.Campaign;
import io.restest.gen.Campaigns;
import java.util.Optional;
import io.restest.gen.Dictionaries;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.gen.Scheduler;
import io.restest.oracles.OracleListener;
import io.restest.report.ConsoleReport;
import io.restest.report.JsonReport;
import io.restest.spec.SwaggerSpecificationParser;
import io.restest.store.SqliteInteractionStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
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
 * anybody can paste into a terminal to see it happen again. What it found is left behind as a file
 * describing the faults, for a program to read. Asked to, it also keeps every request and reply, so
 * the run can be examined afterwards without asking the API anything - but only when asked, because
 * a minute against a fast API is hundreds of megabytes that almost nothing reads back. Stopped
 * before its time is up - with Ctrl-C, say - it makes no new requests and still leaves all of that
 * behind, said to be what a run cut short found.
 *
 * <p>It holds no cleverness of its own. Reading, inventing, sending, judging and reporting each
 * belong to a different part of the tool; what is decided here is the order they happen in, how long
 * they are given, and what the answer means to whoever ran the command.
 */
@Command(
        name = "run",
        description = {
                "Tests an API described by an OpenAPI document and reports what is wrong.",
                "",
                "The requests are real, and so is what they do: an operation that creates, changes "
                        + "or deletes something is tested by creating, changing or deleting "
                        + "something. An API left under test for a whole budget has data in it "
                        + "afterwards. Point this at a deployment you are willing to have written "
                        + "to."},
        mixinStandardHelpOptions = true,
        versionProvider = ToolVersion.class,
        sortOptions = false,
        exitCodeListHeading = "%nExit codes - the number the command ends with:%n",
        exitCodeList = {
                ExitCode.NO_FAULTS + ":The run finished and found nothing wrong.",
                ExitCode.FAULTS_FOUND + ":The run finished and found at least one fault in the API.",
                ExitCode.BAD_COMMAND_LINE + ":The command line was wrong, or a plan, a settings file "
                        + "or a key it named could not be used. Nothing was sent.",
                ExitCode.NOTHING_TO_TEST + ":Nothing was tested: the document describes no "
                        + "operation that can be tried, there is no address to send requests to or "
                        + "nowhere to write the results, or not one request was answered - none "
                        + "could be built, the budget ran out before the first, or nothing at the "
                        + "address replied. The run says which.",
                ExitCode.TOOL_FAILED + ":RESTest itself went wrong - it lost requests on their "
                        + "way, had to keep exchanges without their details, or broke outright - "
                        + "so what it printed may be incomplete. The message and the stack trace "
                        + "are what to report.",
                ExitCode.INTERRUPTED + ":The run was stopped with Ctrl-C. What it found until then "
                        + "was printed and written, and report.json says it was cut short - or "
                        + "the run says what it did not leave behind.",
                ExitCode.TERMINATED + ":The run was stopped with kill or docker stop. What it "
                        + "found until then was printed and written, and report.json says it was "
                        + "cut short - or the run says what it did not leave behind."},
        // Written out line by line, each short enough never to be broken by the framework, which
        // breaks a long line after a colon or a full stop - in the middle of an address, or of the
        // name of a setting, where a person copying it would copy half.
        footerHeading = "%nEnvironment:%n",
        footer = {
                "  RESTEST_AUTH            What one --auth holds, so that a key need not be typed",
                "                          where the shell's history keeps it. One typed with",
                "                          --auth for the same place wins over it.",
                "  RESTEST_<GROUP>_<KEY>   One setting: RESTEST_ENGINE_MAX_CONCURRENCY=1 is",
                "                          engine.maxConcurrency=1. --set wins over it, and it",
                "                          wins over --settings.",
                "",
                "Examples:",
                "  restest run openapi.yaml --url http://localhost:8080 --budget 5m",
                "  restest run openapi.yaml --auth special-key",
                "  restest run openapi.yaml --auth 'header:Authorization=Bearer <token>'",
                "  restest run openapi.yaml --set engine.maxConcurrency=1",
                "  restest run --print-settings > settings.yaml"})
final class RunCommand implements Callable<Integer> {

    /** How many unreadable parts of a document to name before saying how many are left. */
    private static final int ISSUES_SHOWN = 5;

    /**
     * Everything RESTest writes into an output directory.
     *
     * <p>A directory holds one run, so every run begins by removing what an earlier one left there -
     * the kept run included, and including when this run will not write one. A directory holding one
     * run's database beside another run's report is worse than an empty one, because it reads as a
     * single run whose two halves disagree.
     *
     * <p>Only these names are removed, never the directory's other contents: the directory is
     * whatever somebody typed after {@code --out}, and may be full of work that is not ours.
     *
     * <p>Whoever adds a new kind of report has to name its file here too. Left out, it would survive
     * into the next run and be read as part of it. So is the file the report is written into before
     * it is given its name, which only a program ended outright while writing it leaves behind.
     */
    private static final List<String> FILES_A_RUN_WRITES = List.of("run.sqlite", "run.sqlite-wal",
            "run.sqlite-shm", "report.json",
            JsonReport.whileBeingWritten(Path.of("report.json")).toString());

    /**
     * How long a run stopped from outside is given to write what it found, once it has stopped
     * waiting for its answers, before the program is let end without it.
     *
     * <p>Writing takes a fraction of a second: the reports catching up with the last answers, the
     * report file, the last interactions into the stored run. This is not a pause anybody waits
     * through. It is how long to wait for writing that has got stuck - a report that stopped making
     * progress, a disk that stopped answering - before saying so, because the program will be ended
     * outright soon after whoever stopped it runs out of patience: ten seconds after
     * {@code docker stop}, with nothing said. With the two seconds a stopped run waits for its
     * answers by default, and the moment {@link #LOOKING_ALLOWED} takes, this leaves two in hand.
     */
    private static final Duration WRITING_ALLOWED = Duration.ofSeconds(5);

    /**
     * How long a run stopped from outside that ran out of time to write gives its directory to say
     * what is in it, before saying that it did not answer. A directory answers at once, unless it
     * is the reason the writing ran out of time.
     */
    private static final Duration LOOKING_ALLOWED = Duration.ofSeconds(1);

    @Parameters(
            index = "0",
            // Not required, so that --print-campaign can answer a question about the tool without
            // being handed a document it is not going to read. Asked for below instead, in the
            // words picocli would have used.
            arity = "0..1",
            paramLabel = "<specification>",
            description = "The OpenAPI document describing the API: a file, a web address, or "
                    + "something on the class path.")
    private String specification;

    @Option(
            names = "--url",
            paramLabel = "<base>",
            description = "Which machine the API is running on, as a web address. Taken from the "
                    + "document when it names a usable address and this is omitted. Given without "
                    + "a path, the directory the document says the API is served from is kept; "
                    + "given with a path of its own, that path is used instead.")
    private String baseUrl;

    @Option(
            names = "--auth",
            paramLabel = "<key>",
            description = "A key the API asks for, sent where its document says - in a header, the "
                    + "query or a cookie - and only with the operations that ask for one. Where the "
                    + "document declares more than one kind of key, name the one this is for, as "
                    + "api_key=<key>. Where it declares none, or for a token or a session cookie "
                    + "you already hold, say where it goes, and it goes with every request: "
                    + "header:<name>=<key>, query:<name>=<key> or cookie:<name>=<key>. A bearer "
                    + "token goes as header:Authorization=Bearer <token>, in quotes for the space. "
                    + "Repeat for several. RESTEST_AUTH may hold one instead. What the run writes "
                    + "shows REDACTED-AUTH where one went, never the key itself.")
    private List<String> keysTyped = new ArrayList<>();

    @Option(
            names = "--budget",
            paramLabel = "<duration>",
            defaultValue = "60s",
            converter = BudgetDuration.class,
            description = "How long to keep testing: 500ms, 30s, 5m, 2h, or a plain number of "
                    + "seconds. "
                    + "All of it is used, reading the document included. Default: ${DEFAULT-VALUE}.")
    private Duration budget;

    @Option(
            names = "--seed",
            paramLabel = "<number>",
            description = "Fixes the random choices, so the same command makes the same requests. "
                    + "One is chosen, and printed, when this is omitted. A plan drawing on what "
                    + "the API has already returned is the exception: what it sends depends on "
                    + "what came back, so the same number makes a similar run rather than the "
                    + "same one. --store keeps every request and reply of it.")
    private Long seed;

    @Option(
            names = "--out",
            paramLabel = "<directory>",
            defaultValue = "restest-out",
            description = "Where to write this run's files: report.json, and run.sqlite with "
                    + "--store. What an earlier run wrote there is replaced, and nothing else in "
                    + "the directory is touched. Default: ${DEFAULT-VALUE}.")
    private Path outputDirectory;

    @Option(
            names = "--dictionary",
            paramLabel = "<file-or-directory>",
            description = "A file of values to send, in YAML, or a directory of them. Repeat for "
                    + "several. Values good enough to be worth keeping belong next to the "
                    + "specification they were worked out for. RESTest always uses its own list of "
                    + "values to push with on top of whatever is given here.")
    private List<Path> dictionaries = new ArrayList<>();

    @Option(
            names = "--fuzzing",
            paramLabel = "<percentage>",
            defaultValue = "" + RandomTestCaseGenerator.AWKWARD_SHARE,
            converter = FuzzingShare.class,
            description = "How much of the time to spend pushing at the API with values nobody "
                    + "sensible would send - empty text, enormous numbers, the wrong kind of value "
                    + "entirely. A healthy API takes them or turns them away; a fragile one falls "
                    + "over. 0 sends none of them. Not with --campaign, whose plan says the same "
                    + "thing. Default: ${DEFAULT-VALUE}.")
    private int fuzzingShare;

    @Option(
            names = "--campaign",
            paramLabel = "<file>",
            description = "A plan, in YAML, saying where this run's values should come from and "
                    + "which operations it may touch. Without one, RESTest follows the plan it "
                    + "carries; --print-campaign writes that out as a starting point.")
    private Path campaignFile;

    @Option(
            names = "--print-campaign",
            description = "Write out the plan RESTest follows when it is given none, and stop. "
                    + "Save it, change a line, and hand it back with --campaign.")
    private boolean printTheCampaign;

    @Option(
            names = "--settings",
            paramLabel = "<file>",
            description = "A file of settings, in YAML, saying how the tool itself should behave - "
                    + "how hard it pushes, how much it keeps, how deep it goes. "
                    + "--print-settings writes out the ones in force as a starting point.")
    private Path settingsFile;

    @Option(
            names = "--set",
            paramLabel = "<group.key=value>",
            description = "One setting, named the way --print-settings names it. Setting "
                    + "engine.maxConcurrency to 1 is the answer to an API too fragile to be asked "
                    + "two things at once. Repeat for several. Wins over --settings and over the "
                    + "environment.")
    private List<String> settingsTyped = new ArrayList<>();

    @Option(
            names = "--print-settings",
            description = "Write out the settings this command would use, each with where its "
                    + "value came from, and stop. Save it, change a line, and hand it back with "
                    + "--settings.")
    private boolean printTheSettings;

    @Option(
            names = "--store",
            description = "Keep every request and reply in run.sqlite, so the run can be examined "
                    + "again later without asking the API anything. Off unless asked for: a minute "
                    + "against a fast API keeps hundreds of megabytes that almost nothing reads.")
    private boolean keepTheRun;

    /** Whether starting this run destroyed a kept run somebody may have wanted. */
    private boolean replacedAKeptRun;

    /**
     * The environment the command was started in, read for the settings and for a key left in it.
     * Handed in rather than read here, so that two commands in one program can be started in two
     * environments; and held as it was handed over, never copied, because on some systems the
     * names in it are looked up whatever their capitals, and a copy would not be.
     */
    private final Map<String, String> environment;

    /**
     * What sends the requests, made from the engine's settings once they are known. The engine that
     * talks HTTP, except in a test that needs a run to break where only a real one ever would: on
     * the thread a request is sent on.
     */
    private final Function<EngineSettings, HttpEngine> engines;

    /**
     * Where a run hears that the program has been told to stop from outside. Java's own, except in
     * a test that stops a run at a moment it chooses.
     */
    private final StopsFromOutside stops;

    /**
     * A command started in the given environment, sending through the given engine.
     *
     * @param environment the variables the command was started with
     * @param engines what makes the engine requests are sent through, from its settings
     * @param stops where a run hears that the program has been told to stop
     */
    RunCommand(Map<String, String> environment, Function<EngineSettings, HttpEngine> engines,
            StopsFromOutside stops) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.engines = Objects.requireNonNull(engines, "engines");
        this.stops = Objects.requireNonNull(stops, "stops");
    }

    @Spec
    private CommandSpec spec;

    /** Whether a share of pushing was typed, as against left at what this command defaults to. */
    private boolean askedForAShareOfPushing() {
        return spec.commandLine().getParseResult().hasMatchedOption("--fuzzing");
    }


    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (printTheCampaign && campaignFile != null) {
            // Printing the plan RESTest carries while being handed another one is two questions
            // at once, and answering the first silently would look like an answer to the second.
            err.println("restest: --print-campaign writes out the plan RESTest follows when it is "
                    + "given none, so there is nothing for it to say about " + campaignFile
                    + ". Ask for one or the other");
            return ExitCode.BAD_COMMAND_LINE;
        }
        if (printTheCampaign && printTheSettings) {
            // Two questions, and printing either answer alone would read as an answer to both.
            // Unlike --settings, which --print-settings is happy to be given: the settings it
            // prints are the ones this command would use, file and all, which is the useful
            // answer. A plan is printed instead of being read, so the two cannot be combined.
            err.println("restest: --print-campaign writes out a plan and --print-settings writes "
                    + "out the settings. Ask for one or the other");
            return ExitCode.BAD_COMMAND_LINE;
        }
        // Gathered before anything else is decided, and before anything is printed, because
        // everything after this - how the engine behaves, what an invented value looks like, how
        // much the report keeps - is read out of it. A settings file or a --set this version cannot
        // accept ends the command here, with nothing sent and nothing printed: running with
        // different numbers from the ones somebody asked for would produce a result that answers a
        // question nobody put, and answering some other question of theirs while ignoring the
        // mistake would leave them to find it later.
        SettingsInEffect configuration;
        try {
            configuration = SettingsFromEverywhere.gather(Optional.ofNullable(settingsFile),
                    environment, settingsTyped);
        } catch (SettingsException refused) {
            err.println("restest: " + refused.getMessage());
            return ExitCode.BAD_COMMAND_LINE;
        }
        Settings settings = configuration.settings();
        if (printTheCampaign) {
            // Before the document is read, and before the clock starts: this asks what RESTest
            // would do, not that it do anything.
            try {
                out.print(Campaigns.shippedText());
                return ExitCode.NO_FAULTS;
            } catch (IOException cannotRead) {
                err.println("restest: " + cannotRead.getMessage());
                return ExitCode.TOOL_FAILED;
            }
        }
        if (printTheSettings) {
            // Like --print-campaign: a question about the tool, answered without a document and
            // without starting the clock. Printed after the four layers have been gathered, so
            // what it shows is what this very command would have used.
            out.print(configuration.asAFile());
            return ExitCode.NO_FAULTS;
        }
        if (specification == null) {
            err.println("Missing required parameter: '<specification>'");
            spec.commandLine().usage(err);
            return ExitCode.BAD_COMMAND_LINE;
        }
        if (campaignFile != null && askedForAShareOfPushing()) {
            // Two ways of saying one thing. A plan sets the share of every strategy it names, and
            // --fuzzing sets one of them, so honouring both would mean deciding which of the two
            // the person meant - and the answer they get would depend on a rule nobody wrote down.
            err.println("restest: --fuzzing sets how much of a run pushes at the API, and so does "
                    + "the 'share' of a plan's strategies. Name one or the other, not both: the "
                    + "share belongs in " + campaignFile + " now");
            return ExitCode.BAD_COMMAND_LINE;
        }

        // From here until everything is written, being told to stop is heard. A run told while it
        // is still reading the document does not begin; one told while it tests is given the time
        // to leave behind what it found - and no more.
        StopFromOutside stop = new StopFromOutside();
        Duration grace = settings.schedule().interruptGrace();
        try (StopsFromOutside.Arrangement heard = stops.whenStopped(
                () -> stoppedFromOutside(stop, grace, err))) {
            try {
                int answer = readAndTest(configuration, stop, out, err);
                // Stopped while it was still reading, a run can go on to find something else to
                // answer - a key refused, nothing to test. A program stopped from outside ends with
                // Java's number whatever it answers, and so, here, does the command.
                return stop.stoppedBeforeTesting() ? ExitCode.INTERRUPTED : answer;
            } finally {
                // Before saying it is done, since the program may end the moment it has: what was
                // printed has to have reached the screen by then.
                out.flush();
                err.flush();
                stop.wrappedUp();
            }
        }
    }

    /**
     * The run itself, once what was typed has been taken in: reads the document, builds what the
     * run needs, and tests - unless it is stopped from outside before it begins testing.
     */
    private int readAndTest(SettingsInEffect configuration, StopFromOutside stop, PrintWriter out,
            PrintWriter err) {
        Settings settings = configuration.settings();
        Instant startedAt = Instant.now();
        // The engine is built before a single line of the document has been read, and that ordering
        // is the point rather than an accident. The engine starts its own clock when it is built,
        // and everything this run reports about how much of its time nothing was happening is
        // measured against that clock. Reading a large document takes real time, during which the
        // API is being asked nothing - and a tool that spends its time preparing instead of testing
        // is exactly what that measurement exists to catch. Start the clock afterwards, and such a
        // run reports itself as flawless.
        try (HttpEngine engine = engines.apply(settings.engine())) {
            ApiModel model = new SwaggerSpecificationParser(settings.document()).parse(specification);
            // Read against the document as soon as there is one, and before anything else is said
            // about it. A key typed that cannot be placed ends the command here with nothing sent:
            // a run of a protected API without the key somebody meant it to have is a run of
            // refusals, answering a question nobody asked. A document with nothing in it to test is
            // left to say so in its own words, whatever keys came with it.
            CredentialPlan credentials = CredentialPlan.none();
            if (!model.operations().isEmpty()) {
                CredentialPlan.Found keys = CredentialPlan.gather(keysGiven(), model,
                        settings.engine().followRedirects());
                keys.warnings().forEach(warning -> err.println("restest: " + warning));
                if (!keys.refusals().isEmpty()) {
                    keys.refusals().forEach(refusal -> err.println("restest: " + refusal));
                    return ExitCode.BAD_COMMAND_LINE;
                }
                credentials = keys.plan();
            }
            Dictionaries.Found found = Dictionaries.gather(dictionaries, model);
            found.problems().forEach(problem -> err.println("restest: " + problem));
            // Every list this run holds, not only the ones somebody handed over: the question a
            // plan is judged against is whether the source it names will find anything, and the
            // list RESTest carries is there whether or not anybody asked for it.
            Campaigns.Found plan;
            try {
                plan = Campaigns.gather(Optional.ofNullable(campaignFile), model,
                        found.dictionaries().stream().map(io.restest.gen.Dictionary::name)
                                .collect(java.util.stream.Collectors.toSet()));
            } catch (io.restest.core.json.JsonException cannotRead) {
                // A plan somebody named and this version cannot read. Running a different one
                // instead would answer "keep to the operations that only read" by writing to the
                // API, so the run does not start.
                err.println("restest: " + cannotRead.getMessage());
                return ExitCode.BAD_COMMAND_LINE;
            }
            plan.problems().forEach(problem -> err.println("restest: " + problem));
            RandomTestCaseGenerator generator;
            try {
                // Only when somebody typed it. Applying the default share regardless would
                // renormalise every plan back to it, so the 'share' lines of the file RESTest
                // ships - and of any file copied from it - would decide nothing at all.
                Campaign campaign = askedForAShareOfPushing()
                        ? plan.campaign().withTheShareOfPushingSetTo(fuzzingShare)
                        : plan.campaign();
                // The one part of the run that sees the API without the inputs a key fills, so that
                // nothing is invented for them; everything else keeps the document as it is.
                generator = new RandomTestCaseGenerator(credentials.modelToFillIn(model),
                        seed == null ? new java.util.SplittableRandom().nextLong() : seed,
                        found.dictionaries(), campaign, settings);
            } catch (IllegalArgumentException outOfRange) {
                // Asking for something the command line does not offer, which is the same kind of
                // mistake as misspelling an option and answers with the same number.
                err.println("restest: " + outOfRange.getMessage());
                return ExitCode.BAD_COMMAND_LINE;
            }
            // Only about lists somebody handed over. RESTest's own going unused is not news to
            // whoever asked for exactly that, and a message naming a file they never wrote is one
            // they could not act on.
            java.util.Set<String> theirs = found.namesFromTheUser();
            generator.listsGivenButNotUsed().stream().filter(theirs::contains)
                    .forEach(unused -> err.println("restest: the list of values called '" + unused
                            + "' is one this run pushes at the API with, and "
                            + (campaignFile == null
                                    ? "--fuzzing 0 asks for no pushing"
                                    : campaignFile + " gives no strategy that pushes")
                            + ", so nothing in it will be sent"));
            List<Operation> testable = generator.testableOperations();
            if (testable.isEmpty()) {
                return nothingToTest(err, model, generator);
            }
            String address;
            Path directory;
            try {
                address = BaseAddress.resolve(baseUrl, model);
                // Settled before anything in the directory is touched: a run stopped before this
                // leaves what an earlier run wrote there as it was, and has been told so.
                if (!stop.beginTesting()) {
                    return ExitCode.INTERRUPTED;
                }
                directory = prepared(outputDirectory);
            } catch (IllegalArgumentException | IOException cannotStart) {
                err.println("restest: " + cannotStart.getMessage());
                return ExitCode.NOTHING_TO_TEST;
            }
            return testing(model, generator, found, testable, address, directory, startedAt,
                    engine, credentials, configuration, stop, out, err);
        }
    }

    /**
     * What the program does, on a thread of its own, once it has been told to stop: it tells the
     * run, and waits for it to leave behind what it found. When the run is done, this returns and
     * the program ends. When it is not done in time, this says what it has not left behind, and
     * the program ends all the same: whoever stopped it is about to end it outright. A run stopped
     * before it began testing is not waited for, and this says it wrote nothing.
     */
    private void stoppedFromOutside(StopFromOutside stop, Duration grace, PrintWriter err) {
        Duration atMost = grace.plus(WRITING_ALLOWED);
        switch (stop.askAndWait(atMost)) {
            case DONE -> {
                return;
            }
            case NOTHING_BEGUN -> {
                err.println("restest: the run was stopped before it began testing, so it sent "
                        + "nothing and wrote nothing, and " + outputDirectory + " is as it was, "
                        + "with whatever an earlier run left there");
                err.flush();
                return;
            }
            case NOT_DONE -> {
                // Said below.
            }
        }
        // Looked at on a thread of its own, and for a moment at most. Writing may have run out of
        // time because the directory stopped answering, and a look that waited on it for ever
        // would keep the program from ending, when a second Ctrl-C does nothing.
        FutureTask<List<String>> look = new FutureTask<>(this::whatWasNotLeftBehind);
        Thread.ofVirtual().name("restest-looking").start(look);
        String missing;
        try {
            List<String> found = look.get(StopFromOutside.nanosIn(LOOKING_ALLOWED),
                    TimeUnit.NANOSECONDS);
            missing = found.isEmpty() ? "" : ": " + String.join("; ", found);
        } catch (TimeoutException notAnswering) {
            missing = ": " + outputDirectory + " did not answer, so what of it was written is not "
                    + "known";
        } catch (ExecutionException couldNotLook) {
            missing = "";
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            missing = "";
        }
        err.println("restest: the run was stopped from outside and had not finished writing what "
                + "it found " + human(atMost) + " later, so it ends here" + missing);
        err.flush();
    }

    /**
     * What a run stopped from outside has not left behind, read from its directory rather than from
     * the run. The report is given its name only once it is written whole, so there being none is
     * the answer. The stored run keeps a working file beside it until it is closed; what it had not
     * yet saved when the program ends is lost, and some of what it did save is in that file.
     */
    private List<String> whatWasNotLeftBehind() {
        List<String> missing = new ArrayList<>();
        if (!Files.exists(outputDirectory.resolve("report.json"))) {
            missing.add("report.json was not written");
        }
        if (keepTheRun && Files.exists(outputDirectory.resolve("run.sqlite-wal"))) {
            missing.add("run.sqlite was not closed, so the last interactions it had not yet saved "
                    + "are lost, and some of what it did save is in run.sqlite-wal beside it: keep "
                    + "the three files together");
        }
        return missing;
    }

    private int testing(ApiModel model, RandomTestCaseGenerator generator,
            Dictionaries.Found found, List<Operation> testable, String address, Path directory,
            Instant startedAt, HttpEngine engine, CredentialPlan credentials,
            SettingsInEffect configuration, StopFromOutside stop, PrintWriter out,
            PrintWriter err) {
        Settings settings = configuration.settings();
        // Each key goes into a request as it leaves, and comes out of each exchange before anything
        // else sees it. A run handed no key sends through the engine itself, exactly as it did
        // before keys could be handed over. Nothing here needs closing: the engine behind it is
        // closed where it was opened.
        HttpEngine sending = credentials.isEmpty()
                ? engine : new CredentialedEngine(engine, credentials);
        Path reportFile = directory.resolve("report.json");
        Path runFile = directory.resolve("run.sqlite");
        // What this command has to say about the run before it begins - the count, the seed, the
        // budget, what could not be read - is handed to the report that owns the screen while the
        // run lasts, and printed by it under the line naming the API. Printed from here, it was
        // written alongside that line rather than after it, and the two reached the screen in
        // either order, now and then one inside the other: one command explaining itself two ways.
        StringWriter introduction = new StringWriter();
        describe(new PrintWriter(introduction), model, generator, configuration, testable,
                credentials);
        ConsoleReport console = ConsoleReport.to(out, settings.report(),
                introduction.toString());

        RunLoop.Outcome outcome = null;
        EventStream events = null;
        // Held on to because what the rules failed to do is part of what the run is allowed to
        // claim at the end, and that is only readable once everything has been judged.
        OracleListener rules = null;
        // Opened only when this run was asked to keep itself. Written as a plain variable closed in
        // a finally, rather than as a resource that might not be there, because "there may be no
        // store" is the thing a reader has to notice here.
        InteractionStore store = keepTheRun ? SqliteInteractionStore.at(runFile) : null;
        try {
            events = new EventStream();
            // Closed first, and separately, so that everything announced has reached the reports and
            // the stored run before either is shut - and so that what the stream says about itself
            // afterwards is a finished run's answer rather than a mid-flight one.
            try (EventStream drained = events) {
                if (store != null) {
                    drained.subscribe(event -> {
                        if (event instanceof RunEvent.InteractionCompleted completed) {
                            store.record(completed.interaction());
                        }
                    });
                }
                // Before the rules and the reports, because these are what the next request is
                // built from: the sooner an identifier the API has just handed back, or a request
                // it has just accepted, is in hand, the sooner a request can use it. Nothing here
                // is subscribed at all unless the plan asked for something that learns from the
                // replies.
                generator.whatListensToTheRun().forEach(drained::subscribe);
                rules = OracleListener.standard(model, drained);
                drained.subscribe(rules);
                drained.subscribe(console);
                drained.subscribe(JsonReport.to(reportFile, configuration));

                drained.publish(new RunEvent.RunStarted(startedAt, model.title(), address));

                try {
                    // Every operation this run will never try, and why, announced before anything
                    // is sent: first the ones the document could not be read for, in the order it
                    // was read, then the ones the generator keeps - so that the reports name them,
                    // and name them the same way each time the same command is run. The count
                    // printed above includes them; their names reach the screen with the summary.
                    // Inside this block so that an announcement going wrong still leaves the
                    // summary and the report.
                    Instant said = Instant.now();
                    // Which lists of values the run holds, and which it was handed and could not
                    // read: said on the screen as the files were read, and said again here so that
                    // both reports carry it beside the verdict. A run made without a list somebody
                    // meant it to have finishes like any other, and nothing else would tell them.
                    found.read().forEach(read -> drained.publish(
                            new RunEvent.DictionaryRead(said, read.name(), read.from())));
                    found.refused().forEach(refused -> drained.publish(
                            new RunEvent.DictionaryRefused(said, refused.from(),
                                    refused.reason())));
                    model.unreadableOperations().forEach(issue -> drained.publish(
                            new RunEvent.OperationSkipped(said, issue.operation().orElseThrow(),
                                    whyNot(issue))));
                    generator.untestableOperations().forEach((operation, reason) ->
                            drained.publish(new RunEvent.OperationSkipped(said, operation, reason)));
                    // The deadline is the moment the command started plus the budget, not the
                    // moment testing starts: reading the document was paid for out of the same
                    // budget, and so is the first round the scheduler may open with.
                    Scheduler scheduler = new Scheduler(generator, settings.schedule(),
                            startedAt.plus(budget), InstantSource.system(), drained::publish);
                    outcome = RunLoop.run(scheduler, address,
                            settings.schedule().workAheadFactor() * settings.engine()
                                    .maxConcurrency(),
                            settings.schedule().announcementsAllowedToPileUp(),
                            settings.engine().callTimeout()
                                    .plus(settings.schedule().stragglerGrace()),
                            settings.schedule().interruptGrace(), stop, sending, drained);
                } finally {
                    // Even if the loop fails, what already happened is worth reporting. Without
                    // this, the run's summary and its report file would both be missing, and the
                    // evidence of whatever went wrong would go with them.
                    Instant finishedAt = Instant.now();
                    drained.publish(new RunEvent.RunFinished(finishedAt,
                            Duration.between(startedAt, finishedAt), sending.statistics(),
                            outcome == null ? stop.asked() : outcome.cutShort()));
                }
            }
        } finally {
            if (store != null) {
                store.close();
            }
        }

        // Read once, so that the sentence and the number are about the same exchanges however late
        // the last of them arrives.
        long keptWithoutTheirDetails = sending instanceof CredentialedEngine door
                ? door.hiddenWhole() : 0;
        if (keptWithoutTheirDetails > 0) {
            err.println("restest: " + keptWithoutTheirDetails + " exchange(s) were kept without "
                    + "their details, because RESTest could not pick the key out of them; this is "
                    + "a fault of RESTest rather than of the API");
        }
        OurOwnFailures ours = new OurOwnFailures(events.listenerFailures(),
                rules == null ? 0 : rules.failures(),
                rules == null ? 0 : rules.rulesThatFailed(),
                events.undelivered());
        int answer = ExitCode.of(outcome, ours.reports() + ours.judgements(),
                ours.eventsNeverHeard(), keptWithoutTheirDetails, console.faults());
        long repeated = sending instanceof CredentialedEngine door
                ? door.repliesThatRepeatedAKey() : 0;
        UnaryOperator<String> hidden = sending instanceof CredentialedEngine door
                ? door::withTheKeysHidden : UnaryOperator.identity();
        Optional<Duration> stoppedAfter = outcome != null && outcome.cutShort()
                ? stop.askedAt().map(at -> Duration.between(startedAt, at))
                : Optional.empty();
        explain(out, err, answer, outcome, address, reportFile, runFile, ours, repeated, hidden,
                stoppedAfter, settings.schedule().interruptGrace());
        return answer;
    }

    /**
     * What went wrong on RESTest's own side, as opposed to in the API being tested.
     *
     * <p>Kept apart rather than added up, because the two mean different things to whoever reads the
     * run. A report that failed may have written nothing, so nothing can be promised about the
     * files. A rule that failed leaves the files exactly as complete as they always were; what is
     * missing is some of the judging. Added together they produced a sentence that was wrong twice
     * over: it called a single broken rule thousands of failed listeners, and it withheld the line
     * saying where the perfectly good report had been written.
     *
     * @param reports          how many listeners threw while being told something
     * @param judgements       how many times a rule failed, which is once per reply it failed on
     * @param rules            how many distinct rules that was
     * @param eventsNeverHeard how many announcements never reached the listeners
     */
    private record OurOwnFailures(long reports, long judgements, long rules,
            long eventsNeverHeard) {

        /** Whether what was written down can still be promised to be complete. */
        boolean theFilesAreSound() {
            return reports == 0 && eventsNeverHeard == 0;
        }
    }

    /**
     * Says what happened, in the words that fit what actually happened.
     *
     * <p>The files are only announced when what wrote them was working. A message saying a report
     * was written is a promise, and a run whose reporting broke may not have kept it. A run whose
     * <em>judging</em> broke kept it in full, so that one is still announced.
     */
    private void explain(PrintWriter out, PrintWriter err, int answer, RunLoop.Outcome outcome,
            String address, Path reportFile, Path runFile, OurOwnFailures ours,
            long repliesThatRepeatedAKey, UnaryOperator<String> hidden,
            Optional<Duration> stoppedAfter, Duration interruptGrace) {
        // First, because it says how to read everything else: a run stopped half-way reports half
        // a run, and nothing below is about the budget it was given.
        stoppedAfter.ifPresent(after -> err.println("restest: the run was stopped from outside "
                + "after " + seconds(after) + " of its " + human(budget) + " budget; what is "
                + "reported above, and in report.json, is what it found until then"));
        if (outcome != null) {
            long notBuilt = outcome.notGenerated() + outcome.notAssembled();
            if (notBuilt > 0) {
                out.println(notBuilt + " test case(s) could not be built and were skipped");
            }
            if (outcome.stillOwed() > 0 && stoppedAfter.isPresent()) {
                err.println("restest: " + outcome.stillOwed() + " request(s) were still unanswered "
                        + "when the run stopped waiting for them, at most " + human(interruptGrace)
                        + " after it was stopped (schedule.interruptGrace), so they are missing "
                        + "from what is reported above");
            } else if (outcome.stillOwed() > 0) {
                err.println("restest: " + outcome.stillOwed() + " request(s) were never answered "
                        + "and the run stopped waiting for them, so they are missing from what is "
                        + "reported above");
            }
            // Said with what went wrong, because somebody has to be able to report it; and never as
            // a problem with the address, which is what a run where nothing came back would
            // otherwise be told, and what would send whoever reads it looking in the wrong place.
            if (outcome.lost() > 0) {
                // The requests that were neither answered, nor lost, nor still owed ended without
                // an answer from the API - refused, reset, timed out - and when nothing at all was
                // answered those say something about the address, which is worth saying beside
                // what RESTest did wrong rather than instead of it.
                long unansweredByTheApi = outcome.sent() - outcome.answered() - outcome.lost()
                        - outcome.stillOwed();
                err.println("restest: " + outcome.lost() + " request(s) were lost by RESTest "
                        + "itself, on their way to the API or back, and are missing from what is "
                        + "reported above; this is a fault of RESTest rather than of the API"
                        + (outcome.nothingAnswered() ? ". Not one request was answered, and a run "
                                + "stops sending, at the end of the round it is in, once as many "
                                + "as can be in flight have gone unanswered" : "")
                        + (outcome.nothingAnswered() && unansweredByTheApi > 0
                                ? "; the other " + unansweredByTheApi + " ended without an answer "
                                        + "- refused, reset, timed out, or never sent at all - so "
                                        + "check " + address + " as well"
                                : "")
                        + (outcome.firstLoss().isPresent() ? ". The first went wrong like this:"
                                : ""));
                outcome.firstLoss().ifPresent(failure -> printed(failure, err, hidden));
            }
        }
        // A reply with a key hidden in it is not quite what the API sent, and the check of replies
        // against the document lets pass whatever the hiding may have changed - so how many there
        // were is part of what "nothing wrong" means. An API handing a key back is worth knowing
        // about in itself.
        if (repliesThatRepeatedAKey > 0) {
            out.println(repliesThatRepeatedAKey
                    + (repliesThatRepeatedAKey == 1 ? " reply" : " replies")
                    + " repeated a key back; it is hidden there too, and the check of replies "
                    + "against the document passes over whatever the hiding changed");
        }
        // Said first, and said whatever else went wrong. A run can break in both ways at once,
        // and an earlier version of this reported only the listeners - so a run where one report
        // threw once and one rule threw four thousand times mentioned the once and never the four
        // thousand, which is the larger of the two problems by three orders of magnitude.
        if (ours.rules() > 0) {
            err.println("restest: " + ours.rules() + " rule(s) failed, " + ours.judgements()
                    + " time(s) in all, so some replies were judged by fewer rules than the rest "
                    + "and finding nothing wrong with them means less than it should");
        }
        // Whatever the answer: a run stopped from outside answers with Java's number, and a report
        // that broke before it was stopped still may not have written anything.
        if (!ours.theFilesAreSound()) {
            err.println("restest: " + ours.reports() + " listener(s) failed and "
                    + ours.eventsNeverHeard() + " event(s) never arrived, so what is printed above "
                    + "may be incomplete and the files may not have been written");
            return;
        }

        out.println("report written to " + reportFile + sizeOf(reportFile));
        if (keepTheRun) {
            out.println("run stored in " + runFile + sizeOf(runFile));
        } else {
            out.println("the run itself was not kept; pass --store to keep every request and reply");
        }
        if (replacedAKeptRun) {
            // Whoever kept a run and then ran again in the same place has just lost it. Saying so
            // here is the difference between a rule and a nasty surprise.
            out.println("a run kept in " + outputDirectory + " by an earlier command was replaced");
        }

        if (answer != ExitCode.NOTHING_TO_TEST || outcome == null) {
            return;
        }
        // Three different ways to end up with no evidence about the API, and telling somebody the
        // wrong one wastes their afternoon.
        if (outcome.nothingCouldBeBuilt()) {
            err.println("restest: none of the " + outcome.notGenerated() + " + "
                    + outcome.notAssembled() + " test case(s) this document produced could be "
                    + "turned into a request that could be sent, so nothing was tested");
        } else if (outcome.sent() == 0) {
            err.println("restest: the budget of " + human(budget) + " ran out before a single "
                    + "request could be sent - reading the document and starting up takes a moment, "
                    + "and it is paid out of the budget. Give the run more time.");
        } else {
            err.println("restest: nothing at " + address + " answered any of the " + outcome.sent()
                    + " requests, so the API was never actually tested. Check the address, and that "
                    + "it is running.");
        }
    }

    /**
     * What went wrong, with its stack trace, the way the run writes everything else: with every key
     * it was handed hidden. Saying so can fail in its turn - a program that has run out of memory
     * may not have enough left to write it out - and the run goes on to say what it wrote, which it
     * did write.
     */
    private static void printed(Throwable failure, PrintWriter err, UnaryOperator<String> hidden) {
        try {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            err.print(hidden.apply(trace.toString()));
            err.flush();
        } catch (Throwable whileSayingSo) {
            // Its kind at least, which holds no key, so that the line above it is not left
            // introducing nothing. If even that cannot be written, the count above has been said.
            try {
                err.println(failure.getClass().getName() + " (the rest of it could not be "
                        + "written out)");
            } catch (Throwable stillNot) {
                // Nothing is left to say it with.
            }
        }
    }

    /**
     * Explains a document nothing can be done with.
     *
     * <p>Having nothing to test is not the same as having found nothing wrong, and the two must not
     * produce the same answer: an empty report that exits successfully reads as "this API is fine".
     */
    private int nothingToTest(PrintWriter err, ApiModel model, RandomTestCaseGenerator generator) {
        var refused = generator.untestableOperations();
        List<SpecificationIssue> unreadable = model.unreadableOperations();
        int setAside = generator.operationsThePlanSetAside().size();
        // The plan is asked about first, and only about what could be read: an operation the plan
        // set aside is one somebody asked to leave alone, and blaming the document for it would
        // report a plan working exactly as intended as a problem with the API. One that could not
        // be read is said as well, because the plan had nothing to judge there.
        if (refused.isEmpty() && setAside > 0) {
            err.println("restest: the plan keeps this run to no operation at all - the document "
                    + "describes " + setAside + " that could have been tested, and its "
                    + "'operations' filter matches none of them"
                    + (unreadable.isEmpty() ? ""
                            : "; " + unreadable.size() + " more could not be read at all"));
        } else if (refused.isEmpty() && unreadable.isEmpty()) {
            err.println("restest: the document describes no operation that could be tested");
        } else if (refused.isEmpty()) {
            err.println("restest: none of the " + unreadable.size() + " operations in the document "
                    + "can be tested; the first says: " + whyNot(unreadable.get(0)));
        } else {
            // The reason quoted is one the generator gave, whenever it gave one: that operation
            // was read and kept by the plan, so its reason is why this run is empty. One that could
            // not be read may be one the plan would have left alone, and is counted beside it.
            err.println("restest: none of the " + refused.size() + " operations in the document "
                    + "can be tested; the first says: " + refused.values().iterator().next()
                    + (unreadable.isEmpty() ? ""
                            : "; " + unreadable.size() + " more could not be read at all"));
        }
        report(err, model.issues());
        return ExitCode.NOTHING_TO_TEST;
    }

    private void describe(PrintWriter out, ApiModel model, RandomTestCaseGenerator generator,
            SettingsInEffect configuration, List<Operation> testable, CredentialPlan credentials) {
        int setAside = generator.operationsThePlanSetAside().size();
        // Every operation the document describes, including the ones it could not be read for.
        out.println(testable.size() + " of "
                + (testable.size() + generator.untestableOperations().size()
                        + model.unreadableOperations().size() + setAside)
                + " operations can be tested, seed " + generator.seed()
                + ", budget " + human(budget));
        // Straight under the count, so that "of them" is read against it.
        describeTheKeys(out, model, credentials, testable);
        // Said as its own line rather than folded into the count, because the two are different
        // things: one is what the document makes impossible, the other is what somebody asked for.
        if (setAside > 0) {
            out.println("  " + setAside + " left alone by the plan");
        }
        // The seed has just been printed, and for this plan it promises less than it usually does.
        // Saying so here rather than leaving somebody to find out by running the same command
        // twice and getting two different runs.
        if (generator.dependsOnTheApisAnswers()) {
            out.println("  what it sends depends on the API's own replies, so the seed alone does "
                    + "not repeat this run" + (keepTheRun ? "" : "; --store keeps what it sent"));
        }
        // An environment variable is invisible in the command somebody typed and in the transcript
        // they paste into a bug report, so a run configured by one has to say so somewhere a person
        // will look. All of them are in report.json; this is the line that sends them there.
        long changed = configuration.rows().stream()
                .filter(row -> row.source() != io.restest.core.settings.SettingSource.DEFAULT)
                .count();
        if (changed > 0) {
            out.println("  " + changed + " setting(s) are not what RESTest does by default; "
                    + "--print-settings lists them with where each came from");
        }
        report(out, model.issues());
        out.println();
    }

    /**
     * What happens to the keys: where each one handed over goes, and which keys the document asks
     * for that nobody handed over.
     *
     * <p>Said before the first request, because a protected API run without its key answers every
     * request with a refusal, and that should not take the whole budget to find out. Only the
     * operations that can be tested are counted, since those are the ones the count above names.
     */
    private static void describeTheKeys(PrintWriter out, ApiModel model,
            CredentialPlan credentials, List<Operation> testable) {
        Set<OperationId> tested = testable.stream().map(Operation::id)
                .collect(Collectors.toSet());
        for (CredentialPlan.Placed placed : credentials.placed()) {
            long with = placed.operations().stream().filter(tested::contains).count();
            if (with == 0) {
                out.println("  " + placed.named() + " goes with none of them: "
                        + placed.whyWithNone().orElse("the operations it would go with cannot be "
                                + "tested"));
            } else {
                out.println("  " + placed.named() + " goes with " + ofThem(with, tested.size())
                        + ", in " + inWords(placed.places()) + "; what the run writes says "
                        + placed.mask() + " in its place");
            }
        }
        long keys = model.securitySchemes().values().stream()
                .filter(SecurityScheme.ApiKey.class::isInstance).count();
        for (CredentialPlan.Missing missing : credentials.missing()) {
            long asking = missing.operations().stream().filter(tested::contains).count();
            if (asking == 0) {
                continue;
            }
            String option = AuthGiven.OPTION + " "
                    + (keys == 1 ? "<key>" : asAShellWord(missing.scheme() + "=<key>"));
            String which = "(" + missing.scheme() + ", in " + missing.place().described() + ")";
            if (missing.askedForNowhere()) {
                out.println("  the document declares an API key it asks for on no operation "
                        + which + ": " + option + " sends it with " + ofThem(asking, tested.size()));
            } else {
                out.println("  " + (asking == tested.size() && asking > 1 ? "all " : "") + asking
                        + " of them " + (asking == 1 ? "asks" : "ask") + " for an API key that was "
                        + "not given " + which + ": " + option + " gives it");
            }
        }
    }

    private static String ofThem(long some, int all) {
        return some == all && all > 1 ? "every one of them" : some + " of them";
    }

    /** Places in words: "the header api_key", or "the query parameter k and the form field k". */
    private static String inWords(List<Place> places) {
        List<String> described = places.stream().map(Place::described).toList();
        if (described.size() <= 1) {
            return String.join("", described);
        }
        return String.join(", ", described.subList(0, described.size() - 1)) + " and "
                + described.get(described.size() - 1);
    }

    /** A word as a shell needs it to arrive whole: quoted when it holds anything but the plain. */
    private static String asAShellWord(String word) {
        return word.matches("[A-Za-z0-9._=<>-]+") ? word : "'" + word.replace("'", "'\\''") + "'";
    }

    /**
     * Every key handed over: the one left in the environment first, then each one typed, in the
     * order typed - which is the order in which a key typed wins over one left in the environment
     * for the same place. An empty variable is how a shell says a variable is not there for one
     * command, so it counts as none, and so does one holding nothing but a line break.
     */
    private List<AuthGiven> keysGiven() {
        List<AuthGiven> given = new ArrayList<>();
        String left = environment.get(AuthGiven.VARIABLE);
        if (left != null && !AuthGiven.fromTheEnvironment(left).isEmpty()) {
            given.add(AuthGiven.fromTheEnvironment(left));
        }
        for (int position = 0; position < keysTyped.size(); position++) {
            given.add(AuthGiven.typed(keysTyped.get(position), position + 1));
        }
        return given;
    }

    /**
     * Why an operation the document could not be read for is not tested, with where it is.
     *
     * <p>Where, because it never became an operation and nothing else about it is known: a document
     * can give two operations one name, and the place in the document is what tells the one that
     * could not be read from the one that could. These are counted and named beside the operations
     * the generator refused, but kept apart from them, because the generator's list is also what it
     * checks before building a request, and the readable one must not be taken for this.
     */
    private static String whyNot(SpecificationIssue unreadable) {
        return unreadable.message() + " (" + unreadable.location() + ")";
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

    /**
     * Makes the output directory, and clears out whatever an earlier run left in it.
     *
     * <p>Including the two files the database keeps beside its own: a run cut short leaves them
     * behind, and a fresh database next to another run's leftovers is a database that may not open.
     */
    private Path prepared(Path directory) throws IOException {
        try {
            Files.createDirectories(directory);
            if (!Files.isWritable(directory)) {
                // Asked now rather than discovered later, and the difference is which answer the
                // command gives. Left to be found out when the report is written, this surfaces as
                // a listener that failed or a database that would not open - which is RESTest
                // malfunctioning, answer 4, printed with a stack trace. It is not a malfunction. It
                // is one of the ordinary ways a run cannot start, it is answer 3, and it deserves a
                // sentence naming the directory and nothing more.
                throw new IOException("the directory is there but cannot be written to");
            }
            for (String leftOver : FILES_A_RUN_WRITES) {
                if (Files.deleteIfExists(directory.resolve(leftOver))
                        && leftOver.equals("run.sqlite")) {
                    replacedAKeptRun = true;
                }
            }
        } catch (IOException cannotBeUsed) {
            throw new IOException("nothing can be written to " + directory + " ("
                    + cannotBeUsed.getMessage() + "); choose somewhere else with --out");
        }
        return directory;
    }

    /**
     * How big a file this run wrote, in the units a person reads.
     *
     * <p>Beside the name rather than left to be discovered later: a tool that writes hundreds of
     * megabytes owes whoever ran it that number at the moment it writes them.
     */
    private static String sizeOf(Path file) {
        long bytes;
        try {
            bytes = Files.size(file);
        } catch (IOException cannotTell) {
            // The file was written; not being able to measure it is no reason to say nothing at all.
            return "";
        }
        if (bytes < 1024) {
            return " (" + bytes + " bytes)";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double size = bytes / 1024.0;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, " (%.1f %s)", size, units[unit]);
    }

    /** The budget the way it was asked for, rather than the way a machine writes it. */
    private static String human(Duration budget) {
        return budget.toMillis() % 1000 == 0 ? budget.toSeconds() + "s" : budget.toMillis() + "ms";
    }

    /** A length of time the way a person reads one on a clock: {@code 12.3s}, whatever the machine. */
    private static String seconds(Duration length) {
        return String.format(Locale.ROOT, "%.1fs", length.toMillis() / 1000.0);
    }
}
