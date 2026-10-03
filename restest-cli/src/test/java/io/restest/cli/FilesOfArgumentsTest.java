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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Files of arguments read the way the command-line framework reads them, word for word.
 *
 * <p>The command reads every file named with {@code @} itself and tells the framework not to, so
 * that each is read once and the words looked through for a key are the words acted on. That is
 * only safe if a file means exactly what it meant when the framework read it. So every case here is
 * read twice, by {@link FilesOfArguments} and by the framework's own reading, and the two lists of
 * words must be the same: comments, quotation marks in the middle of a word, escapes, files inside
 * files, a file that names itself, and a name reached through a link.
 */
class FilesOfArgumentsTest {

    @Test
    @DisplayName("every file reads as the framework reads it")
    void every_file_reads_as_the_framework_reads_it(@TempDir Path directory) throws Exception {
        Path plain = write(directory, "plain", "--budget 30s\n--out results\r\n--store\n");
        Path commented = write(directory, "commented",
                "# where the results go, see @" + directory + "\n--out results # not this @x\n");
        Path quoted = write(directory, "quoted",
                "--out'--authSECRETKEY' \"two words\" 'it''s' --set=\"a b\"c\n");
        Path escaped = write(directory, "escaped", "'a\\tb' \"c\\\\d\" 'e\\'f' \"\\101\"\n");
        Path windows = write(directory, "windows", "\"abc\\\r\ndef\" --x\r\n'g\\\rh'\r\n");
        Path unicode = write(directory, "unicode", "--out résultats ☃ --dictionary=día.yaml\n");
        Path inner = write(directory, "inner", "--seed 7\n@@literal\n@\n@" + directory.resolve(
                "missing") + "\n");
        Path outer = write(directory, "outer", "--budget 1s @" + inner + " -- @" + inner + "\n");
        Path itself = directory.resolve("itself");
        Files.writeString(itself, "--store @" + itself + "\n");
        Path twice = write(directory, "twice", "@" + inner + " @" + inner + "\n");
        Path empty = write(directory, "empty", "");

        Map<String, List<String>> cases = new LinkedHashMap<>();
        cases.put("plain", List.of("run", "@" + plain));
        cases.put("comments", List.of("run", "@" + commented));
        cases.put("quotes", List.of("@" + quoted));
        cases.put("escapes", List.of("@" + escaped));
        cases.put("line breaks a Windows editor writes", List.of("@" + windows));
        cases.put("characters beyond ASCII", List.of("@" + unicode));
        cases.put("files inside files", List.of("@" + outer));
        cases.put("a file named twice in one argument", List.of("@" + twice));
        cases.put("the same file for two arguments", List.of("@" + inner, "@" + inner));
        cases.put("a file that names itself", List.of("@" + itself));
        cases.put("an empty file", List.of("run", "@" + empty, "api.yaml"));
        cases.put("words that only look like files",
                List.of("@@" + plain, "@", "@" + directory.resolve("missing"), "a@b"));
        cases.put("after --", List.of("run", "--", "@" + plain));

        cases.forEach((what, arguments) -> assertThat(ours(arguments)).describedAs(what)
                .isEqualTo(theFrameworks(arguments)));
    }

    @Test
    @DisplayName("a key holding a backslash reaches the command as it was written, when written as the page says")
    void a_key_with_a_backslash_is_written_as_the_page_says(@TempDir Path directory)
            throws Exception {
        // The page's advice for a key in a file of arguments: a backslash outside quotation marks
        // is an ordinary character, and inside them it is written twice.
        Path arguments = write(directory, "arguments",
                "--auth k3y\\path\n--auth 'k3y\\\\path#1 x'\n--auth \"k3y\\\\path\"\n");

        assertThat(ours(List.of("@" + arguments))).isEqualTo(theFrameworks(List.of("@" + arguments)))
                .containsExactly("--auth", "k3y\\path", "--auth", "k3y\\path#1 x", "--auth",
                        "k3y\\path");
    }

    @Test
    @DisplayName("a name reached through a link and back out of it names the file the framework reads")
    void a_link_in_the_name(@TempDir Path directory) throws Exception {
        Path inner = Files.createDirectories(directory.resolve("real").resolve("inner"));
        write(directory.resolve("real"), "x.args", "--beside-the-target\n");
        write(directory, "x.args", "--beside-the-link\n");
        Path link = directory.resolve("link");
        try {
            Files.createSymbolicLink(link, inner);
        } catch (IOException | UnsupportedOperationException notAllowedHere) {
            Assumptions.abort("this machine does not let a test make a link: " + notAllowedHere);
        }
        List<String> arguments = List.of("@" + link + "/../x.args");

        // Which file that is belongs to the operating system: Linux and macOS follow the link and
        // then go up from where it points, Windows takes the .. off the name before it looks at the
        // link. What matters is that both readings open the same one.
        assertThat(ours(arguments)).isEqualTo(theFrameworks(arguments))
                .containsExactly(OS.WINDOWS.isCurrentOs() ? "--beside-the-link"
                        : "--beside-the-target");
    }

    @Test
    @DisplayName("what each file holds is kept with it, so a -- inside one can be told from one typed")
    void each_file_keeps_what_it_holds(@TempDir Path directory) throws Exception {
        Path inner = write(directory, "inner", "--seed 7 --\n");

        List<FilesOfArguments.Given> given = FilesOfArguments.read(
                List.of("run", "@" + inner, "api.yaml"));

        assertThat(given).containsExactly(new FilesOfArguments.Word("run"),
                new FilesOfArguments.ReadFrom(inner.toString(), List.of(
                        new FilesOfArguments.Word("--seed"), new FilesOfArguments.Word("7"),
                        new FilesOfArguments.Word("--"))),
                new FilesOfArguments.Word("api.yaml"));
    }

    @Test
    @DisplayName("a directory, wherever it is named, cannot be read, and the sentence says which")
    void a_directory_cannot_be_read(@TempDir Path directory) throws Exception {
        Path naming = write(directory, "naming", "--budget 1s\n@" + directory + "\n");

        assertThatThrownBy(() -> FilesOfArguments.read(List.of("run", "@" + naming)))
                .isInstanceOf(FilesOfArguments.CannotBeRead.class)
                .hasMessage("@" + directory + " names a file of arguments, and it is a directory");
    }

    private static Path write(Path directory, String name, String text) throws IOException {
        return Files.writeString(directory.resolve(name), text);
    }

    private static List<String> ours(List<String> arguments) {
        try {
            return FilesOfArguments.words(FilesOfArguments.read(arguments));
        } catch (FilesOfArguments.CannotBeRead cannotBeRead) {
            throw new AssertionError(cannotBeRead.getMessage(), cannotBeRead);
        }
    }

    /** The words the framework reads for the same arguments, when it reads the files itself. */
    private static List<String> theFrameworks(List<String> arguments) {
        CommandLine reading = new CommandLine(new TakesAnything())
                .setUnmatchedArgumentsAllowed(true)
                .setUnmatchedOptionsArePositionalParams(true);
        return reading.parseArgs(arguments.toArray(String[]::new)).expandedArgs();
    }

    /** A command that takes any words at all, so that the framework only reads them. */
    @Command(name = "anything")
    static final class TakesAnything {

        @Parameters
        List<String> words = new ArrayList<>();
    }
}
