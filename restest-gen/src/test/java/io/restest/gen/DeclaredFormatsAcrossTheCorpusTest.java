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

import io.restest.core.gen.ValueRequest;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Parameter;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the fifty documents in the corpus say about the kind of text they want, and how much of it
 * RESTest can now act on.
 *
 * <p>Both numbers here are measurements rather than opinions, and both are worth pinning. The first
 * is how much there was to gain: a parameter described as a timestamp used to be sent an ordinary
 * word, and an API told to expect a timestamp refuses one. The second is how much of that is
 * actually taken - a kind of text nobody here has heard of is left alone on purpose, so the gap
 * between the two numbers is the honest measure of what is still missed.
 */
class DeclaredFormatsAcrossTheCorpusTest {

    /**
     * The kinds of text the five APIs the tool is measured on ask their callers for, in a
     * parameter: none at all.
     *
     * <p>Not an oversight in those documents. All five describe dates and identifiers, and every
     * one of those descriptions sits in a shape shared between a request body and a reply rather
     * than on a parameter. Nothing sends a request body yet, so nothing in these five reaches this
     * source today - and everything in them will, the moment bodies are built, because a value
     * nested inside a body is asked of the same sources in the same order.
     *
     * <p>Pinned at nothing on purpose: the day one of these documents describes a parameter as a
     * date, this test fails and somebody gets to notice that the numbers have changed.
     */
    private static final Map<String, Integer> IN_THE_PRIORITY_CORPUS = Map.of();

    @Test
    @DisplayName("the kinds of text the priority corpus asks for are the ones measured")
    void the_priority_corpus_asks_for_what_it_is_known_to_ask_for() {
        Map<String, Integer> found = new TreeMap<>();
        for (Path document : priorityCorpus()) {
            ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
            for (Parameter parameter : parametersOf(model).toList()) {
                formatOf(resolved(model, parameter.schema()))
                        .ifPresent(format -> found.merge(format, 1, Integer::sum));
            }
        }

        assertThat(found)
                .describedAs("a document in the priority corpus gaining or losing a declared kind "
                        + "of text changes what a run of it can send")
                .containsExactlyInAnyOrderEntriesOf(IN_THE_PRIORITY_CORPUS);
    }

    @Test
    @DisplayName("every kind of text the whole corpus asks for is either built or knowingly left")
    void what_the_corpus_asks_for_is_either_built_or_knowingly_left() {
        Map<String, Integer> built = new TreeMap<>();
        Map<String, Integer> left = new TreeMap<>();
        for (Path document : corpus()) {
            ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
            for (Parameter parameter : parametersOf(model).toList()) {
                formatOf(resolved(model, parameter.schema())).ifPresent(format -> {
                    boolean answered = new FormatValueProvider(Schemas.fixedRandom())
                            .offer(asking(parameter, resolved(model, parameter.schema())))
                            .isPresent();
                    (answered ? built : left).merge(format, 1, Integer::sum);
                });
            }
        }

        // Written out rather than computed, because a computed expectation would agree with
        // whatever the code does. Twenty-three parameters in fifty documents: the whole of what a
        // declared kind of text is worth today, before request bodies are built.
        assertThat(built).describedAs("the kinds of text a run now sends properly")
                .containsExactlyInAnyOrderEntriesOf(Map.of("date", 8, "date-time", 14,
                        "password", 1));
        // Both of these are one document's own word for something, not a kind of text anybody
        // could build a value for. Leaving them to invention is the right answer, not a gap.
        assertThat(left).describedAs("a name RESTest would be guessing at rather than building")
                .containsExactlyInAnyOrderEntriesOf(Map.of("Integer", 1, "string", 1));
    }

    @Test
    @DisplayName("no value built for a declared kind of text is one its own document refuses")
    void every_value_built_is_one_its_shape_accepts() {
        List<String> refused = new ArrayList<>();
        for (Path document : corpus()) {
            ApiModel model = new SwaggerSpecificationParser().parse(document.toString());
            for (Parameter parameter : parametersOf(model).toList()) {
                CanonicalSchema shape = resolved(model, parameter.schema());
                if (formatOf(shape).isEmpty()) {
                    continue;
                }
                // Several draws: a shape is refused by one value in a run, not by all of them.
                FormatValueProvider provider = new FormatValueProvider(Schemas.fixedRandom());
                for (int draw = 0; draw < 20; draw++) {
                    provider.offer(asking(parameter, shape)).ifPresent(built ->
                            SchemaSatisfaction.violations(built.value(), shape, model).forEach(
                                    complaint -> refused.add(document.getParent().getFileName()
                                            + " " + parameter.name() + ": " + complaint)));
                }
            }
        }

        assertThat(refused)
                .describedAs("a value RESTest built for a kind of text its document named, and "
                        + "that same document refuses, would be sent while claiming to be believed in")
                .isEmpty();
    }

    private static Optional<String> formatOf(CanonicalSchema schema) {
        return schema instanceof StringSchema text ? text.format() : Optional.empty();
    }

    private static ValueRequest asking(Parameter parameter, CanonicalSchema schema) {
        return ValueRequest.of(io.restest.core.model.OperationId.of("GET /x"), parameter.name(),
                parameter.location(), schema);
    }

    private static CanonicalSchema resolved(ApiModel model, CanonicalSchema schema) {
        return schema instanceof SchemaReference reference
                ? model.schema(reference.name()).orElse(schema)
                : schema;
    }

    private static Stream<Parameter> parametersOf(ApiModel model) {
        return model.operations().stream().flatMap(operation -> operation.parameters().stream());
    }

    private static List<Path> priorityCorpus() {
        return corpus().stream()
                .filter(path -> path.toString().contains("restleague-2027"))
                .toList();
    }

    private static List<Path> corpus() {
        try (Stream<Path> tree = Files.walk(specifications())) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .matches("openapi\\.(yaml|yml|json)"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path specifications() {
        return repositoryRoot().resolve("restest-spec/src/test/resources/specifications");
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.exists(here.resolve("ROADMAP.md"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("the repository root is not above the working directory");
        }
        return here;
    }

}
