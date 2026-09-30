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

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.settings.Settings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The requests RESTest sends to the APIs whose documents ask for a key, when it is given none -
 * pinned, so that reading what a document says about keys can be shown to change nothing else.
 *
 * <p>An API may ask for a key: a document names it, says whether it travels in a header, in the
 * address or in a cookie, and says which operations want it. Reading all of that is only safe if a
 * run that was handed no key goes on sending exactly what it sent before, request for request. This
 * is where that is held, for every document of the corpus that declares a key and for the one that
 * carries a key as an ordinary parameter instead: each operation's likeliest request, then two
 * rounds of ordinary ones, written out as they would go over the wire.
 *
 * <p>The file this compares against was written by the tool as it was before it learned anything
 * about keys. When the two disagree, what this run produced is written beside the build's other
 * output, so a change somebody meant can be looked at line by line and copied over.
 */
class RequestsWithoutAKeyTest {

    /** The number every run here starts from. Any number would do; this one is fixed. */
    private static final long SEED = 20260930L;

    /** How many times round each document's operations the ordinary requests go. */
    private static final int ROUNDS = 2;

    /** Where the requests are pretended to go. Nothing is sent: this only decides the addresses. */
    private static final String BASE = "http://localhost:8080";

    private static final String PINNED = "requests-without-a-key.txt";

    /**
     * The eight documents of the corpus that declare a key the API asks for, and the one that asks
     * for its key as an ordinary parameter and declares none.
     */
    private static final List<String> DOCUMENTS = List.of(
            "community/Amadeus/openapi.yaml",
            "community/BigOven/openapi.yaml",
            "community/BingWebSearch/openapi.yaml",
            "community/DHL/openapi.yaml",
            "community/Graphhopper/openapi.yaml",
            "community/LanguageTool/openapi.json",
            "community/OMDb/openapi.yaml",
            "community/Petstore/openapi.yaml",
            "community/Tumblr/openapi.yaml");

    @Test
    @DisplayName("an API that asks for a key, given none, is sent what it was always sent")
    void a_document_that_asks_for_a_key_is_sent_what_it_always_was() throws IOException {
        List<String> lines = new ArrayList<>();
        for (String document : DOCUMENTS) {
            lines.add("# " + document);
            ApiModel model = TheCorpus.parse(specifications().resolve(document));
            RandomTestCaseGenerator generator = asARunBuildsIt(model);
            List<Operation> operations = generator.testableOperations();
            for (Operation operation : operations) {
                lines.add(written(operation, generator.likeliestRequest(operation)));
            }
            for (int round = 0; round < ROUNDS; round++) {
                for (Operation operation : operations) {
                    lines.add(written(operation, generator.generate(operation)));
                }
            }
        }

        assertThat(String.join("\n", lines) + "\n").isEqualTo(pinned(lines));
    }

    /**
     * The generator a run builds when it is handed nothing but the document: the lists of values
     * RESTest carries, the plan it carries, and the settings it has when nobody has changed one.
     */
    private static RandomTestCaseGenerator asARunBuildsIt(ApiModel model) {
        Dictionaries.Found found = Dictionaries.gather(List.of(), model);
        Campaigns.Found plan = Campaigns.gather(Optional.empty(), model,
                found.dictionaries().stream().map(Dictionary::name).collect(Collectors.toSet()));
        return new RandomTestCaseGenerator(model, SEED, found.dictionaries(), plan.campaign(),
                Settings.defaults());
    }

    /** One request as one line: what would go over the wire, headers and body included. */
    private static String written(Operation operation, Optional<TestCase> generated) {
        if (generated.isEmpty()) {
            return operation.id().value() + " (nothing could be built)";
        }
        HttpRequestRecord request;
        try {
            request = RequestBuilder.build(operation, generated.get(), BASE);
        } catch (IllegalArgumentException cannotBeSent) {
            return operation.id().value() + " (could not be assembled: "
                    + cannotBeSent.getMessage() + ")";
        }
        StringBuilder line = new StringBuilder(request.method().name()).append(' ')
                .append(request.url());
        for (Header header : request.headers()) {
            line.append(" | ").append(header.name()).append(": ").append(header.value());
        }
        request.body().ifPresent(body -> line.append(" | body ")
                .append(new String(body.content(), StandardCharsets.UTF_8)));
        return line.toString();
    }

    /**
     * What the runs are expected to produce, or a failure saying where what they did produce was
     * left.
     *
     * @param actual what these runs produced, written out when it does not match
     */
    private static String pinned(List<String> actual) throws IOException {
        String produced = String.join("\n", actual) + "\n";
        try (InputStream in = RequestsWithoutAKeyTest.class.getResourceAsStream(PINNED)) {
            String expected = in == null
                    ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            if (!produced.equals(expected)) {
                Path written = Path.of("target", PINNED);
                Files.createDirectories(written.getParent());
                Files.writeString(written, produced, StandardCharsets.UTF_8);
            }
            return expected == null ? "(no pinned file; this run's is in " + Path.of("target",
                    PINNED).toAbsolutePath() + ")" : expected;
        }
    }

    /** The corpus, found by walking up from wherever the tests are being run. */
    private static Path specifications() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("restest-spec"))) {
                return candidate.resolve("restest-spec/src/test/resources/specifications");
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not find the checkout above "
                + Path.of("").toAbsolutePath() + ", and the corpus lives in it");
    }
}
