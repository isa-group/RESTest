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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads every file of arguments on the command line, once, so that the command is handed the words
 * they hold rather than their names.
 *
 * <p>A person can keep a long command in a file and type {@code restest run @its-name}: the file's
 * words are taken as if they had been typed there, and a file may name another one the same way. The
 * command-line framework can do that reading itself, and is told not to, for three reasons. A file
 * it tries to read and cannot - a directory named by mistake - made it fail in a way that reads as
 * RESTest breaking, with a stack trace, where the mistake is in what was typed. The command looks
 * through those words for a key typed where it would be repeated back, and had to read each file a
 * second time to do it - which empties a pipe before the framework gets to it, and could disagree
 * with the framework about what a file says. And one reading, made here, is the only way the words
 * looked through and the words acted on are certain to be the same.
 *
 * <p>The reading follows the framework's own rules exactly, so that a file means what it always
 * meant: words are separated by spaces and line breaks, a quotation mark - single or double - keeps
 * a word whole and understands a backslash inside it, and {@code #} begins a comment to the end of
 * the line. {@code @@} stands for a word that begins with {@code @}, a lone {@code @} is a word like
 * any other, a name that is not a file the program may read is a word as it is, and a file already
 * read for one argument is not read again for it. Files are read wherever they are named, after
 * {@code --} as well.
 */
final class FilesOfArguments {

    private FilesOfArguments() {
    }

    /** One argument as it was given: a word, or a file of arguments with what it holds. */
    sealed interface Given permits Word, ReadFrom {
    }

    /**
     * A word, typed or found in a file.
     *
     * @param text the word
     */
    record Word(String text) implements Given {
    }

    /**
     * A file of arguments, and what it holds.
     *
     * @param name the file, as it was named after the {@code @}
     * @param holds its words and the files it names, in order
     */
    record ReadFrom(String name, List<Given> holds) implements Given {

        ReadFrom {
            holds = List.copyOf(holds);
        }
    }

    /** A file of arguments that is there, and could not be read. */
    static final class CannotBeRead extends Exception {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        CannotBeRead(String said) {
            super(said, null, false, false);
        }
    }

    /**
     * Every argument, with each file of arguments read.
     *
     * @param arguments what was typed
     * @return each argument as it was given, the files with what they hold
     * @throws CannotBeRead when a file named with {@code @} is there and cannot be read, with a
     *     sentence saying which and why
     */
    static List<Given> read(List<String> arguments) throws CannotBeRead {
        List<Given> given = new ArrayList<>();
        for (String argument : arguments) {
            // A fresh memory of the files read for each argument, and never forgotten while it is
            // read: the framework's rule, which reads a file named twice in one argument once.
            given.addAll(readOne(argument, new HashSet<>()));
        }
        return given;
    }

    /**
     * The words the command is handed: every file of arguments in the place of its name.
     *
     * @param given each argument as it was given
     * @return the words, in order
     */
    static List<String> words(List<Given> given) {
        List<String> words = new ArrayList<>();
        for (Given argument : given) {
            switch (argument) {
                case Word word -> words.add(word.text());
                case ReadFrom file -> words.addAll(words(file.holds()));
            }
        }
        return words;
    }

    private static List<Given> readOne(String argument, Set<String> alreadyRead)
            throws CannotBeRead {
        if (argument.length() < 2 || !argument.startsWith("@")) {
            return List.of(new Word(argument));
        }
        String name = argument.substring(1);
        if (name.startsWith("@")) {
            return List.of(new Word(name));
        }
        File file = new File(name);
        // The framework's own test: a name it may not read is a word as it is, which is how an
        // argument that happens to begin with @ reaches the command as it was typed.
        if (!file.canRead()) {
            return List.of(new Word(argument));
        }
        if (!alreadyRead.add(file.getAbsolutePath())) {
            return List.of();
        }
        List<Given> holds = new ArrayList<>();
        for (String word : wordsIn(name, file)) {
            holds.addAll(readOne(word, alreadyRead));
        }
        return List.of(new ReadFrom(name, holds));
    }

    /** The words of one file, split exactly where the framework splits them. */
    private static List<String> wordsIn(String name, File file) throws CannotBeRead {
        if (file.isDirectory()) {
            throw new CannotBeRead("@" + name + " names a file of arguments, and it is a directory");
        }
        // Read by the name as typed, in the characters the program reads text in, through a reader
        // that makes every line break one character, as the framework reads it: the same file,
        // however the name reaches it, and the same words, whichever editor wrote it.
        try (Reader reader = new LineNumberReader(new FileReader(file))) {
            StreamTokenizer words = new StreamTokenizer(reader);
            words.resetSyntax();
            words.wordChars(' ', 255);
            words.whitespaceChars(0, ' ');
            words.quoteChar('"');
            words.quoteChar('\'');
            words.commentChar('#');
            List<String> found = new ArrayList<>();
            while (words.nextToken() != StreamTokenizer.TT_EOF) {
                found.add(words.sval);
            }
            return found;
        } catch (IOException | RuntimeException unreadable) {
            throw new CannotBeRead("@" + name + " names a file of arguments, and it cannot be read: "
                    + unreadable.getMessage());
        }
    }
}
