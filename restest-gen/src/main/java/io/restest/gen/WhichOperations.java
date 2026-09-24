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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which of an API's operations a run is allowed to touch.
 *
 * <p>By default, all of them. This exists for the times when that is the wrong answer: pointing the
 * tool at an API somebody cares about and wanting it to read rather than write, or narrowing a run
 * down to the two operations being worked on so the whole budget goes there.
 *
 * <p>Two ways of narrowing, and they apply together. <b>By method</b> - only {@code GET}, say, or
 * only the four that HTTP calls <em>safe</em>. That is deliberately not called "read-only": an API
 * that searches with {@code POST} is perfectly ordinary, and one of the five in this project's own
 * priority corpus does exactly that, so a filter written in terms of reading and writing would be
 * wrong about it. <b>By name</b> - the operations listed and no others, each written either as the
 * identifier the document declares or as the method and path anybody can read off it,
 * {@code GET /pets/{petId}}.
 *
 * <p>This can only ever take operations away. The run has already worked out which ones it could
 * test at all, and this narrows that set rather than reopening it.
 */
public final class WhichOperations {

    /** The methods HTTP itself calls safe: they ask for something and change nothing. */
    public static final Set<HttpMethod> SAFE =
            Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    private static final WhichOperations EVERYTHING = new WhichOperations(Set.of(), Set.of());

    private final Set<HttpMethod> methods;
    private final Set<String> only;

    private WhichOperations(Set<HttpMethod> methods, java.util.Collection<String> only) {
        this.methods = Set.copyOf(methods);
        // Kept in the order they were written, because a message naming the ones this API does not
        // have should list them the way their author wrote them down.
        this.only = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(only));
    }

    /** No narrowing at all: every operation the run could test, it may test. */
    public static WhichOperations everything() {
        return EVERYTHING;
    }

    /**
     * A filter narrowing by method, by name, or by both.
     *
     * @param methods the methods to keep, or empty for every method
     * @param only the operations to keep, by identifier or by method and path, or empty for every
     *     operation. Taken as a collection rather than a set so that the order they were written
     *     in survives, which is the order a message about them reads best in
     * @return the filter
     */
    public static WhichOperations of(Set<HttpMethod> methods,
            java.util.Collection<String> only) {
        Objects.requireNonNull(methods, "methods");
        Objects.requireNonNull(only, "only");
        if (methods.isEmpty() && only.isEmpty()) {
            return EVERYTHING;
        }
        return new WhichOperations(methods, only);
    }

    /**
     * Whether this operation is one the run may touch.
     *
     * @param operation the operation
     * @return whether it survives both halves of the filter
     */
    public boolean matches(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        if (!methods.isEmpty() && !methods.contains(operation.method())) {
            return false;
        }
        return only.isEmpty() || only.contains(operation.id().value())
                || only.contains(methodAndPath(operation));
    }

    /** Whether this narrows anything at all, which is what decides if it is worth mentioning. */
    public boolean narrowsAnything() {
        return !methods.isEmpty() || !only.isEmpty();
    }

    /**
     * The operations named here that this API does not have, in the order they were written.
     *
     * <p>Worth saying out loud rather than quietly ignoring. A campaign naming an operation that
     * does not exist is usually a file written against an older version of the document, and the
     * run it produces tests less than its author believes - which is the one kind of wrong answer
     * that looks like a right one.
     *
     * @param model the API being tested
     * @return the names nothing in it answers to
     */
    public List<String> namesNothingMatches(ApiModel model) {
        Objects.requireNonNull(model, "model");
        Set<String> answersTo = new LinkedHashSet<>();
        for (Operation operation : model.operations()) {
            answersTo.add(operation.id().value());
            answersTo.add(methodAndPath(operation));
        }
        // One the document could not be read for is still one of the API's operations. Telling the
        // plan's author the API does not have it, while the run names it as one it could not test,
        // would be two answers to one question. Only its identifier is known, so only a plan naming
        // it that way is answered.
        model.unreadableOperations().forEach(issue ->
                answersTo.add(issue.operation().orElseThrow().value()));
        List<String> stale = new ArrayList<>();
        only.stream().filter(name -> !answersTo.contains(name)).forEach(stale::add);
        return List.copyOf(stale);
    }

    private static String methodAndPath(Operation operation) {
        return operation.method() + " " + operation.path();
    }

    /**
     * Two filters are the same when they keep the same operations.
     *
     * <p>Written out because this is held by {@link Campaign}, which is a record: without it, two
     * plans read from the same text would compare unequal whenever they carried a filter, and
     * equal whenever they did not.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof WhichOperations same
                && methods.equals(same.methods) && only.equals(same.only);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methods, only);
    }

    /** What this keeps, for a message that has to name it. */
    @Override
    public String toString() {
        if (!narrowsAnything()) {
            return "every operation";
        }
        return (methods.isEmpty() ? "" : "methods " + methods)
                + (methods.isEmpty() || only.isEmpty() ? "" : ", ")
                + (only.isEmpty() ? "" : "only " + only);
    }

    /** The methods this keeps, empty when it keeps every method. */
    public Set<HttpMethod> methods() {
        return methods;
    }

    /** The operations this keeps, empty when it keeps every operation. */
    public Set<String> only() {
        return only;
    }
}
