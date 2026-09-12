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
package io.restest.core.model;

import io.restest.core.internal.Copies;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.SchemaReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole API as the tool understands it: its operations, the shapes they share, and what could not
 * be read.
 *
 * <p>This is the output of {@code restest-spec} and the input to everything else. Once it exists,
 * no part of the tool needs the document again, which is what ADR-0007 means by keeping the parser
 * behind a boundary: swapping the parser, or adding support for a new version of the format, changes
 * how this value is produced and nothing about how it is used.
 *
 * <p>It is immutable and holds no reference to anything global, so two models - two runs, two APIs,
 * two versions of one API - coexist in one JVM without interfering. That is design principle 6 at
 * the level of the data.
 *
 * @param title the API's name, as the document gives it
 * @param version the API's version, as the document gives it
 * @param servers the base URLs the document declares, in order. An operation may override them
 * @param operations every operation that could be read, in document order
 * @param schemas the shapes the document declares by name, which is where a
 *     {@link SchemaReference} resolves and therefore how a recursive shape is held
 * @param issues everything that could not be read, each saying where it was and why. An empty list
 *     means the document was read in full
 */
public record ApiModel(
        String title,
        String version,
        List<Server> servers,
        List<Operation> operations,
        Map<String, CanonicalSchema> schemas,
        List<SpecificationIssue> issues) {

    public ApiModel {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(version, "version");
        servers = List.copyOf(servers);
        operations = List.copyOf(operations);
        schemas = Copies.orderedMap(schemas, "schemas");
        issues = List.copyOf(issues);
        rejectDuplicateIds(operations);
    }

    /** An API of the given name and version with the given operations, read in full. */
    public static ApiModel of(String title, String version, List<Operation> operations) {
        return new ApiModel(title, version, List.of(), operations, Map.of(), List.of());
    }

    /** The same API, with the named shapes its references resolve against. */
    public ApiModel withSchemas(Map<String, CanonicalSchema> value) {
        return new ApiModel(title, version, servers, operations, value, issues);
    }

    /** The same API, carrying what could not be read. */
    public ApiModel withIssues(List<SpecificationIssue> value) {
        return new ApiModel(title, version, servers, operations, schemas, value);
    }

    /** The operation under the given identifier, if this API has one. */
    public Optional<Operation> operation(OperationId id) {
        Objects.requireNonNull(id, "id");
        return operations.stream().filter(operation -> operation.id().equals(id)).findFirst();
    }

    /** Every operation using the given method, in document order. */
    public List<Operation> operations(HttpMethod method) {
        Objects.requireNonNull(method, "method");
        return operations.stream().filter(operation -> operation.method() == method).toList();
    }

    /** The shape declared under the given name, if the document declares one. */
    public Optional<CanonicalSchema> schema(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(schemas.get(name));
    }

    /**
     * What a reference points at.
     *
     * <p>Empty when the document never declared the name - a broken reference, which is a fact for
     * the parser to report rather than a reason to refuse the API. Following a chain of references
     * is left to the caller, deliberately: a document can point one name at another, and a model
     * that resolved chains silently could loop for ever on one that points at itself.
     */
    public Optional<CanonicalSchema> resolve(SchemaReference reference) {
        Objects.requireNonNull(reference, "reference");
        return schema(reference.name());
    }

    /** Whether everything in the document could be read. */
    public boolean isComplete() {
        return issues.isEmpty();
    }

    /**
     * The servers an operation is reached at: its own when it declares any, the API's otherwise.
     *
     * <p>The inheritance rule lives here rather than in {@link Operation} because only the API knows
     * what is being inherited, and because a caller that has to remember the rule will eventually
     * forget it and send a request to the wrong host.
     */
    public List<Server> serversFor(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        return operation.servers().isEmpty() ? servers : operation.servers();
    }

    /**
     * Two operations under one identifier is a contradiction, not a degradation, so it is refused
     * here rather than carried.
     *
     * <p>The model is what the rest of the tool keys on: per-operation settings, stored
     * interactions, failure reports and {@code restest recheck} all look an operation up by
     * identifier, and every one of them would silently get the first of the two. Design principle 2
     * is not in tension with this - it says skip the offending operation and report it, and that is
     * exactly what the parser does from M1.2: it makes the identifier unique, records a
     * {@link SpecificationIssue} saying so, and keeps both operations testable. What it may not do
     * is hand over a model that cannot answer its own lookups.
     */
    private static void rejectDuplicateIds(List<Operation> operations) {
        Map<OperationId, Operation> seen = new LinkedHashMap<>();
        for (Operation operation : operations) {
            Operation previous = seen.put(operation.id(), operation);
            if (previous != null) {
                throw new IllegalArgumentException("two operations share the identifier "
                        + operation.id() + ": " + previous.method() + " " + previous.path()
                        + " and " + operation.method() + " " + operation.path());
            }
        }
    }
}
