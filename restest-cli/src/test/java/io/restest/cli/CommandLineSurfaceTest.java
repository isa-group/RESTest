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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.restest.core.auth.AuthGiven;
import io.restest.core.settings.SettingsException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * What somebody finds out before running anything: which RESTest this is, how each command is used,
 * and what the number a command ends with means.
 *
 * <p>This is the part of the tool a person reads first and a script depends on longest, so what it
 * promises is checked against what the tool does: every number the help lists is one the command
 * answers, every option it lists says what it does, every example it gives is a command the tool
 * reads, and every variable it names is one the tool looks at. A mistake typed on the command line -
 * a file of arguments that is a directory, say - is answered as a mistake, never as RESTest breaking.
 */
class CommandLineSurfaceTest {

    private final StringWriter screen = new StringWriter();
    private final StringWriter problems = new StringWriter();

    @Nested
    @DisplayName("which RESTest this is")
    class Version {

        @Test
        @DisplayName("restest version names the version on its first line and the Java on its second, and answers 0")
        void the_version_command_names_the_version_and_the_java() {
            assertThat(run("version")).isZero();

            List<String> lines = screen.toString().lines().toList();
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0))
                    .describedAs("a script reading one line reads the version alone")
                    .startsWith("RESTest ")
                    .doesNotContain("Java");
            assertThat(lines.get(1))
                    .startsWith("Java " + System.getProperty("java.version"))
                    .contains(System.getProperty("os.name"))
                    .contains(System.getProperty("os.arch"));
        }

        @Test
        @DisplayName("restest version and restest --version say the same thing")
        void the_command_and_the_option_agree() {
            run("version");
            String command = screen.toString();
            screen.getBuffer().setLength(0);

            assertThat(run("--version")).isZero();

            assertThat(screen.toString()).isEqualTo(command);
        }
    }

    @Nested
    @DisplayName("how each command is used")
    class Help {

        @Test
        @DisplayName("restest help run shows what restest run --help shows")
        void help_for_a_command_is_its_own_help() {
            assertThat(run("run", "--help")).isZero();
            String asked = screen.toString();
            screen.getBuffer().setLength(0);

            assertThat(run("help", "run")).isZero();

            assertThat(screen.toString()).isEqualTo(asked).contains("Usage: restest run");
        }

        @Test
        @DisplayName("help for a command that does not exist answers 2")
        void help_for_no_such_command_answers_two() {
            assertThat(run("help", "nonsense")).isEqualTo(2);
            assertThat(problems.toString()).contains("nonsense");
        }

        @Test
        @DisplayName("restest --help lists every command there is")
        void the_top_level_help_lists_every_command() {
            assertThat(run("--help")).isZero();

            for (String command : commandLine().getSubcommands().keySet()) {
                assertThat(screen.toString()).containsPattern("(?m)^  " + command + " ");
            }
            assertThat(commandLine().getSubcommands().keySet())
                    .containsExactly("run", "version", "help");
        }

        @Test
        @DisplayName("every option of every command says what it does")
        void every_option_says_what_it_does() {
            for (CommandLine command : commands()) {
                for (OptionSpec option : command.getCommandSpec().options()) {
                    assertThat(String.join(" ", option.description()))
                            .describedAs(command.getCommandName() + " " + option.longestName())
                            .isNotBlank();
                }
            }
            assertThat(String.join(" ", run().positionalParameters().get(0).description()))
                    .isNotBlank();
        }

        @Test
        @DisplayName("the exit codes the help lists are exactly the numbers the command answers with")
        void the_exit_codes_listed_are_the_ones_answered() throws IllegalAccessException {
            Map<String, String> listed = run().usageMessage().exitCodeList();

            Map<String, String> answered = new TreeMap<>();
            for (Field field : ExitCode.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    field.setAccessible(true);
                    answered.put(String.valueOf(field.getInt(null)), field.getName());
                }
            }
            assertThat(new TreeMap<>(listed).keySet())
                    .describedAs("a number a script may meet and cannot look up, or one listed that "
                            + "the command never gives, is a promise broken either way")
                    .isEqualTo(answered.keySet());
            assertThat(listed.values()).allSatisfy(meaning -> assertThat(meaning).isNotBlank());

            assertThat(run("run", "--help")).isZero();
            assertThat(screen.toString()).contains("Exit codes")
                    .containsPattern("(?m)^  4 +RESTest itself went wrong")
                    .containsPattern("(?m)^  130 +The run was stopped with Ctrl-C");
        }

        @Test
        @DisplayName("every example the help gives is a command the tool reads, printed whole on one line")
        void every_example_is_a_command_the_tool_reads() throws SettingsException {
            assertThat(run("run", "--help")).isZero();
            List<String> printed = screen.toString().lines().toList();
            screen.getBuffer().setLength(0);
            assertThat(run("--help")).isZero();
            printed = new ArrayList<>(printed);
            printed.addAll(screen.toString().lines().toList());

            List<String> examples = new ArrayList<>();
            for (CommandLine command : List.of(commandLine(), commandLine().getSubcommands()
                    .get("run"))) {
                for (String line : command.getCommandSpec().usageMessage().footer()) {
                    // An example is a line of its own, indented; a sentence that begins with the
                    // tool's name is not one.
                    if (line.startsWith("  restest ")) {
                        examples.add(line);
                    }
                }
            }
            assertThat(examples).hasSizeGreaterThanOrEqualTo(6);
            for (String example : examples) {
                assertThat(printed)
                        .describedAs("an example broken across two lines is copied in halves")
                        .contains(example);
                String[] words = wordsOf(example.trim().substring("restest ".length()))
                        .toArray(String[]::new);
                assertThatCode(() -> commandLine().parseArgs(words))
                        .describedAs(example)
                        .doesNotThrowAnyException();
                // A setting an example names has to be one, or the example teaches a mistake.
                List<String> settings = commandLine().parseArgs(words).subcommands().stream()
                        .flatMap(command -> command.matchedOptionValue("--set",
                                List.<String>of()).stream())
                        .toList();
                SettingsFromEverywhere.gather(Optional.empty(), Map.of(), settings);
            }
        }

        @Test
        @DisplayName("the variables the help names are the ones the tool reads")
        void the_variables_named_are_the_ones_read() throws SettingsException {
            assertThat(run("run", "--help")).isZero();
            String help = screen.toString();

            assertThat(help).contains(AuthGiven.VARIABLE);
            assertThat(help).contains("RESTEST_ENGINE_MAX_CONCURRENCY=1");
            assertThat(SettingsFromEverywhere.gather(Optional.empty(),
                    Map.of("RESTEST_ENGINE_MAX_CONCURRENCY", "1"), List.of())
                    .settings().engine().maxConcurrency())
                    .describedAs("the example of a setting in the environment is one")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("a file of arguments")
    class FilesOfArgumentsNamed {

        @Test
        @DisplayName("a directory named as a file of arguments answers 2 in a sentence, not 4 with a stack trace")
        void a_directory_answers_two(@TempDir Path directory) {
            int answer = run("run", "@" + directory);

            assertThat(answer).isEqualTo(2);
            assertThat(problems.toString())
                    .contains("restest: @" + directory + " names a file of arguments, and it is a "
                            + "directory")
                    .doesNotContain("Exception")
                    .doesNotContain("\tat ");
        }

        @Test
        @DisplayName("so does one named inside another file of arguments, or after --")
        void a_directory_named_anywhere_answers_two(@TempDir Path directory) throws Exception {
            Path arguments = Files.writeString(directory.resolve("arguments"),
                    "--budget 1s\n@" + directory + "\n");

            assertThat(run("run", "@" + arguments)).isEqualTo(2);
            assertThat(run("run", "--", "@" + directory)).isEqualTo(2);
            assertThat(problems.toString()).doesNotContain("\tat ");
        }

        @Test
        @DisplayName("a file of arguments that can be read is read, and @@ is a word that begins with @")
        void a_readable_file_is_read(@TempDir Path directory) throws Exception {
            Path arguments = Files.writeString(directory.resolve("arguments"),
                    "--print-settings\n--set\nengine.maxConcurrency=3\n");

            assertThat(run("run", "@" + arguments)).isZero();
            assertThat(screen.toString()).contains("maxConcurrency: 3");

            // Read as a file, this one would hand the command an option that does not exist. Given
            // to an option that takes any text, since on Windows the word it stands for - an @ and
            // then a drive letter - is no name a directory can have.
            Path notToBeRead = Files.writeString(directory.resolve("not-to-be-read"),
                    "--no-such-option\n");
            assertThat(run("run", "--print-settings", "--url", "@@" + notToBeRead)).isZero();
            assertThat(problems.toString()).isEmpty();
        }

        @Test
        @DisplayName("a comment in a file of arguments is not read, whatever it names")
        void a_comment_is_not_read(@TempDir Path directory) throws Exception {
            Path arguments = Files.writeString(directory.resolve("arguments"),
                    "# the results go in @" + directory + ", see --authoring notes\n"
                            + "--print-settings\n");

            assertThat(run("run", "@" + arguments)).isZero();
            assertThat(problems.toString()).isEmpty();
        }

        @Test
        @DisplayName("a key stuck to --auth in a file named from inside another is refused, and so is one typed after a file that ends its options")
        void a_key_stuck_in_a_file_inside_a_file_is_refused(@TempDir Path directory)
                throws Exception {
            Path inner = Files.writeString(directory.resolve("inner"), "--authSECRETKEY1\n");
            Path outer = Files.writeString(directory.resolve("outer"), "--budget 1s\n@" + inner);
            Path ending = Files.writeString(directory.resolve("ending"), "--budget 1s\n--\n");

            assertThat(run("run", "api.yaml", "@" + outer)).isEqualTo(2);
            assertThat(run("run", "@" + ending, "--authSECRETKEY1")).isEqualTo(2);
            assertThat(problems.toString())
                    .contains("an argument begins with --auth and runs straight on")
                    .doesNotContain("SECRETKEY1");
        }

        @Test
        @DisabledOnOs(OS.WINDOWS)
        // On a thread of its own: a pipe opened with nobody writing to it waits in a way that
        // interrupting the test cannot end, and a reading of it twice would do exactly that.
        @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        @DisplayName("a pipe named as a file of arguments is read once, so its arguments reach the command")
        void a_pipe_is_read_once(@TempDir Path directory) throws Exception {
            // Read twice - once to look for a key, once by the command-line framework - a pipe gave
            // its words to the first reading and left nothing for the second, and the command went
            // ahead without them.
            Path pipe = directory.resolve("arguments");
            assertThat(new ProcessBuilder("mkfifo", pipe.toString()).start().waitFor()).isZero();
            Thread writer = Thread.ofVirtual().start(() -> {
                try {
                    Files.writeString(pipe, "--print-settings\n--set\nengine.maxConcurrency=5\n");
                } catch (java.io.IOException cannotWrite) {
                    throw new java.io.UncheckedIOException(cannotWrite);
                }
            });

            assertThat(run("run", "@" + pipe)).isZero();
            writer.join();

            assertThat(screen.toString()).contains("maxConcurrency: 5");
        }

        @Test
        @DisplayName("a file of arguments that names itself is read once, as the framework reads it")
        void a_file_that_names_itself_is_read_once(@TempDir Path directory) throws Exception {
            Path arguments = directory.resolve("arguments");
            Files.writeString(arguments, "--print-settings\n@" + arguments + "\n");

            assertThat(run("run", "@" + arguments)).isZero();
        }
    }

    /** The words of an example as a shell would hand them over: split at spaces, quotes kept whole. */
    private static List<String> wordsOf(String example) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false;
        for (char character : example.toCharArray()) {
            if (character == '\'') {
                quoted = !quoted;
            } else if (character == ' ' && !quoted) {
                if (!word.isEmpty()) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(character);
            }
        }
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        // Where the output goes is the shell's business, not the command's.
        int redirected = words.indexOf(">");
        return redirected < 0 ? words : words.subList(0, redirected);
    }

    private CommandLine commandLine() {
        return Restest.commandLine(new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()), Map.of());
    }

    private CommandSpec run() {
        return commandLine().getSubcommands().get("run").getCommandSpec();
    }

    private List<CommandLine> commands() {
        List<CommandLine> all = new ArrayList<>(List.of(commandLine()));
        all.addAll(commandLine().getSubcommands().values());
        return all;
    }

    private int run(String... arguments) {
        PrintWriter out = new PrintWriter(screen);
        PrintWriter err = new PrintWriter(problems);
        try {
            return Restest.run(arguments, out, err, Map.of());
        } finally {
            out.flush();
            err.flush();
        }
    }
}
