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
package io.restest.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.SpecificationIssue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that the corpus of real specifications is what it claims to be, and that RESTest can read
 * all of it.
 *
 * <p>Two kinds of check, and they answer different questions. The text ones confirm a fixture still
 * says what its directory name promises, so that a later edit cannot quietly turn the
 * dangling-reference fixture into one whose reference resolves. The reading one points the parser at
 * every document in the corpus and requires it to come back with an answer rather than an exception:
 * never crashing on a bad specification, stated as a test, over forty-odd documents nobody here
 * wrote.
 */
class CorpusSanityTest {

    private static final Pattern VERSION_KEY =
            Pattern.compile("[\"']?(openapi|swagger)[\"']?\\s*:\\s*[\"']?([0-9][\\w.]*)");

    @Test
    @DisplayName("every specification in the corpus declares an OpenAPI or Swagger version")
    void every_document_declares_a_version() throws IOException {
        for (Path file : specificationFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(VERSION_KEY.matcher(content).find())
                    .describedAs("%s should declare an 'openapi' or 'swagger' version key", file)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("exactly one specification in the corpus declares OpenAPI 3.2, the unsupported-version fixture")
    void exactly_one_document_declares_oas_32() throws IOException {
        List<Path> declaring32 = specificationFiles().stream()
                .filter(file -> {
                    String content = readQuietly(file);
                    var matcher = VERSION_KEY.matcher(content);
                    return matcher.find() && matcher.group(2).startsWith("3.2");
                })
                .toList();

        assertThat(declaring32)
                .describedAs("only fixtures/unsupported-version/ should declare OAS 3.2 "
                        + "(ADR-0007, reversed at M1.2: the corpus has no real 3.2 documents)")
                .hasSize(1)
                .allSatisfy(file -> assertThat(file.toString()).contains("unsupported-version"));
    }

    @Test
    @DisplayName("the malformed fixture references a schema that is never defined")
    void malformed_fixture_has_a_dangling_reference() throws IOException {
        Path malformed = specificationsRoot().resolve("fixtures/malformed/openapi.yaml");
        String content = Files.readString(malformed, StandardCharsets.UTF_8);

        assertThat(content)
                .describedAs("the fixture should still reference the undefined schema by name")
                .contains("$ref: \"#/components/schemas/Gadget\"");
        assertThat(content)
                .describedAs("Gadget must stay undefined, or this is no longer a dangling reference")
                .doesNotContain("Gadget:");
    }

    @Test
    @DisplayName("every specification in the corpus is read without throwing, and every real one yields operations")
    void the_whole_corpus_can_be_read() throws IOException {
        SwaggerSpecificationParser parser = new SwaggerSpecificationParser();

        for (Path file : specificationFiles()) {
            ApiModel api = parser.parse(file.toString());

            // Whatever a document turns out to contain, what could not be used has to be sayable:
            // an issue nobody can locate is the same as no issue at all to whoever reads the report.
            assertThat(api.issues())
                    .describedAs("issues reported for %s", file)
                    .allSatisfy(issue -> {
                        assertThat(issue.location()).isNotBlank();
                        assertThat(issue.message()).isNotBlank();
                        assertThat(issue.effect()).isNotNull();
                        if (issue.effect() != SpecificationIssue.Effect.DOCUMENT) {
                            assertThat(issue.operation()).isPresent();
                        }
                    });

            if (isAFixture(file)) {
                // The fixtures are deliberately broken, and two of them yield nothing on purpose.
                continue;
            }
            assertThat(api.operations())
                    .describedAs("%s is a real published specification, so it must yield something "
                            + "to test; yielding nothing means the whole document was lost", file)
                    .isNotEmpty();
        }
    }

    /** Whether this document is one of the hand-written broken ones, rather than a real API's. */
    private static boolean isAFixture(Path file) {
        return file.startsWith(specificationsRoot().resolve("fixtures"));
    }

    /** Every file under {@code specifications/} except the human-facing READMEs. */
    private static List<Path> specificationFiles() throws IOException {
        try (Stream<Path> tree = Files.walk(specificationsRoot())) {
            return tree.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().equals("README.md"))
                    .toList();
        }
    }

    private static Path specificationsRoot() {
        URL resource = CorpusSanityTest.class.getClassLoader().getResource("specifications");
        assertThat(resource).describedAs("the specifications/ test resource directory").isNotNull();
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not resolve specifications/ on the classpath", e);
        }
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
