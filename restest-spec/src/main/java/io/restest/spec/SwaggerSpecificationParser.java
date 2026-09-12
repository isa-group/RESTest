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

import io.restest.core.model.ApiModel;
import io.restest.core.model.Server;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.UnsupportedSchema;
import io.restest.core.spec.SpecificationParser;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads an OpenAPI or Swagger document with {@code swagger-parser}, the only place in RESTest that
 * does.
 *
 * <p>{@code swagger-parser} is trusted for exactly one thing: turning a document already known to be
 * OAS 2.0 (which it converts to 3.0 itself, before this class ever sees it), 3.0.x or 3.1.x into
 * Java objects. Everything about whether the document is safe to hand it, and everything about
 * turning those objects into RESTest's own model, is this module's job - see {@link VersionGate},
 * {@link SchemaConverter} and {@link OperationConverter}.
 *
 * <p>Nothing here throws for a problem with the document itself. A location nothing answers at, text
 * that is not valid YAML or JSON, a declared version out of scope, an operation that cannot be
 * represented - each becomes an {@link ApiModel} with an issue attached, never an exception. Because a
 * hand-written or machine-generated document can be wrong in ways no amount of individually-guarded
 * code anticipates, converting the parsed document into RESTest's model runs under one last, general
 * guard too, but only around whatever a more specific guard has not already caught closer to the
 * construct that broke - named schemas and servers are each guarded where they are read, precisely so
 * that a single bad one costs only itself, never every operation in the document.
 */
public final class SwaggerSpecificationParser implements SpecificationParser {

    /** Generous, but not unbounded: a hung server must not hang the run that asked for its document. */
    private static final int URL_TIMEOUT_MILLIS = 10_000;

    /** Far larger than any real specification; a bound against an unbounded or hostile response. */
    private static final int MAX_DOCUMENT_BYTES = 64 * 1024 * 1024;

    @Override
    public ApiModel parse(String location) {
        if (location == null || location.isBlank()) {
            return incomplete(SpecificationIssue.document("location", "no location was given"));
        }

        Optional<String> content = read(location);
        if (content.isEmpty()) {
            return incomplete(SpecificationIssue.document("location",
                    "nothing could be read from '" + location + "'"));
        }

        Optional<String> unsupported = VersionGate.unsupportedReason(content.get());
        if (unsupported.isPresent()) {
            return incomplete(SpecificationIssue.document("info", unsupported.get()));
        }

        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        SwaggerParseResult result = new OpenAPIParser().readContents(content.get(), null, options);
        OpenAPI api = result.getOpenAPI();
        List<String> messages = result.getMessages();
        if (api == null) {
            String reason = messages == null || messages.isEmpty()
                    ? "the document could not be read" : String.join("; ", messages);
            return incomplete(SpecificationIssue.document("info", reason));
        }

        try {
            return toApiModel(api, messages);
        } catch (RuntimeException e) {
            // Every construct this class knows to expect is already guarded, specifically, closer to
            // where it can go wrong. This is the backstop for the one it does not yet know about: the
            // rule that a bad document degrades the run, not this one exception's job to enumerate.
            return incomplete(SpecificationIssue.document("info",
                    "the document could not be fully converted: "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }

    private static ApiModel toApiModel(OpenAPI api, List<String> backendMessages) {
        Info info = api.getInfo();
        String title = info != null && info.getTitle() != null ? info.getTitle() : "";
        String version = info != null && info.getVersion() != null ? info.getVersion() : "";
        List<Server> servers = OperationConverter.convertServers(api.getServers());

        List<SpecificationIssue> documentIssues = new ArrayList<>();
        if (backendMessages != null) {
            backendMessages.forEach(message ->
                    documentIssues.add(SpecificationIssue.document("info", message)));
        }

        Map<String, CanonicalSchema> schemas = new LinkedHashMap<>();
        if (api.getComponents() != null && api.getComponents().getSchemas() != null) {
            api.getComponents().getSchemas().forEach((name, schema) -> {
                CanonicalSchema converted;
                try {
                    converted = SchemaConverter.convert(schema);
                } catch (IllegalArgumentException e) {
                    // A named schema this malformed is still named: keeping the entry, as something
                    // unsupported, means an operation that references it is degraded, correctly,
                    // rather than the reference silently resolving to nothing.
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    converted = UnsupportedSchema.of("could not be represented: " + reason);
                    documentIssues.add(SpecificationIssue.document("components.schemas." + name,
                            "this schema could not be represented: " + reason));
                }
                schemas.put(name, converted);
            });
        }
        schemas.forEach((name, schema) -> {
            if (SchemaConverter.hasUnsupportedConstruct(schema, schemas)) {
                documentIssues.add(SpecificationIssue.document("components.schemas." + name,
                        "this schema includes a shape RESTest does not fully understand yet"));
            }
        });

        OperationConverter.Result operations = OperationConverter.convert(api, schemas);

        List<SpecificationIssue> issues = new ArrayList<>(documentIssues);
        issues.addAll(operations.issues());

        return new ApiModel(title, version, servers, operations.operations(), schemas, issues);
    }

    private static ApiModel incomplete(SpecificationIssue issue) {
        return new ApiModel("", "", List.of(), List.of(), Map.of(), List.of(issue));
    }

    /**
     * The document's raw text, from a file path, a path relative to the classpath, or a URL - in that
     * order. Empty when none of the three produced anything, which is the one signal
     * {@code swagger-parser} itself does not give: fed a location nothing answers at, it returns a
     * {@code null} result and {@code null} messages, confirmed empirically while building this class.
     *
     * <p>Read as bytes and decoded leniently rather than with a decoder that throws, so a file that
     * exists but is not valid UTF-8 is reported for what it is - unreadable content - rather than
     * mistaken for a location nothing answered at.
     */
    private static Optional<String> read(String location) {
        try {
            Path path = Path.of(location);
            if (Files.isReadable(path) && !Files.isDirectory(path)) {
                return Optional.of(decode(Files.readAllBytes(path)));
            }
        } catch (InvalidPathException | IOException ignored) {
            // Falls through: not every location is a valid local path, and that is not an error yet.
        }
        try (InputStream stream = SwaggerSpecificationParser.class.getClassLoader()
                .getResourceAsStream(location)) {
            if (stream != null) {
                return Optional.of(decode(readBounded(stream)));
            }
        } catch (IOException ignored) {
            // Falls through to the URL attempt.
        }
        if (location.startsWith("http://") || location.startsWith("https://")) {
            try {
                URLConnection connection = URI.create(location).toURL().openConnection();
                connection.setConnectTimeout(URL_TIMEOUT_MILLIS);
                connection.setReadTimeout(URL_TIMEOUT_MILLIS);
                try (InputStream stream = connection.getInputStream()) {
                    return Optional.of(decode(readBounded(stream)));
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // A malformed URL (an illegal character, for instance) is exactly as unreachable as
                // one that is well-formed but answers nothing; both report the same way.
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Every byte, up to a ceiling no real specification approaches - a bound against an unbounded or
     * hostile response, not a claim about how large a real document can be.
     */
    private static byte[] readBounded(InputStream stream) throws IOException {
        return stream.readNBytes(MAX_DOCUMENT_BYTES);
    }

    private static String decode(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
