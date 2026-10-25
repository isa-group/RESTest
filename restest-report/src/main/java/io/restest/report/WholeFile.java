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
package io.restest.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Writes one of a run's report files so that a file with its name is always a whole one.
 *
 * <p>A report is written in full beside the file it becomes, under a name of its own, and then
 * given its name in one step. A program ended outright while it was writing - a run stopped from
 * outside that ran out of time, say - leaves that other file behind instead, and never half a
 * report under the name a reader looks for. {@link JsonReport} and {@link HtmlReport} write their
 * files this way.
 */
final class WholeFile {

    /** What goes into a file, written to whatever it is handed. */
    @FunctionalInterface
    interface Content {
        void writeTo(Writer out) throws IOException;
    }

    private WholeFile() {
    }

    /** Where a file is written before it is given its own name: beside it, with a word added. */
    static Path whileBeingWritten(Path file) {
        Objects.requireNonNull(file, "file");
        return file.resolveSibling(file.getFileName() + ".partial");
    }

    /**
     * Writes the file whole, or leaves nothing of this attempt behind.
     *
     * @param file where it goes
     * @param what what it is, in words, for the message if it cannot be written
     * @param content what goes into it
     * @throws UncheckedIOException if it could not be written
     */
    static void write(Path file, String what, Content content) {
        Path partial = whileBeingWritten(file);
        try {
            try (Writer out = Files.newBufferedWriter(partial)) {
                content.writeTo(out);
            }
            try {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notInOneStep) {
                // A file system that cannot rename in one step still renames: the file is then
                // whole or absent everywhere but in the instant of the rename.
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(partial);
            } catch (IOException alsoNot) {
                e.addSuppressed(alsoNot);
            }
            throw new UncheckedIOException(what + " could not be written to " + file, e);
        }
    }
}
