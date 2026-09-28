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

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The specifications these tests read, and a way of finding an operation in one by its address. */
final class TheCorpus {

    private TheCorpus() {
    }

    /** One of the five APIs the competition names, read the way a run reads it. */
    static ApiModel priority(String api) {
        Path document = specifications().resolve("restleague-2027").resolve(api)
                .resolve("openapi.yaml");
        if (!Files.isRegularFile(document)) {
            throw new IllegalArgumentException("the priority corpus has no " + api);
        }
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /** One of the documents the community wrote, read the way a run reads it. */
    static ApiModel community(String api) {
        Path document = specifications().resolve("community").resolve(api)
                .resolve("openapi.yaml");
        if (!Files.isRegularFile(document)) {
            throw new IllegalArgumentException("the community corpus has no " + api);
        }
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /** Every document of the corpus, without the deliberately broken ones kept beside it. */
    static List<Path> all() {
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

    static ApiModel parse(Path document) {
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /** The operation at this address with this method. */
    static Operation operation(ApiModel model, HttpMethod method, String path) {
        return model.operations().stream()
                .filter(operation -> operation.method() == method && operation.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(model.title() + " has no "
                        + method + " " + path));
    }

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
