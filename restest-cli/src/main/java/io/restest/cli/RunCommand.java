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
import io.restest.core.exec.HttpEngine;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.settings.Settings;
import io.restest.core.settings.SettingsException;
import io.restest.core.settings.SettingsInEffect;
import io.restest.core.store.InteractionStore;
import io.restest.exec.OkHttpEngine;
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
 * anybody can paste into a terminal to see it happen again. What it found is left behind as a file
 * describing the faults, for a program to read. Asked to, it also keeps every request and reply, so
 * the run can be examined afterwards without asking the API anything - but only when asked, because
 * a minute against a fast API is hundreds of megabytes that almost nothing reads back.
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
        sortOptions = false)
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
     * into the next run and be read as part of it.
     */
    private static final List<String> FILES_A_RUN_WRITES =
            List.of("run.sqlite", "run.sqlite-wal", "run.sqlite-shm", "report.json");

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
            description = "Which machine the API is running on, for instance "
                    + "http://localhost:8080. Taken from the document when it names a usable "
                    + "address and this is omitted. Given without a path, the directory the "
                    + "document says the API is served from is kept; given with one, such as "
                    + "http://localhost:8080/api/v3, that replaces it.")
    private String baseUrl;

    @Option(
            names = "--budget",
            paramLabel = "<duration>",
            defaultValue = "60s",
            converter = BudgetDuration.class,
            description = "How long to keep testing: 30s, 5m, 2h, or a plain number of seconds. "
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
            description = "Where to write this run's files. An earlier run in the same directory is "
                    + "replaced. Default: ${DEFAULT-VALUE}.")
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
            description = "How much of the time to spend pushing at the API with values nobody "
                    + "sensible would send - empty text, enormous numbers, the wrong kind of value "
                    + "entirely. A healthy API takes them or turns them away; a fragile one falls "
                    + "over. 0 sends none of them. Default: ${DEFAULT-VALUE}.")
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
            description = "One setting, for instance --set engine.maxConcurrency=1 for an API too "
                    + "fragile to be asked two things at once. Repeat for several. Wins over "
                    + "--settings and over the environment.")
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
                    System.getenv(), settingsTyped);
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

        Instant startedAt = Instant.now();
        // The engine is built before a single line of the document has been read, and that ordering
        // is the point rather than an accident. The engine starts its own clock when it is built,
        // and everything this run reports about how much of its time nothing was happening is
        // measured against that clock. Reading a large document takes real time, during which the
        // API is being asked nothing - and a tool that spends its time preparing instead of testing
        // is exactly what that measurement exists to catch. Start the clock afterwards, and such a
        // run reports itself as flawless.
        try (HttpEngine engine = new OkHttpEngine(settings.engine())) {
            ApiModel model = new SwaggerSpecificationParser(settings.document()).parse(specification);
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
                generator = new RandomTestCaseGenerator(model,
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
                directory = prepared(outputDirectory);
            } catch (IllegalArgumentException | IOException cannotStart) {
                err.println("restest: " + cannotStart.getMessage());
                return ExitCode.NOTHING_TO_TEST;
            }
            return testing(model, generator, testable, address, directory, startedAt, engine,
                    configuration, out, err);
        }
    }

    private int testing(ApiModel model, RandomTestCaseGenerator generator, List<Operation> testable,
            String address, Path directory, Instant startedAt, HttpEngine engine,
            SettingsInEffect configuration, PrintWriter out, PrintWriter err) {
        Settings settings = configuration.settings();
        Path reportFile = directory.resolve("report.json");
        Path runFile = directory.resolve("run.sqlite");
        // What this command has to say about the run before it begins - the count, the seed, the
        // budget, what could not be read - is handed to the report that owns the screen while the
        // run lasts, and printed by it under the line naming the API. Printed from here, it was
        // written alongside that line rather than after it, and the two reached the screen in
        // either order, now and then one inside the other: one command explaining itself two ways.
        StringWriter introduction = new StringWriter();
        describe(new PrintWriter(introduction), model, generator, configuration, testable.size());
        ConsoleReport console = ConsoleReport.to(out, generator.sourcesThatPushAtTheApi(),
                settings.report(), introduction.toString());

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
                // Before the rules and the reports, because this one is what the next request is
                // built from: the sooner an identifier the API has just handed back is in hand,
                // the sooner a request can carry it. Nothing here is subscribed at all unless the
                // plan asked for a source that learns from the replies.
                generator.whatListensToTheRun().ifPresent(drained::subscribe);
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
                    unreadable(model).forEach(issue -> drained.publish(
                            new RunEvent.OperationSkipped(said, issue.operation().orElseThrow(),
                                    issue.message())));
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
                            settings.engine().readTimeout()
                                    .plus(settings.schedule().stragglerGrace()),
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
        } finally {
            if (store != null) {
                store.close();
            }
        }

        OurOwnFailures ours = new OurOwnFailures(events.listenerFailures(),
                rules == null ? 0 : rules.failures(),
                rules == null ? 0 : rules.rulesThatFailed(),
                events.undelivered());
        int answer = ExitCode.of(outcome, ours.reports() + ours.judgements(),
                ours.eventsNeverHeard(), console.faults());
        explain(out, err, answer, outcome, address, reportFile, runFile, ours);
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
            String address, Path reportFile, Path runFile, OurOwnFailures ours) {
        if (outcome != null) {
            long lost = outcome.notGenerated() + outcome.notAssembled();
            if (lost > 0) {
                out.println(lost + " test case(s) could not be built and were skipped");
            }
            if (outcome.stillOwed() > 0) {
                err.println("restest: " + outcome.stillOwed() + " request(s) were never answered "
                        + "and the run stopped waiting for them, so they are missing from what is "
                        + "reported above");
            }
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
        if (answer == ExitCode.TOOL_FAILED && !ours.theFilesAreSound()) {
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
     * Explains a document nothing can be done with.
     *
     * <p>Having nothing to test is not the same as having found nothing wrong, and the two must not
     * produce the same answer: an empty report that exits successfully reads as "this API is fine".
     */
    private int nothingToTest(PrintWriter err, ApiModel model, RandomTestCaseGenerator generator) {
        var refused = generator.untestableOperations();
        List<SpecificationIssue> unreadable = unreadable(model);
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
        } else {
            // What could not be read comes first, as it does everywhere else a run names them.
            String first = unreadable.isEmpty()
                    ? refused.values().iterator().next() : unreadable.get(0).message();
            err.println("restest: none of the " + (refused.size() + unreadable.size())
                    + " operations in the document can be tested; the first says: " + first);
        }
        report(err, model.issues());
        return ExitCode.NOTHING_TO_TEST;
    }

    private void describe(PrintWriter out, ApiModel model, RandomTestCaseGenerator generator,
            SettingsInEffect configuration, int testable) {
        int setAside = generator.operationsThePlanSetAside().size();
        // Every operation the document describes, including the ones it could not be read for.
        out.println(testable + " of "
                + (testable + generator.untestableOperations().size() + unreadable(model).size()
                        + setAside)
                + " operations can be tested, seed " + generator.seed()
                + ", budget " + human(budget));
        // Said as its own line rather than folded into the count, because the two are different
        // things: one is what the document makes impossible, the other is what somebody asked for.
        if (setAside > 0) {
            out.println("  " + setAside + " left alone by the plan");
        }
        // The seed has just been printed, and for this plan it promises less than it usually does.
        // Saying so here rather than leaving somebody to find out by running the same command
        // twice and getting two different runs.
        if (generator.whatListensToTheRun().isPresent()) {
            out.println("  values from the API's own replies, so the seed alone does not repeat "
                    + "this run" + (keepTheRun ? "" : "; --store keeps what it sent"));
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
     * The operations the document could not be read for at all, each with what reading it said, in
     * the order it was read.
     *
     * <p>They never became operations, so the generator never judged them and they are nowhere in
     * what it will or will not build. They are counted and named beside the ones it refused, but
     * kept apart from them: an operation the generator can build may share a name with one of these,
     * because a document can give two operations one name, and it must not be taken for it.
     */
    private static List<SpecificationIssue> unreadable(ApiModel model) {
        return model.issues().stream()
                .filter(issue -> issue.skipsAnOperation() && issue.operation().isPresent())
                .toList();
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
}
