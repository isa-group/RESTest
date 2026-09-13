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

import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.oracle.Finding;
import io.restest.oracles.ResponseSchemaOracle;
import io.restest.spec.SwaggerSpecificationParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The rule that checks a reply's shape, against the five real APIs the tool is measured on.
 *
 * <p>Hand-written examples test what their author thought of. These five documents were written by
 * other people for their own purposes, and they say things nobody writing a fixture would think to
 * say: one of them declares every single one of its replies once and points at it from everywhere
 * else, three hundred times over, and every one of the five states formats for its dates.
 *
 * <p>What is measured here is reach: how many of each API's operations the rule can find a
 * declared shape for at all. A rule that quietly finds nothing looks exactly like an API with no
 * fault, which is the worst way a testing tool can fail, so the numbers are pinned and a change
 * that lowers one has to be a deliberate act. Whether a reply that matches is left alone is
 * asked of hand-written documents instead, where a matching reply can be written down.
 *
 * <p>Eighty of the eighty-one readable operations across the five are reachable. The one that is
 * not declares that it replies with JSON and then says nothing at all about the shape of it, so
 * there is nothing to hold a reply to - a gap in that document rather than in this rule.
 */
class GoldenCorpusSchemaOracleTest {

    @ParameterizedTest(name = "{0}: a shape is found for {1} of {2} readable operations")
    @CsvSource({
            "flight-search,     23, 23",
            "gestao-hospital,   10, 10",
            "kafka-rest-proxy,  33, 33",
            "notebook-manager,   1,  2",
            "pet-clinic,        13, 13",
    })
    @DisplayName("the shape a real API declares for a reply is found, however the document says it")
    void a_declared_shape_is_found_for_a_real_api(String api, int reachable, int readable) {
        ApiModel model = parse(api);
        ResponseSchemaOracle oracle = new ResponseSchemaOracle();

        List<Operation> readableOperations = readsSomething(model);
        assertThat(readableOperations).extracting(operation -> operation.id().value())
                .describedAs("%s: operations that read and declare a JSON reply", api)
                .hasSize(readable);

        List<String> reached = new ArrayList<>();
        for (Operation operation : readableOperations) {
            // A bare true. Every one of these operations declares an object, a list or a piece of
            // text, so a shape the rule can actually reach will reject it; a shape it cannot reach
            // looks exactly like an API with nothing wrong. That is the difference being measured.
            Interaction nonsense = Replies.of(operation, "true");
            List<Finding> found = oracle.judge(nonsense, model);
            if (!found.isEmpty()) {
                reached.add(operation.id().value());
            }
        }

        assertThat(reached)
                .describedAs("operations whose declared shape could not be reached: %s",
                        readableOperations.stream()
                                .map(operation -> operation.id().value())
                                .filter(id -> !reached.contains(id))
                                .toList())
                .hasSize(reachable);
    }

    @ParameterizedTest(name = "{0} is not complained about for saying what it is")
    @CsvSource({"flight-search", "gestao-hospital", "kafka-rest-proxy", "notebook-manager",
            "pet-clinic"})
    @DisplayName("what a real API complains of is always the shape, never a matter of opinion")
    void the_complaints_are_about_shape_only(String api) {
        ApiModel model = parse(api);
        ResponseSchemaOracle oracle = new ResponseSchemaOracle();

        for (Operation operation : readsSomething(model)) {
            for (Finding finding : oracle.judge(Replies.of(operation, "true"), model)) {
                assertThat(finding.details())
                        .describedAs("%s", operation.id())
                        .allSatisfy(detail -> assertThat(detail)
                                .contains("expected")
                                .doesNotContain("RFC"));
            }
        }
    }

    /** The operations that read rather than write, and that declare a 200 reply of their own. */
    private static List<Operation> readsSomething(ApiModel model) {
        return model.operations().stream()
                .filter(operation -> operation.method() == HttpMethod.GET)
                .filter(operation -> operation.responseFor(200)
                        .flatMap(reply -> reply.declaredContentTypeFor("application/json"))
                        .isPresent())
                .toList();
    }

    private static ApiModel parse(String api) {
        Path document = repositoryRoot()
                .resolve("restest-spec/src/test/resources/specifications/restleague-2027")
                .resolve(api.trim())
                .resolve("openapi.yaml");
        assertThat(document).exists();
        ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
        assertThat(model.document())
                .describedAs("the document is kept, or nothing can be checked against it")
                .isPresent();
        return model;
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
