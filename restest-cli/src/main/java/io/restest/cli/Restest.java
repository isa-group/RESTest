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
import picocli.CommandLine;
import picocli.CommandLine.Command;

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
        CommandLine restest = new CommandLine(new Restest())
                .setOut(out)
                .setErr(err)
                .setCaseInsensitiveEnumValuesAllowed(true)
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
}
