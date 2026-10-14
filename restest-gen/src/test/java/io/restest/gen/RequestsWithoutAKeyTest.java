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

import io.restest.core.auth.CredentialPlan;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.settings.Settings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The requests RESTest sends to an API whose document declares a key, or carries one, when it is
 * given none: exactly the requests it would send if the document said nothing about keys at all.
 *
 * <p>An API may ask for a key: a document names it, says whether it travels in a header, in the
 * address or in a cookie, and says which operations want it. Reading all of that is only safe if a
 * run that was handed no key goes on sending what it would send had none of it been written,
 * request for request. This is where that is held, for every document of the corpus that declares
 * a key and for the one that carries a key as an ordinary parameter instead.
 *
 * <p>Each document is read twice: as it is, and with everything it says about keys taken out. A
 * generator is built from each the way a run builds one when it is handed no key, from the same
 * number, and the two are asked for the same things - each operation's likeliest request, then two
 * rounds of ordinary ones - written out as they would go over the wire. Whatever else changes in
 * how requests are built, the two must agree, so nothing here has to be written again when it does.
 */
class RequestsWithoutAKeyTest {

    /** The number every run here starts from. Any number would do; this one is fixed. */
    private static final long SEED = 20260930L;

    /** How many times round each document's operations the ordinary requests go. */
    private static final int ROUNDS = 2;

    /** Where the requests are pretended to go. Nothing is sent: this only decides the addresses. */
    private static final String BASE = "http://localhost:8080";

    /**
     * The eight documents of the corpus that declare a key - two of which, BigOven's and Tumblr's,
     * never ask for it on any operation - and the one that asks for its key as an ordinary
     * parameter and declares none.
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
    @DisplayName("an API that asks for a key, given none, is sent what it would be sent if its "
            + "document said nothing about keys")
    void a_document_that_asks_for_a_key_is_sent_what_a_silent_one_would_be() {
        int declaringAKey = 0;
        int compared = 0;
        for (String document : DOCUMENTS) {
            ApiModel model = TheCorpus.parse(specifications().resolve(document));
            if (!model.securitySchemes().isEmpty()) {
                declaringAKey++;
            }
            CredentialPlan.Found noKey = CredentialPlan.gather(List.of(), model, false);
            assertThat(noKey.refusals()).describedAs("%s", document).isEmpty();

            List<String> asWritten = requests(noKey.plan().modelToFillIn(model));
            List<String> saysNothing = requests(withoutKeys(model));

            assertThat(asWritten)
                    .describedAs("%s, read with what it says about keys and without it", document)
                    .isEqualTo(saysNothing);
            compared += asWritten.size();
        }

        assertThat(declaringAKey)
                .describedAs("the premise: the documents compared do say something about keys, so "
                        + "the two readings of each are not the same reading twice")
                .isEqualTo(8);
        assertThat(compared).describedAs("requests compared").isPositive();
    }

    /** The same API, with everything its document says about keys taken out. */
    private static ApiModel withoutKeys(ApiModel model) {
        List<Operation> operations = model.operations().stream()
                .map(operation -> new Operation(operation.id(), operation.method(),
                        operation.path(), operation.servers(), operation.parameters(),
                        operation.requestBody(), operation.responses(), operation.tags(),
                        operation.summary(), operation.description(), operation.deprecated(),
                        Optional.empty()))
                .toList();
        return new ApiModel(model.title(), model.version(), model.servers(), operations,
                model.schemas(), model.issues(), model.document(), Map.of(), Optional.empty());
    }

    /**
     * What a run handed nothing but this API sends it: each operation's likeliest request, then the
     * ordinary rounds, one line each.
     */
    private static List<String> requests(ApiModel model) {
        RandomTestCaseGenerator generator = asARunBuildsIt(model);
        List<String> lines = new ArrayList<>();
        List<Operation> operations = generator.testableOperations();
        for (Operation operation : operations) {
            lines.add(written(operation, generator.likeliestRequest(operation)));
        }
        for (int round = 0; round < ROUNDS; round++) {
            for (Operation operation : operations) {
                lines.add(written(operation, generator.generate(operation)));
            }
        }
        return lines;
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
