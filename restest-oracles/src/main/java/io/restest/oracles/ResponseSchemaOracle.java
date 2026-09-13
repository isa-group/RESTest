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

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.OpenApi30;
import com.networknt.schema.dialect.OpenApi31;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.ResponseModel;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.WfcFault;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports a reply whose body is not the shape the specification said it would be.
 *
 * <p>A specification describes what goes in and what comes back. If a document says a successful
 * call to {@code GET /pets/{id}} returns an object with a whole number {@code id} and a text {@code
 * name}, and the API returns {@code id} as text, or leaves {@code name} out altogether, then the
 * API and its documentation disagree. Either is a real problem: anyone writing a program against
 * that document has been misled.
 *
 * <p>What a reply is judged against is the document itself, exactly as the user handed it over -
 * not RESTest's reading of it. So composition, discriminators, references between shapes and every
 * other thing a specification can say all count, whether or not RESTest itself understands them.
 * Doing the checking is an off-the-shelf job, and an off-the-shelf checker does it.
 *
 * <p>The rule throughout is that nothing uncertain is ever reported. Silence here means one of two
 * quite different things - the reply was fine, or there was no way to tell - and the second happens
 * far more often than people expect. A document need not say anything about what comes back, or
 * about that particular status code, or about that particular media type. The body may not be JSON,
 * or may have been too large to keep in full. Every one of those ends the check, quietly. A tool
 * that guesses in those situations produces complaints about APIs that were behaving perfectly, and
 * a tool that does that gets switched off.
 *
 * <p>One thing a specification can say is deliberately not checked: {@code format}. Saying a piece
 * of text is a {@code date} or an {@code email} is, in the standard, a note to the reader and not a
 * rule; different tools enforce different ones, and enforcing them here would report working APIs
 * as broken over a matter of opinion.
 */
public final class ResponseSchemaOracle implements Oracle {

    /** What a JSON reply calls itself: {@code application/json}, or anything ending in +json. */
    private static final String JSON_SUFFIX = "+json";
    private static final String JSON_TYPE = "application/json";

    /** The document being tested against, prepared once. Guarded by this object's own lock. */
    private Reader reader;

    /** Which API {@link #reader} was prepared for, compared by identity. Guarded likewise. */
    private ApiModel preparedFor;

    @Override
    public String name() {
        return "response-schema";
    }

    @Override
    public String description() {
        return "reports a reply whose body is not the shape the specification said it would be";
    }

