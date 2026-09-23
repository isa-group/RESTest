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

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.RequestBodyModel;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * What the corpus actually asks of a tool that sends request bodies, and what this one answers.
 *
 * <p>A quarter of every API in the corpus is behind a request body: the operations that create
 * things, update them and search with a payload. Until bodies were built, every one of those that
 * insisted on a body was skipped and reported as untestable, which is the largest single gap the
 * tool has had.
 *
 * <p>Three things are pinned here, all measurements rather than opinions.
 *
 * <p>The first is the size of the subject: how many operations take a body at all, and how many of
 * them refuse a request without one. The second is what those bodies are offered as, because it is
 * what decides how much work is worth doing: JSON and web forms between them cover all but a
 * handful, and the handful is named one operation at a time rather than counted. The third is the
 * one that could go wrong quietly - that no operation anywhere in the corpus is now turned away for
 * want of a body, which is what this increment exists to end. Not one is, the two unwritable bodies
 * included: both of those documents say the body may be left out, so those operations are tested
 * without one.
 *
 * <p>One more count is pinned because it is zero. The client that sends requests refuses to build a
 * {@code GET} or a {@code HEAD} carrying a body, so an operation that insists on one there is named
 * as untestable rather than tried for the whole run. No operation in the corpus even offers a body
 * on either method, so that rule has never had a case here to act on - and a document that adds one
 * will say so by changing the number.
 */
class RequestBodiesAcrossTheCorpusTest {

    /**
     * The operations whose body the tool cannot write, one line each.
     *
     * <p>Two of them, out of the corpus's 340 operations that take a body: one takes a file, the
     * other takes a page of text. Neither is JSON and neither is a web form. An operation whose
     * document leaves the media type open - {@code *&#47;*}, which one more operation does - is not
     * among them, because a range that includes JSON is answered with JSON.
     */
    private static final List<String> CANNOT_BE_WRITTEN = List.of(
            "GitHub markdown/render-raw -> [text/plain, text/x-markdown]",
            "Petstore uploadFile -> [multipart/form-data]");

    @Test
    @DisplayName("the corpus asks for the number of bodies it is known to ask for")
    void the_size_of_the_subject_is_what_it_was_measured_to_be() {
        int withABody = 0;
        int required = 0;
        for (Path document : corpus()) {
            for (Operation operation : parse(document).operations()) {
                if (operation.requestBody().isEmpty()) {
                    continue;
                }
                withABody++;
                if (operation.requiresBody()) {
                    required++;
                }
            }
        }

        assertThat(withABody)
                .describedAs("operations of the corpus that take a request body")
                .isEqualTo(340);
        assertThat(required)
                .describedAs("of those, the ones that refuse a request without one - every one of "
                        + "which used to be reported as untestable")
                .isEqualTo(103);
    }

    @Test
    @DisplayName("every body in the corpus can be written, bar the two that are named")
    void the_bodies_that_cannot_be_written_are_the_two_measured() {
        List<String> refused = new ArrayList<>();
        for (Path document : corpus()) {
            for (Operation operation : parse(document).operations()) {
                RequestBodyModel body = operation.requestBody().orElse(null);
                if (body != null && RequestBuilder.mediaTypeToSend(body).isEmpty()) {
                    refused.add(document.getParent().getFileName() + " " + operation.id().value()
                            + " -> " + body.mediaTypes());
                }
            }
        }

        assertThat(refused)
                .describedAs("a body offered only as something this cannot write is named here, "
                        + "and XML is not among them: no operation in the corpus offers XML "
                        + "without offering JSON beside it")
                .containsExactlyInAnyOrderElementsOf(CANNOT_BE_WRITTEN);
    }

    @Test
    @DisplayName("not one operation in the corpus is turned away for want of a request body")
    void a_body_is_no_longer_a_reason_to_skip_an_operation() {
        Map<String, String> turnedAway = new LinkedHashMap<>();
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260918L);
            for (Map.Entry<OperationId, String> refused
                    : generator.untestableOperations().entrySet()) {
                if (refused.getValue().contains("request body")) {
                    turnedAway.put(document.getParent().getFileName() + " "
                            + refused.getKey().value(), refused.getValue());
                }
            }
        }

        assertThat(turnedAway)
                .describedAs("103 operations used to be turned away for this reason. Even the two "
                        + "bodies that cannot be written cost nothing, because both documents say "
                        + "that body may be left out - so those operations are tested without one, "
                        + "which is a request their own authors called legitimate")
                .isEmpty();
    }

    @Test
    @DisplayName("no operation in the corpus insists on a body on a GET or a HEAD")
    void no_operation_asks_for_a_body_on_a_get_or_a_head() {
        int looked = 0;
        List<String> offering = new ArrayList<>();
        List<String> insisting = new ArrayList<>();
        List<String> turnedAway = new ArrayList<>();
        for (Path document : corpus()) {
            ApiModel model = parse(document);
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, 20260918L);
            for (Operation operation : model.operations()) {
                String named = document.getParent().getFileName() + " " + operation.id().value();
                boolean noRoom = operation.method() == HttpMethod.GET
                        || operation.method() == HttpMethod.HEAD;
                if (noRoom) {
                    looked++;
                }
                if (noRoom && operation.requestBody().isPresent()) {
                    offering.add(named);
                    if (operation.requiresBody()) {
                        insisting.add(named);
                    }
                }
                if (generator.untestableOperations().getOrDefault(operation.id(), "")
                        .contains("RESTest cannot send one with a")) {
                    turnedAway.add(named);
                }
            }
        }

        assertThat(looked)
                .describedAs("the GET and HEAD operations of the corpus, every one of them looked "
                        + "at - without this, the counts below would read the same if nothing had "
                        + "been read at all")
                .isEqualTo(792);
        assertThat(turnedAway)
                .describedAs("the operations turned away because RESTest cannot send a body with "
                        + "their method are exactly the ones that insist on one there")
                .containsExactlyElementsOf(insisting);
        assertThat(insisting)
                .describedAs("a GET or a HEAD that insists on a body - none in the corpus, measured "
                        + "when the rule that names one was written")
                .isEmpty();
        assertThat(offering)
                .describedAs("and none that merely accepts one either, so the corpus cannot show "
                        + "what happens to a body such a request may leave out")
                .isEmpty();
    }

    private static ApiModel parse(Path document) {
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /**
     * The corpus, without the deliberately broken documents kept beside it.
     *
     * <p>Those exist to be unreadable, and counting the bodies of a document nobody can parse would
     * measure the fixtures rather than the field.
     */
    private static List<Path> corpus() {
        try (Stream<Path> tree = Files.walk(specifications())) {
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

    private static Path specifications() {
        return repositoryRoot().resolve("restest-spec/src/test/resources/specifications");
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
