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
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * The {@code restest} command: where a person's typing becomes a run.
 *
 * <p>There is one thing to do today - {@code restest run}, which tests an API and reports what is
 * wrong with it. Later versions add commands that look at a finished run rather than making a new
 * one; they will sit beside this one and share its options and its answers.
 *
 * <p>This is the only place in the whole of RESTest that ends the program. Everything else hands
 * back a result and lets its caller decide, which is what makes any part of the tool usable inside
 * somebody else's program: a library that can stop the program it is embedded in is a library nobody
 * can embed. Running the command and ending the program are therefore two separate methods here, and
 * the tests use the first one.
 *
 * <p>Running the command always ends in an answer, however badly RESTest itself breaks along the
 * way - running out of memory included. A crash that escaped instead would end the program with the
 * number Java gives any program that crashes, which is the number that says a fault was found in
 * the API.
 */
@Command(
        name = "restest",
        description = "Black-box testing for REST APIs, from an OpenAPI document.",
        mixinStandardHelpOptions = true,
        versionProvider = ToolVersion.class,
        subcommands = RunCommand.class,
        synopsisSubcommandLabel = "COMMAND",
        // What the command-line framework answers when something fails inside the framework itself
        // rather than in a command - while it writes the help, say. Left alone it answers 1, the
        // number that says a fault was found in the API.
        exitCodeOnExecutionException = ExitCode.TOOL_FAILED)
public final class Restest {

    private Restest() {
    }

    /**
     * Runs a command and ends the program with what it answered.
     *
     * @param arguments what was typed after {@code restest}
     */
    public static void main(String[] arguments) {
        int answer = ExitCode.TOOL_FAILED;
        try {
            answer = run(arguments);
        } catch (Throwable escaped) {
            // Running the command answers for whatever escapes it. What could still get here is a
            // failure in the few lines around it - making somewhere to write, and flushing it -
            // which takes memory already gone; it is said if it can be.
            escaped.printStackTrace();
        } finally {
            // Always with a number RESTest chose. Java would otherwise end the program with 1, the
            // number that says a fault was found in the API.
            System.exit(answer);
        }
    }

    /**
     * Runs a command and answers, without ending anything.
     *
     * <p>The answer is 0 when the run found nothing wrong, 1 when it found a fault, 2 when the
     * command line was wrong, 3 when there was nothing to test, and 4 when RESTest itself went
     * wrong. A failure that would otherwise be thrown out of the command - running out of memory
     * included - is answered with 4 instead, and said where problems go, with the stack trace
     * somebody needs to report it.
     *
     * @param arguments what was typed after {@code restest}
     * @return the number the command answered with
     */
    public static int run(String[] arguments) {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        try {
            return run(arguments, out, err);
        } finally {
            // Flushed, never closed: closing these would close the program's own output, and
            // whatever runs next in the same program would have nowhere to write.
            out.flush();
            err.flush();
        }
    }

    /**
     * The same, writing wherever it is told to, so that a test can read back what a person would
     * have seen.
     *
     * @param arguments what was typed after {@code restest}
     * @param out where everything the command has to say goes
     * @param err where anything that went wrong goes
     * @return the number the command answered with
     */
    public static int run(String[] arguments, PrintWriter out, PrintWriter err) {
        // The environment is read here, once, and handed to the command rather than read by it, so
        // that the part of the program deciding what the command does can be given another one.
        return run(arguments, out, err, System.getenv());
    }

    /**
     * The same, started in the given environment rather than the program's own: what a test uses
     * to see what a variable does without setting one in the machine running the tests.
     *
     * @param arguments what was typed after {@code restest}
     * @param out where everything the command has to say goes
     * @param err where anything that went wrong goes
     * @param environment the variables the command is started with
     * @return the number the command answered with
     */
    static int run(String[] arguments, PrintWriter out, PrintWriter err,
            Map<String, String> environment) {
        try {
            return answer(arguments, out, err, environment);
        } catch (Throwable failure) {
            // What arrives here is what the command-line framework does not catch. It catches the
            // exceptions a command throws, and lets through the more serious kind of failure Java
            // calls an error: running out of memory, running out of room for the stack, a piece of
            // Java missing where the program runs. Let through, one of those would end the program
            // with the number Java gives any program that crashes - 1, the number that says a fault
            // was found in the API - and whatever ran the command would count a crash as a finding.
            //
            // Caught here rather than only where the program ends, so that every caller gets the
            // same answer: a test, or a program running RESTest inside itself, reads 4 exactly as a
            // script does. On its way out the run has closed the engine, the store and the thread
            // that delivers its events; requests it had already sent may still be finishing, for as
            // long as the engine waits for a reply.
            return saysItBroke(failure, err);
        }
    }