    @Override
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        Optional<Reader> reader = readerFor(api);
        if (reader.isEmpty()) {
            return List.of();
        }
        Optional<HttpResponseRecord> answer = interaction.response();
        if (answer.isEmpty()) {
            return List.of();
        }
        HttpResponseRecord response = answer.get();
        Optional<Operation> operation = api.operation(interaction.testCase().operation());
        if (operation.isEmpty()) {
            return List.of();
        }
        Optional<String> mediaType = response.headerValues("Content-Type").stream().findFirst();
        if (mediaType.isEmpty() || !isJson(mediaType.get())) {
            return List.of();
        }
        Optional<ResponseModel> declared = operation.get().responseFor(response.statusCode());
        if (declared.isEmpty()) {
            return List.of();
        }
        Optional<String> contentType = declared.get().declaredContentTypeFor(mediaType.get());
        if (contentType.isEmpty()) {
            return List.of();
        }
        if (isTruncated(response) || !hasBody(response) || !isUtf8(mediaType.get())) {
            return List.of();
        }
        Optional<String> pointer = reader.get().document()
                .pointerToSchema(operation.get(), declared.get(), contentType.get());
        if (pointer.isEmpty()) {
            return List.of();
        }
        Optional<Schema> checker = reader.get().checkerAt(pointer.get());
        if (checker.isEmpty()) {
            return List.of();
        }
        return judgeBody(interaction, checker.get(), bodyText(response), response.statusCode(),
                contentType.get());
    }

    /**
     * Prepares the document a reply will be checked against, once per API rather than once per
     * reply.
     *
     * <p>Compared by identity rather than by contents on purpose: a specification is a large thing
     * to compare, this is asked once for every reply, and a run works with one model throughout.
     * Meeting a different one simply prepares again.
     */
    private synchronized Optional<Reader> readerFor(ApiModel api) {
        if (preparedFor != api) {
            preparedFor = api;
            reader = Reader.forApi(api).orElse(null);
        }
        return Optional.ofNullable(reader);
    }

    /**
     * Whether only part of the reply was kept, because it was larger than the run was willing to
     * hold. Half a reply cannot be judged: what looked like a fault would be RESTest's own setting.
     */
    private static boolean isTruncated(HttpResponseRecord response) {
        return response.body().map(Payload::truncated).orElse(false);
    }

    /**
     * Whether there is a body to judge at all.
     *
     * <p>A reply with no body is left alone even where the document declares a shape for one, which
     * looks at first like a check being given away. It is not. An answer to a {@code HEAD} request
     * carries the headers a {@code GET} would - the declared media type among them - and no body,
     * by the rules of HTTP; so does a {@code 304}. Reporting those would be complaining about an
     * API for doing what it is supposed to do. Whether a reply was allowed to be empty at all is a
     * question about status codes, and belongs with the rules that judge those.
     */
    private static boolean hasBody(HttpResponseRecord response) {
        return response.body().map(payload -> payload.size() > 0).orElse(false);
    }

    private static String bodyText(HttpResponseRecord response) {
        return new String(response.body().orElseThrow().content(), StandardCharsets.UTF_8);
    }

    /**
     * Whether the reply's own description of itself allows it to be read as UTF-8.
     *
     * <p>JSON is UTF-8; the standard says so and offers no alternative, so a reply naming no
     * character set is read as UTF-8 and a reply naming UTF-8 agrees. A reply insisting on some
     * other character set is left unjudged rather than read wrongly and then complained about.
     */
    private static boolean isUtf8(String mediaType) {
        for (String parameter : mediaType.split(";")) {
            String[] named = parameter.split("=", 2);
            if (named.length == 2 && named[0].trim().equalsIgnoreCase("charset")) {
                String charset = named[1].trim().replace("\"", "");
                return charset.isEmpty()
                        || charset.equalsIgnoreCase("utf-8")
                        || charset.equalsIgnoreCase("utf8");
            }
        }
        return true;
    }

    private List<Finding> judgeBody(Interaction interaction, Schema checker, String body,
            int statusCode, String contentType) {
        List<Error> errors;
        try {
            errors = checker.validate(body, InputFormat.JSON);
        } catch (SchemaException unusableShape) {
            // Something about the declared shape itself defeated the checker. That is a problem
            // with the document, already reported when the document was read; it is not evidence
            // about the API, so nothing is claimed here.
            return List.of();
        } catch (RuntimeException notJson) {
            // The only other thing that can go wrong here is the body not being JSON at all, which
            // the checker complains about by throwing. Caught by its general kind rather than by
            // name on purpose: naming it would mean naming the checker's own JSON library, and the
            // point of confining a library to one module is that its types stay inside it.
            return List.of(mismatch(interaction, statusCode, contentType,
                    "the body is not JSON at all", List.of(firstLineOf(notJson))));
        }
        if (errors.isEmpty()) {
            return List.of();
        }
        List<String> details = new ArrayList<>();
        for (Error error : errors) {
            String where = String.valueOf(error.getInstanceLocation());
            details.add((where.isEmpty() ? "the body" : where) + ": " + error.getMessage());
        }
        return List.of(mismatch(interaction, statusCode, contentType,
                "the body does not match the shape the specification declares for it", details));
    }

    private static Finding mismatch(Interaction interaction, int statusCode, String contentType,
            String summary, List<String> details) {
        return Finding.of(WfcFault.SCHEMA_INVALID_RESPONSE, interaction,
                        summary + ", answering " + statusCode + " as " + contentType)
                .withDetails(details);
    }

    /**
     * The first line of a complaint, without the reader's note about where in the bytes it was.
     *
     * <p>Package-private so that it can be checked directly: what a report says when a body is not
     * JSON is the whole use of it, and the complaints that reach it are somebody else's to word.
     */
    static String firstLineOf(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        return message.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElseGet(() -> e.getClass().getSimpleName());
    }

    private static boolean isJson(String mediaType) {
        String bare = mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return bare.equals(JSON_TYPE) || bare.endsWith(JSON_SUFFIX);
    }

    /**
     * One API's document, with the checker that reads shapes out of it.
     *
     * <p>The document is registered under a name that is never fetched, so that a reference inside
     * it such as {@code #/components/schemas/Pet} has something to be relative to and resolves
     * without anything leaving this computer. Fetching references from the network is switched off
     * outright: a specification is somebody else's file, and a file that could make the tool call
     * out to an address of its choosing is not something to leave switched on.
     */
    private record Reader(SpecificationDocument document, SchemaRegistry registry,
            Map<String, Optional<Schema>> checkers) {

        static Optional<Reader> forApi(ApiModel api) {
            return SpecificationDocument.of(api).map(document -> {
                Dialect dialect = document.openApiVersion().orElse("").startsWith("3.1")
                        ? OpenApi31.getInstance()
                        : OpenApi30.getInstance();
                SchemaRegistry registry = SchemaRegistry.withDefaultDialect(dialect,
                        builder -> builder
                                .schemas(Map.of(SpecificationDocument.URI, document.text()))
                                .schemaLoader(loader -> loader.fetchRemoteResources(false))
                                .schemaRegistryConfig(SchemaRegistryConfig.builder()
                                        .formatAssertionsEnabled(false)
                                        .build()));
                return new Reader(document, registry, new ConcurrentHashMap<>());
            });
        }

        /**
         * The checker for one shape, built once and kept.
         *
         * <p>Kept here, beside the document it came from, rather than on the oracle. A trail of
         * names means nothing on its own: two specifications can spell the same trail and mean two
         * different shapes by it, so anything remembering a shape by its trail alone would sooner
         * or later answer for the wrong API.
         */
        Optional<Schema> checkerAt(String pointer) {
            return checkers().computeIfAbsent(pointer, where -> {
                try {
                    return Optional.of(registry().getSchema(
                            SchemaLocation.of(SpecificationDocument.URI + where)));
                } catch (RuntimeException notUsable) {
                    // Anything at all going wrong here means one thing: the shape this points at
                    // cannot be used. Caught broadly rather than by name, because an exception
                    // escaping from here does not merely lose this reply - it abandons every
                    // remaining rule for this attempt, and the report then reads as though the
                    // whole thing had been checked. Saying nothing is honest; saying nothing while
                    // appearing to have looked is not.
                    return Optional.empty();
                }
            });
        }
    }
}
