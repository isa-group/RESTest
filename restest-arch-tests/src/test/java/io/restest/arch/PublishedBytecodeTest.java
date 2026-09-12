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
package io.restest.arch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that the bytecode the build actually produces is Java 21, which is the whole of ADR-0003's
 * promise to library consumers.
 *
 * <p>The promise is not abstract. A project on Java 21 cannot load a class compiled for 25 no matter
 * what that class does, so {@code maven.compiler.release=21} is the single setting standing between
 * RESTest and a large share of its potential users. A setting is easy to lose in a POM edit; the
 * class file header is the evidence.
 *
 * <p>Module descriptors are excluded for the reason given on {@link #MODULE_DESCRIPTOR}, so until
 * M1.1a - when the domain model became the first compiled code in the reactor - there was nothing
 * to measure and the scan reported as skipped rather than passing over an empty set. It now reads
 * real classes. The instrument it reads them with is itself calibrated by
 * {@link #reads_the_major_version_from_a_class_file_header()}, which checks the header reader
 * against bytes whose version is known.
 */
class PublishedBytecodeTest {

    /** Class file major version 65 is Java 21. See JVMS 4.1. */
    private static final int JAVA_21 = 65;

    /**
     * Excluded deliberately, after checking what javac actually does.
     *
     * <p>{@code javac --release 21} emits {@code module-info.class} at the class file version of the
     * compiling JDK, not of the release target: built on JDK 25 it comes out as major version 69
     * even though every other class in the same invocation comes out as 65. This is javac's
     * behaviour, not a misconfiguration, and it is harmless - a JDK 21 runtime resolves such a
     * module, compiles against it and runs code from it, because the module system reads descriptors
     * leniently. Asserting 65 here would therefore fail the build over something correct.
     *
     * <p>Verified rather than assumed: a module compiled on JDK 25 with {@code --release 21} was run
     * on JDK 21 as a named module during M0.2.
     */
    private static final String MODULE_DESCRIPTOR = "module-info.class";

    @Test
    @DisplayName("every published class file is Java 21 bytecode (ADR-0003)")
    void every_published_class_file_is_java_21_bytecode() {
        List<Path> classFiles = publishedClassFiles();

        assertThat(classFiles)
                .describedAs("Nothing was examined, so a pass here would read as evidence that the "
                        + "published bytecode is Java 21 when nothing was measured. Since M1.1a the "
                        + "domain model is compiled, so an empty scan means the reactor was not "
                        + "built rather than that there is nothing to scan")
                .isNotEmpty();

        Map<String, Integer> wrongVersion = new LinkedHashMap<>();
        for (Path classFile : classFiles) {
            int major = majorVersionOf(classFile);
            if (major != JAVA_21) {
                wrongVersion.put(RepositoryRoot.locate().relativize(classFile).toString(), major);
            }
        }

        assertThat(wrongVersion)
                .describedAs("A consumer on Java 21 cannot load a class compiled for a later "
                        + "release, whatever the class does. Class files examined: %d",
                        classFiles.size())
                .isEmpty();
    }

    /**
     * Calibrates the header reader, so that the scan above is not the only thing standing behind
     * ADR-0003's claim while the modules are still empty.
     *
     * <p>A class file header is the magic number, then the minor version, then the major version -
     * six bytes, which is little enough to write out and know the answer in advance.
     */
    @Test
    @DisplayName("the header reader returns the major version a class file declares")
    void reads_the_major_version_from_a_class_file_header() throws IOException {
        assertThat(majorVersionOf(classFileHeader(JAVA_21))).isEqualTo(JAVA_21);
        assertThat(majorVersionOf(classFileHeader(69))).isEqualTo(69);

        Path notAClassFile = Files.createTempFile("not-a-class", ".bin");
        Files.write(notAClassFile, new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        assertThatThrownBy(() -> majorVersionOf(notAClassFile))
                .describedAs("a file that is not a class file must be reported, not read as "
                        + "whatever its fourth and fifth bytes happen to be")
                .isInstanceOf(AssertionError.class);
    }

    /** Six bytes: the class file magic number, minor version 0, then {@code major}. */
    private static Path classFileHeader(int major) throws IOException {
        Path file = Files.createTempFile("header-" + major, ".class");
        ByteBuffer header = ByteBuffer.allocate(8)
                .putInt(0xCAFEBABE)
                .putShort((short) 0)
                .putShort((short) major);
        Files.write(file, header.array());
        return file;
    }

    /** Every compiled class in a production module, excluding module descriptors. */
    private static List<Path> publishedClassFiles() {
        Path root = RepositoryRoot.locate();
        List<Path> classes = new ArrayList<>();
        for (String module : RepositoryRoot.declaredModules()) {
            Path output = root.resolve(module).resolve("target/classes");
            if (!Files.isDirectory(output)) {
                // A module with no main sources, or a reactor that has not been built yet.
                continue;
            }
            try (Stream<Path> tree = Files.walk(output)) {
                tree.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().equals(MODULE_DESCRIPTOR))
                        .forEach(classes::add);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not walk " + output, e);
            }
        }
        return classes;
    }

    /** The major version from a class file header: magic, then minor, then major. */
    private static int majorVersionOf(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile);
                DataInputStream data = new DataInputStream(in)) {
            int magic = data.readInt();
            assertThat(magic)
                    .describedAs("%s does not start with the class file magic number", classFile)
                    .isEqualTo(0xCAFEBABE);
            data.readUnsignedShort();
            return data.readUnsignedShort();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the header of " + classFile, e);
        }
    }
}
