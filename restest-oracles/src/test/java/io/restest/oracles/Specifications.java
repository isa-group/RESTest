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
package io.restest.oracles;

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small APIs to point the rules at, each with the document it was read from.
 *
 * <p>The shapes RESTest holds in memory here are deliberately empty ones that accept anything. The
 * document says what a reply must look like; RESTest's own reading of it says nothing at all. If a
 * test below still catches a bad reply, it is the document doing the catching, which is exactly the
 * claim being made.
 */
final class Specifications {

    private Specifications() {
    }

    /** An API of pets, as OpenAPI 3.0, spelling some of its own keys awkwardly on purpose. */
    static ApiModel pets() {
        Operation listPets = Operation.of(HttpMethod.GET, "/pets")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation onePet = Operation.of(HttpMethod.GET, "/pets/{petId}",
                        List.of(Parameter.of("petId", ParameterLocation.PATH, true,
                                NumberSchema.of(NumberKind.INTEGER))))
                .withResponses(List.of(
                        ResponseModel.json("200", AnySchema.of()),
                        new ResponseModel("default", Map.of("Application/JSON", AnySchema.of()),
                                Map.of(), Optional.empty())));
        Operation kinds = Operation.of(HttpMethod.GET, "/kinds")
                .withResponses(List.of(ResponseModel.json("2xx", AnySchema.of())));
        Operation raw = Operation.of(HttpMethod.GET, "/raw")
                .withResponses(List.of(new ResponseModel("200",
                        Map.of("text/plain", AnySchema.of()), Map.of(), Optional.empty())));
        Operation unsaid = Operation.of(HttpMethod.GET, "/unsaid")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation noReply = Operation.of(HttpMethod.GET, "/noreply")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation onlyXml = Operation.of(HttpMethod.GET, "/onlyxml")
                .withResponses(List.of(new ResponseModel("200",
                        Map.of("application/xml", AnySchema.of()), Map.of(), Optional.empty())));
        Operation dangling = Operation.of(HttpMethod.GET, "/dangling")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation charsetKey = Operation.of(HttpMethod.GET, "/charsetkey")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation byReference = Operation.of(HttpMethod.GET, "/byreference")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation byChain = Operation.of(HttpMethod.GET, "/bychain")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation elsewhere = Operation.of(HttpMethod.GET, "/elsewhere")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation seen = Operation.of(HttpMethod.GET, "/seen")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation strayEscape = Operation.of(HttpMethod.GET, "/strayescape")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation encodedReference = Operation.of(HttpMethod.GET, "/encodedref")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation cycle = Operation.of(HttpMethod.GET, "/cycle")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation nowhere = Operation.of(HttpMethod.GET, "/nowhere")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        Operation problem = Operation.of(HttpMethod.GET, "/problem")
                .withResponses(List.of(new ResponseModel("200",
                        Map.of("application/problem+json; charset=utf-8", AnySchema.of()),
                        Map.of(), Optional.empty())));

        return ApiModel.of("Pets", "1.0.0", List.of(listPets, onePet, kinds, raw, unsaid, noReply,
                        onlyXml, dangling, charsetKey, byReference, byChain, elsewhere, seen,
                        strayEscape, encodedReference, cycle, nowhere, problem))
                .withDocument(read("/pets-3.0.json"));
    }

    /**
     * An API written as OpenAPI 3.0 whose one reply sits at exactly the same place in its document
     * as {@link #kinds31()}'s does, and says something different about it.
     *
     * <p>Two specifications can name the same trail to two different shapes, and anything that
     * remembers one shape by its trail alone would answer for the wrong API. Having the pair makes
     * that mistake something a test can catch rather than something to be careful about.
     */
    static ApiModel kinds30() {
        Operation kinds = Operation.of(HttpMethod.GET, "/kinds")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        return ApiModel.of("Kinds", "1.0.0", List.of(kinds))
                .withDocument(read("/kinds-3.0.json"));
    }

    /** An API written as OpenAPI 3.1, where "or nothing" is said a different way from 3.0. */
    static ApiModel kinds31() {
        Operation kinds = Operation.of(HttpMethod.GET, "/kinds")
                .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        return ApiModel.of("Kinds", "1.0.0", List.of(kinds))
                .withDocument(read("/kinds-3.1.json"));
    }

    /** The same API of pets, with no document kept - so nothing can be checked against it. */
    static ApiModel petsWithoutItsDocument() {
        ApiModel withDocument = pets();
        return ApiModel.of(withDocument.title(), withDocument.version(), withDocument.operations());
    }

    static String read(String resource) {
        try (InputStream file = Specifications.class.getResourceAsStream(resource)) {
            if (file == null) {
                throw new IllegalStateException("the fixture " + resource + " is missing");
            }
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
