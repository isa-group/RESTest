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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A base URL the API is served from, with whatever the document says about its variables.
 *
 * <p>The URL is kept as written, templates included ({@code https://{region}.example.com/v2}), and
 * {@link #resolvedUrl()} applies the documented defaults. Both are wanted: the template is what a
 * report should show, and the resolved form is what a request is sent to.
 *
 * <p>Choosing between several declared servers, or overriding them, stays a decision for the command
 * line - {@code restest run} takes {@code --url} precisely so that a user can ignore whatever the
 * document claims.
 *
 * @param url the base URL, possibly templated
 * @param variables what the document says each template variable may hold, by name
 * @param description what the document says the server is, a staging or production note most often
 */
public record Server(String url, Map<String, ServerVariable> variables,
        Optional<String> description) {

    public Server {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(description, "description");
        variables = Copies.orderedMap(variables, "variables");
        if (url.isBlank()) {
            throw new IllegalArgumentException("a server has a URL");
        }
    }

    /** A server at the given base URL, with no variables. */
    public static Server at(String url) {
        return new Server(url, Map.of(), Optional.empty());
    }

    /** The variables a URL template carries: {@code region} in {@code https://{region}.example.com}. */
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{([^{}/]+)}");

    /**
     * The URL with every declared variable replaced by its default.
     *
     * <p>A template the document declares no variable for is left as it stands, braces and all. That
     * is a document the tool cannot derive a base URL from, and leaving the template visible says so
     * - both to {@link #isResolved()} and to whoever reads the error.
     *
     * <p>One pass over the template, not one pass per variable. Replacing each variable in turn
     * would let one default containing braces be expanded by another variable's substitution, so
     * the URL would depend on the order the document happened to declare its variables in.
     */
    public String resolvedUrl() {
        Matcher variable = TEMPLATE_VARIABLE.matcher(url);
        StringBuilder resolved = new StringBuilder();
        while (variable.find()) {
            ServerVariable declared = variables.get(variable.group(1));
            variable.appendReplacement(resolved, Matcher.quoteReplacement(
                    declared == null ? variable.group() : declared.defaultValue()));
        }
        variable.appendTail(resolved);
        return resolved.toString();
    }

    /** Whether {@link #resolvedUrl()} produced a URL with nothing left to fill in. */
    public boolean isResolved() {
        String resolved = resolvedUrl();
        return resolved.indexOf('{') < 0 && resolved.indexOf('}') < 0;
    }
}
