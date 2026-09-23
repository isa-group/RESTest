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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The first round of a run, over every document in the corpus rather than the five the tool is
 * measured on.
 *
 * <p>A first round that skipped an operation, or sent one twice, would say it had sent every
 * operation once when it had not. And a request that is supposed to be the one an API is most likely
 * to accept is no use if it cannot even be put together into something that could be sent. Fifty
 * documents written by other people are where both of those would show.
 */
class OpeningLapAcrossTheCorpusTest {

    @Test
    @DisplayName("in every document, the first round sends every operation that can be attempted "
            + "exactly once")
    void every_operation_is_in_the_round_exactly_once() {
        int documents = 0;
        int operations = 0;
        for (Path document : corpus()) {
            RandomTestCaseGenerator generator =
                    new RandomTestCaseGenerator(parse(document), 20260923L);
            List<Operation> testable = generator.testableOperations();
            List<Operation> inTheRound = OpeningLap.steps(testable).stream()
                    .flatMap(List::stream).toList();

            assertThat(inTheRound)
                    .describedAs("%s", document)
                    .containsExactlyInAnyOrderElementsOf(testable)
                    .doesNotHaveDuplicates();
            documents++;
            operations += testable.size();
        }

        assertThat(documents).describedAs("documents read").isEqualTo(46);
        assertThat(operations).describedAs("operations that can be attempted").isPositive();
    }

    @Test
    @DisplayName("in every document, the request each operation is most likely to accept can be "
            + "built and put together into something that could be sent")
    void every_likeliest_request_can_be_sent() {
        List<String> couldNotBeBuilt = new ArrayList<>();
        List<String> couldNotBeSent = new ArrayList<>();
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260923L);
            for (Operation operation : generator.testableOperations()) {
                Optional<TestCase> likeliest = generator.likeliestRequest(operation);
                String where = document.getParent().getFileName() + " " + operation.id().value();
                if (likeliest.isEmpty()) {
                    couldNotBeBuilt.add(where);
                    continue;
                }
                try {
                    RequestBuilder.build(operation, likeliest.get(), "http://localhost:8080");
                } catch (IllegalArgumentException cannotBeSent) {
                    couldNotBeSent.add(where + ": " + cannotBeSent.getMessage());
                }
            }
        }

        assertThat(couldNotBeBuilt).isEmpty();
        assertThat(couldNotBeSent).isEmpty();
    }

    @Test
    @DisplayName("and it carries every parameter the API requires and none it does not")
    void the_likeliest_request_carries_exactly_what_is_required() {
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260923L);
            for (Operation operation : generator.testableOperations()) {
                TestCase likeliest = generator.likeliestRequest(operation).orElseThrow();
                List<String> sent = likeliest.parameterValues().stream()
                        .map(value -> value.location() + " " + value.name()).toList();
                List<String> required = operation.parameters().stream()
                        .filter(parameter -> parameter.required())
                        .map(parameter -> parameter.location() + " " + parameter.name()).toList();

                assertThat(sent)
                        .describedAs("%s %s", document.getParent().getFileName(), operation.id())
                        .containsExactlyInAnyOrderElementsOf(required);
                if (operation.requestBody().isPresent()
                        && RequestBuilder.mediaTypeToSend(operation.requestBody().get())
                                .isPresent()) {
                    assertThat(likeliest.body())
                            .describedAs("%s %s declares a body it can be sent",
                                    document.getParent().getFileName(), operation.id())
                            .isPresent();
                }
            }
        }
    }

    private static ApiModel parse(Path document) {
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /** The corpus, without the deliberately broken documents kept beside it. */
    private static List<Path> corpus() {
        try (Stream<Path> tree = Files.walk(repositoryRoot()
                .resolve("restest-spec/src/test/resources/specifications"))) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .matches("openapi\\.(yaml|yml|json)"))
                    .filter(path -> !path.toString().contains("fixtures"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The checkout, found by walking up from wherever the tests are being run. */
    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("restest-spec"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not find the checkout above "
                + Path.of("").toAbsolutePath() + ", and the corpus lives in it");
    }
}
