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

import java.io.PrintWriter;
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
 */
@Command(
        name = "restest",
        description = "Black-box testing for REST APIs, from an OpenAPI document.",
        mixinStandardHelpOptions = true,
        versionProvider = ToolVersion.class,
        subcommands = RunCommand.class,
        synopsisSubcommandLabel = "COMMAND")
public final class Restest {

    private Restest() {
    }

    /**
     * Runs a command and ends the program with what it answered.
     *
     * @param arguments what was typed after {@code restest}
     */
    public static void main(String[] arguments) {
        System.exit(run(arguments));
    }

    /**
     * Runs a command and answers, without ending anything.
     *
     * <p>The answer is 0 when the run found nothing wrong, 1 when it found a fault, 2 when the
     * command line was wrong, 3 when there was nothing to test, and 4 when RESTest itself went
     * wrong.
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
                .setExecutionExceptionHandler((failure, command, parsed) -> {
                    // Anything that reaches here is RESTest going wrong rather than the API under
                    // test, and the two must not answer the same. The stack trace goes out because
                    // somebody has to be able to report it.
                    err.println("restest: the run could not be completed: " + failure);
                    failure.printStackTrace(err);
                    return ExitCode.TOOL_FAILED;
                });
        // With no sub-command there is nothing to run; saying so and showing what the choices are
        // beats a silent success.
        if (arguments.length == 0) {
            restest.usage(err);
            return ExitCode.BAD_COMMAND_LINE;
        }
        return restest.execute(arguments);
    }

    /**
     * Says what was wrong with what was typed, the way the command-line framework would, except for
     * one thing: an argument it did not understand is not repeated back.
     *
     * <p>A mistyped option leaves whatever followed it as something nobody asked for, and what
     * follows {@code --auth} is a key. Repeating it would put the key on the screen, and into any
     * log the screen is kept in, for the sake of a typing mistake. The names of options that do not
     * exist are still said, up to any {@code =}, since those are what somebody needs to see to put
     * the mistake right; and the options that do exist and look like them are suggested as before.
     */
    private static int saysWhatWasWrong(ParameterException wrong, String[] arguments) {
        CommandLine command = wrong.getCommandLine();
        PrintWriter err = command.getErr();
        if (wrong instanceof UnmatchedArgumentException unmatched) {
            List<String> options = unmatched.getUnmatched().stream()
                    .filter(argument -> argument.startsWith("--"))
                    .map(argument -> argument.contains("=")
                            ? argument.substring(0, argument.indexOf('=')) : argument)
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
            err.println(wrong.getMessage());
            command.usage(err, command.getColorScheme());
        }
        return command.getCommandSpec().exitCodeOnInvalidInput();
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
