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
import io.restest.core.settings.DocumentSettings;
import io.restest.core.spec.SpecificationParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
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
import java.util.Objects;
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

    /** How long to wait for a document served over the network, and how much of it to read. */
    private final DocumentSettings settings;

    /** A parser that reads a document the way RESTest does when nobody has said otherwise. */
    public SwaggerSpecificationParser() {
        this(DocumentSettings.defaults());
    }

    /**
     * A parser that waits and reads as far as it is told to.
     *
     * @param settings how long to wait for a document fetched over the network, and the largest
     *     one to read at all. A hung server must not hang the run that asked for its document, and
     *     a server that answers for ever must not fill its memory
     */
    public SwaggerSpecificationParser(DocumentSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public ApiModel parse(String location) {
        if (location == null || location.isBlank()) {
            return incomplete(SpecificationIssue.document("location", "no location was given"));
        }

        Optional<String> content;
        try {
            content = read(location);
        } catch (TooLarge tooLarge) {
            // Said plainly, and with the name of the thing to change. Left to be discovered by the
            // reader after this, the same document would be reported as one somebody wrote wrongly.
            return incomplete(SpecificationIssue.document("location",
                    "the description at '" + location + "' is larger than this run will read, "
                            + "which is document.mostBytesRead = " + settings.mostBytesRead()
                            + " bytes"));
        }
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
        SwaggerParseResult result = readWithWhicheverParserUnderstands(content.get(), options);
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
        List<SpecificationIssue> documentIssues = new ArrayList<>();
        if (backendMessages != null) {
            backendMessages.forEach(message ->
                    documentIssues.add(SpecificationIssue.document("info", message)));
        }
        List<Server> servers =
                OperationConverter.convertServers(api.getServers(), "servers", documentIssues);

        Map<String, CanonicalSchema> schemas = new LinkedHashMap<>();
        if (api.getComponents() != null && api.getComponents().getSchemas() != null) {
            api.getComponents().getSchemas().forEach((name, schema) -> {
                CanonicalSchema converted;
                try {
                    converted = SchemaConverter.convert(schema, api.getComponents());
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

        Optional<String> document = asOneJsonDocument(api, issues);

        return new ApiModel(title, version, servers, operations.operations(), schemas, issues,
                document);
    }

    /**
     * The document itself, written back out as a single OpenAPI 3 JSON document.
     *
     * <p>RESTest keeps this so that a reply from an API can be checked against what its document
     * actually says, rather than against RESTest's reading of it. Two things follow from writing the
     * document out here rather than keeping the text that was read in. A Swagger 2.0 document is kept
     * in its converted form, because that is the form every later stage expects and because 2.0
     * spells its responses differently. And a YAML document is kept as JSON, which is the same
     * document written another way.
     *
     * <p>References are left as they are, not followed and pasted in. A document that refers to
     * itself has no finite pasted-in form, and whoever reads this follows references perfectly well.
     *
     * <p>If it cannot be written out, that is recorded as an issue and the run carries on with one
     * fewer thing it can check. Fewer checks is worse than all of them; it is far better than
     * refusing to test the API at all.
     */
    private static Optional<String> asOneJsonDocument(OpenAPI api, List<SpecificationIssue> issues) {
        String written;
        try {
            written = api.getSpecVersion() == SpecVersion.V31
                    ? Json31.pretty(api)
                    : Json.pretty(api);
        } catch (RuntimeException e) {
            written = null;
        }
        if (written == null || written.isBlank()) {
            issues.add(SpecificationIssue.document("info", "this document could not be written back "
                    + "out as JSON, so replies cannot be checked against the shapes it declares"));
            return Optional.empty();
        }
        return Optional.of(written);
    }

    private static ApiModel incomplete(SpecificationIssue issue) {
        return new ApiModel("", "", List.of(), List.of(), Map.of(), List.of(issue),
                Optional.empty());
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
    private Optional<String> read(String location) throws TooLarge {
        try {
            Path path = Path.of(location);
            if (Files.isReadable(path) && !Files.isDirectory(path)) {
                // Bounded like every other way in. A file on this machine is the one somebody
                // chose deliberately, so it is the least likely of the three to be enormous by
                // surprise - but the bound is published as the largest description that is read at
                // all, and a bound with an exception in it is not the thing that was promised.
                try (InputStream stream = Files.newInputStream(path)) {
                    return Optional.of(decode(readBounded(stream)));
                }
            }
        } catch (TooLarge tooLarge) {
            throw tooLarge;
        } catch (InvalidPathException | IOException ignored) {
            // Falls through: not every location is a valid local path, and that is not an error yet.
        }
        try (InputStream stream = SwaggerSpecificationParser.class.getClassLoader()
                .getResourceAsStream(location)) {
            if (stream != null) {
                return Optional.of(decode(readBounded(stream)));
            }
        } catch (TooLarge tooLarge) {
            throw tooLarge;
        } catch (IOException ignored) {
            // Falls through to the URL attempt.
        }
        if (location.startsWith("http://") || location.startsWith("https://")) {
            try {
                URLConnection connection = URI.create(location).toURL().openConnection();
                int patience = (int) Math.min(Integer.MAX_VALUE,
                        settings.fetchTimeout().toMillis());
                connection.setConnectTimeout(patience);
                connection.setReadTimeout(patience);
                try (InputStream stream = connection.getInputStream()) {
                    return Optional.of(decode(readBounded(stream)));
                }
            } catch (TooLarge tooLarge) {
                throw tooLarge;
            } catch (IOException | IllegalArgumentException ignored) {
                // A malformed URL (an illegal character, for instance) is exactly as unreachable as
                // one that is well-formed but answers nothing; both report the same way.
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Every byte, up to the ceiling a run was given - a bound against an unbounded or hostile
     * answer, not a claim about how large a real specification can be.
     *
     * <p>One byte more than the ceiling is asked for, so that "the description ended" and "I
     * stopped reading" can be told apart. They read identically otherwise, and a half-read
     * description is not a small description: it is a description that breaks off in the middle of
     * a line, which every reader after this would report as a document somebody wrote wrongly.
     * Saying which of the two happened is the difference between a person fixing their file and a
     * person hunting for a mistake that is not there.
     */
    private byte[] readBounded(InputStream stream) throws IOException {
        byte[] read = stream.readNBytes(settings.mostBytesRead() + 1);
        if (read.length > settings.mostBytesRead()) {
            throw new TooLarge();
        }
        return read;
    }

    /** A description longer than this run is willing to read. */
    private static final class TooLarge extends IOException {

        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }

    private static String decode(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // io.swagger.parser.OpenAPIParser does exactly this and is the obvious thing to call. It cannot
    // be used here: its package, io.swagger.parser, also arrives from two 1.x-era jars the parser
    // library still pulls in - swagger-parser-1.0.76 and swagger-compat-spec-parser-1.0.76 - and a
    // package supplied by several automatic modules at once is ambiguous. javac settles it by
    // dropping a jar and compiles; ECJ, the compiler behind the Eclipse-based editors, refuses the
    // import outright, whatever order the jars are in:
    //     The package io.swagger.parser is accessible from more than one module:
    //     swagger.compat.spec.parser, swagger.parser
    // Calling the readers directly keeps this module out of that package altogether.
    /**
     * Offers the document to each reader the parser library provides, in turn, and keeps the first
     * answer that actually produced a description of the API. One reader understands the current
     * format, another converts documents written in the older one, and which is needed cannot be
     * known until the document has been tried.
     */
    private static SwaggerParseResult readWithWhicheverParserUnderstands(
            String content, ParseOptions options) {
        SwaggerParseResult result = null;
        for (SwaggerParserExtension parser : OpenAPIV3Parser.getExtensions()) {
            result = parser.readContents(content, null, options);
            if (result != null && result.getOpenAPI() != null) {
                return result;
            }
        }
        return result;
    }

}
