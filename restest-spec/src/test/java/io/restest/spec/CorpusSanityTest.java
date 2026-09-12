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
 * Checks the claims this module's Javadoc-free test fixtures make about themselves, without parsing
 * OpenAPI at all: no {@code SpecificationParser} exists yet (that is the next increment), so this
 * only reads the corpus as plain text. It exists so a later edit to a fixture — accidental or
 * otherwise — cannot quietly stop that fixture from being what its directory name and the corpus
 * README say it is.
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
