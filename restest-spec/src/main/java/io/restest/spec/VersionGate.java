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
package io.restest.spec;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Decides whether a document's own declared version is one the parser backend may be trusted with,
 * before it ever sees the document.
 *
 * <p>Fed a document it does not recognise, the backend does not fail cleanly: given
 * {@code openapi: 3.2.0}, it has been seen to return messages that never mention 3.2 at all, and a
 * non-null result that silently claims a different version - confirmed while building this class.
 * Trusting its output to decide "is this version supported" would sometimes get the answer wrong.
 * This class reads the one fact it needs - the declared version - directly, with a plain YAML load
 * (YAML is a superset of JSON, so one code path covers both corpus formats), and the backend is only
 * ever invoked once that fact is already known to be in scope: OAS 2.0, 3.0.x and 3.1.x.
 *
 * <p>Charitable on purpose: a bare {@code 3.0} (no patch number), surrounding whitespace, and a
 * trailing pre-release or build tag ({@code 3.0.1-rc1}) are all read as the version they plainly are,
 * not rejected on a technicality a real document is entitled to have.
 */
final class VersionGate {

    private static final Pattern SUPPORTED = Pattern.compile("2\\.0|3\\.[01](\\.\\d+)?([-+].*)?");

    /**
     * SnakeYAML refuses, by default, to load a document past roughly three million characters - a
     * guard against decompression-bomb-style input that is far too small for a real specification;
     * the largest one in this project's own corpus is already most of the way there. This is a
     * generous ceiling for peeking at one top-level key, not an endorsement of documents this large.
     */
    private static final int CODE_POINT_LIMIT = 100_000_000;

    /**
     * SnakeYAML's default nesting-depth guard, 50, is comfortably deep for a document written by
     * hand but not for one a code generator produced: a schema with a property of a property of a
     * property, twenty-odd levels deep, is unusual but real, and the backend this gate hands off to
     * (Jackson, whose own default limit is 1000) would read it without complaint. Raised here to the
     * same order of magnitude, so the gate is not the reason such a document is rejected.
     */
    private static final int NESTING_DEPTH_LIMIT = 1_000;

    private VersionGate() {
    }

    /** Empty when the declared version is in scope; otherwise, why it is not. */
    static Optional<String> unsupportedReason(String content) {
        Object document;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setCodePointLimit(CODE_POINT_LIMIT);
            options.setNestingDepthLimit(NESTING_DEPTH_LIMIT);
            document = new Yaml(options).load(content);
        } catch (RuntimeException e) {
            return Optional.of("the document is not valid YAML or JSON: " + firstLine(e.getMessage()));
        }
        if (!(document instanceof Map<?, ?> map)) {
            return Optional.of("the document is not a YAML/JSON object");
        }
        Object declared = map.containsKey("openapi") ? map.get("openapi") : map.get("swagger");
        if (declared == null) {
            return Optional.of("the document declares neither 'openapi' nor 'swagger'");
        }
        String version = String.valueOf(declared).trim();
        if (!SUPPORTED.matcher(version).matches()) {
            return Optional.of("the document declares version '" + version + "', which is out of "
                    + "scope (2.0, 3.0.x and 3.1.x are supported)");
        }
        return Optional.empty();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "malformed";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