    /**
     * The command itself, as the command-line framework runs it: everything
     * {@link #run(String[], PrintWriter, PrintWriter, Map)} does, except answering for what the
     * framework lets through.
     */
    private static int answer(String[] arguments, PrintWriter out, PrintWriter err,
            Map<String, String> environment) {
        CommandLine restest = new CommandLine(new Restest(), new StartedIn(environment))
                .setOut(out)
                .setErr(err)
                // Plain text, on every machine. The command-line framework would otherwise colour
                // its own output wherever it believes the terminal can take it, which is a decision
                // it makes differently on different operating systems - so the same command would
                // print one thing here and another thing there, and a transcript pasted into a bug
                // report or compared with last week's would carry invisible characters that are not
                // in the other one. Everything else this tool prints is already written the same way
                // everywhere, for the same reason.
                .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF))
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setParameterExceptionHandler(Restest::saysWhatWasWrong)
                // Anything that reaches here is RESTest going wrong rather than the API under test,
                // and the two must not answer the same.
                .setExecutionExceptionHandler(
                        (failure, command, parsed) -> saysItBroke(failure, err));
        // With no sub-command there is nothing to run; saying so and showing what the choices are
        // beats a silent success.
        if (arguments.length == 0) {
            restest.usage(err);
            return ExitCode.BAD_COMMAND_LINE;
        }
        if (aKeyStuckToItsOption(List.of(arguments), optionsOf(restest), 0)) {
            err.println("restest: an argument begins with " + AuthGiven.OPTION + " and runs straight "
                    + "on into something else, and is not repeated here in case that is a key: "
                    + "write " + AuthGiven.OPTION + " <key> or " + AuthGiven.OPTION + "=<key>. The "
                    + "value of another option that begins with " + AuthGiven.OPTION + " goes after "
                    + "its '=', as in --out=" + AuthGiven.OPTION + "-results");
            return ExitCode.BAD_COMMAND_LINE;
        }
        return restest.execute(arguments);
    }

    /**
     * Says that RESTest itself went wrong, and gives the answer that means so.
     *
     * <p>What went wrong is named and its stack trace printed, because somebody has to be able to
     * report it. Saying so can fail in its turn - a program that has just run out of memory may not
     * have enough left to write a stack trace - and the answer is given all the same, since the
     * answer is what whatever ran the command acts on.
     *
     * @param failure what went wrong
     * @param err where anything that went wrong goes
     * @return the answer that says RESTest itself went wrong
     */
    private static int saysItBroke(Throwable failure, PrintWriter err) {
        try {
            err.println("restest: the run could not be completed: " + failure);
            failure.printStackTrace(err);
        } catch (Throwable whileSayingSo) {
            // Nothing is left to say it with. The answer still goes out.
        }
        return ExitCode.TOOL_FAILED;
    }

    /** The name of every option of every command, each way it can be written. */
    private static List<String> optionsOf(CommandLine restest) {
        List<String> options = new ArrayList<>();
        List<CommandLine> commands = new ArrayList<>(List.of(restest));
        commands.addAll(restest.getSubcommands().values());
        for (CommandLine command : commands) {
            command.getCommandSpec().options()
                    .forEach(option -> options.addAll(List.of(option.names())));
        }
        return options;
    }

    /**
     * Whether an argument begins with {@code --auth} and runs straight on into something other than
     * an {@code =}, as a key typed without the space after the option does, and is no option of its
     * own. It is caught before the command-line framework sees it, because every way the framework
     * would take it repeats it whole: as an option that does not exist, or as the value of the
     * option before it - a directory to write into, say. A file of arguments named with {@code @} is
     * read by the framework as if its words had been typed, so its words are looked at too; and
     * nothing after {@code --}, where only the document's name can come, is.
     */
    private static boolean aKeyStuckToItsOption(List<String> arguments, List<String> options,
            int filesDeep) {
        String option = AuthGiven.OPTION;
        for (String argument : arguments) {
            if (argument.equals("--")) {
                return false;
            }
            boolean stuck = argument.length() > option.length()
                    && argument.regionMatches(true, 0, option, 0, option.length())
                    && argument.charAt(option.length()) != '=';
            boolean anOption = options.stream().anyMatch(name -> argument.equals(name)
                    || argument.startsWith(name + "="));
            if (stuck && !anOption) {
                return true;
            }
            if (argument.startsWith("@") && !argument.startsWith("@@") && filesDeep < 8
                    && aKeyStuckToItsOption(wordsIn(argument.substring(1)), options,
                            filesDeep + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The words of a file of arguments, split where the framework splits them, with any quotation
     * marks around a word taken off. None, for a file that cannot be read: the framework says so
     * itself.
     */
    private static List<String> wordsIn(String file) {
        try {
            List<String> words = new ArrayList<>();
            for (String word : Files.readString(Path.of(file)).split("\\s+")) {
                String bare = word.replaceAll("^[\"']+|[\"']+$", "");
                if (!bare.isEmpty()) {
                    words.add(bare);
                }
            }
            return words;
        } catch (IOException | RuntimeException unreadable) {
            return List.of();
        }
    }

    /**
     * Says what was wrong with what was typed, the way the command-line framework would, except for
     * one thing: a key is never repeated back.
     *
     * <p>A mistyped option leaves whatever followed it as something nobody asked for, and what
     * follows {@code --auth} is a key. Repeating it would put the key on the screen, and into any
     * log the screen is kept in, for the sake of a typing mistake. So an argument the framework did
     * not understand is not repeated at all, though the names of options that do not exist are
     * still said, up to any {@code =}, since those are what somebody needs to see to put the
     * mistake right; the options that do exist and look like them are suggested as before. Any
     * other complaint is said in the framework's words, with whatever was typed after
     * {@code --auth} taken out: an option missing its value, just before {@code --auth=<key>}, is
     * told what it found instead, and would say so.
     */
    private static int saysWhatWasWrong(ParameterException wrong, String[] arguments) {
        CommandLine command = wrong.getCommandLine();
        PrintWriter err = command.getErr();
        if (wrong instanceof UnmatchedArgumentException unmatched) {
            List<String> options = unmatched.getUnmatched().stream()
                    .filter(argument -> argument.startsWith("--"))
                    .map(argument -> argument.contains("=")
                            ? argument.substring(0, argument.indexOf('=')) : argument)
                    .map(Restest::asFarAsItIsSafe)
                    .toList();
            long others = unmatched.getUnmatched().size() - options.size();
            StringBuilder said = new StringBuilder();
            if (!options.isEmpty()) {
                said.append("Unknown option").append(options.size() == 1 ? "" : "s").append(": ")
                        .append(String.join(", ", options));
            }
            if (others > 0) {
                said.append(said.isEmpty() ? "" : "; ").append(others)
                        .append(others == 1 ? " argument was" : " arguments were")
                        .append(" not understood, and not repeated here in case one is a key");
            }
            err.println(said);
            if (!UnmatchedArgumentException.printSuggestions(unmatched, err)) {
                command.usage(err, command.getColorScheme());
            }
        } else {
            err.println(withoutTheKeys(String.valueOf(wrong.getMessage()), arguments));
            command.usage(err, command.getColorScheme());
        }
        return command.getCommandSpec().exitCodeOnInvalidInput();
    }

    /**
     * The name of an option that does not exist, as far as it is safe to say it: no further than
     * {@code --auth} for one that begins with it and runs on, since the rest is most likely a key
     * stuck to the option in a file of arguments.
     */
    private static String asFarAsItIsSafe(String name) {
        String option = AuthGiven.OPTION;
        return name.length() > option.length()
                && name.regionMatches(true, 0, option, 0, option.length())
                ? name.substring(0, option.length()) + "..." : name;
    }

    /**
     * A complaint with every key typed after {@code --auth} taken out, wherever the framework quoted
     * it: whole, as {@code '<key>'}, or after the option, as {@code --auth=<key>}. A key written
     * with {@code =} in a file of arguments named with {@code @} is not among the arguments here,
     * so whatever follows {@code --auth=} to the end of a line is taken out as well.
     */
    static String withoutTheKeys(String complaint, String[] arguments) {
        String hidden = AuthGiven.OPTION + "=<key>";
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].equals(AuthGiven.OPTION) && i + 1 < arguments.length) {
                keys.add(arguments[i + 1]);
            } else if (arguments[i].startsWith(AuthGiven.OPTION + "=")) {
                keys.add(arguments[i].substring(AuthGiven.OPTION.length() + 1));
            }
        }
        keys.sort(Comparator.comparingInt(String::length).reversed());
        String said = complaint;
        for (String key : keys) {
            if (!key.isEmpty()) {
                said = said.replace(AuthGiven.OPTION + "=" + key, hidden)
                        .replace("'" + key + "'", "'<key>'");
            }
        }
        int from = said.indexOf(AuthGiven.OPTION + "=");
        while (from >= 0) {
            if (!said.startsWith(hidden, from)) {
                int end = said.indexOf('\n', from);
                end = end < 0 ? said.length() : end;
                boolean quoted = said.charAt(end - 1) == '\'';
                said = said.substring(0, from) + hidden + (quoted ? "'" : "") + said.substring(end);
            }
            from = said.indexOf(AuthGiven.OPTION + "=", from + hidden.length());
        }
        return said;
    }

    /**
     * Builds the run command with the environment it was handed, and everything else the way the
     * command-line framework would. A class rather than a record, so that printing it never prints
     * the environment, a key left in it included.
     */
    private static final class StartedIn implements CommandLine.IFactory {

        private final Map<String, String> environment;

        StartedIn(Map<String, String> environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
        }

        @Override
        public <K> K create(Class<K> kind) throws Exception {
            return kind == RunCommand.class
                    ? kind.cast(new RunCommand(environment))
                    : CommandLine.defaultFactory().create(kind);
        }
    }
}
