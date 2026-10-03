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
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.OpenApi30;
import com.networknt.schema.dialect.OpenApi31;
import com.networknt.schema.path.NodePath;
import io.restest.core.auth.Secrets;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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

    /**
     * The objections hiding a key can never be the cause of: about what kind of value something is,
     * about how many members or items it has, and about a number - a key hidden inside a number
     * stops the body being JSON at all.
     */
    private static final Set<String> NEVER_THE_HIDING = Set.of("type", "minimum", "maximum",
            "exclusiveMinimum", "exclusiveMaximum", "multipleOf", "minItems", "maxItems",
            "minProperties", "maxProperties", "items", "additionalItems");

    /** The objections about the names of an object's members rather than about their values. */
    private static final Set<String> ABOUT_NAMES = Set.of("required", "dependentRequired",
            "additionalProperties", "propertyNames");

    /**
     * The words of a shape that make what else is checked depend on how some other part of the
     * reply turned out: a choice between shapes, a condition, a count of the items that fit, and
     * the members or items no other part accepted. When the part that turned out otherwise holds a
     * key's replacement, what is objected to through one of these may have nothing of it at all.
     */
    private static final Set<String> DEPENDS_ON_THE_REST = Set.of("oneOf", "anyOf", "not", "if",
            "then", "else", "contains", "minContains", "maxContains", "dependentSchemas",
            "dependencies", "unevaluatedProperties", "unevaluatedItems", "discriminator");

    /** The words of a shape followed, in the way to an objection, by a name the document chose. */
    private static final Set<String> FOLLOWED_BY_A_NAME = Set.of("properties",
            "patternProperties", "dependentSchemas");

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
        // Whether the body is JSON at all is decided here, with our own reader, before the checker
        // is asked anything. It used to be decided by the checker throwing, and that was a way of
        // reporting faults that were not faults: every exception out of the checker was read as
        // "this is not JSON", so a shape the checker could not handle - a reference it resolves
        // only when it gets there, a pattern its regular-expression engine rejects - was announced
        // as a broken reply from an API that had answered perfectly. Reporting a fault that is not
        // one is the single thing a testing tool must not do, and it is what this rule exists to
        // avoid rather than cause.
        // A key the API repeated back was hidden before anything saw the reply, so a reply with
        // the text written in a key's place is not quite what the API sent: that text can be longer
        // than a length the document allows, or not one of the values it allows, can make the reply
        // fail the shape it really has so that another shape of a choice objects instead, and a key
        // hidden inside a number stops the body being JSON at all. What the hiding may have changed
        // is not held against the API; everything else in the reply is judged as ever.
        boolean hidesAKey = body.contains(Secrets.MARKER);
        try {
            JsonText.checkOneValue(body);
        } catch (JsonException notJson) {
            return hidesAKey ? List.of() : List.of(mismatch(interaction, statusCode, contentType,
                    "the body is not JSON at all", List.of(firstLineOf(notJson))));
        }
        List<Error> errors;
        try {
            // A reply is judged as a reply: a property the document marks write-only is one the API
            // is sent, never one it hands back, so its being required does not make a reply without
            // it wrong.
            errors = checker.validate(body, InputFormat.JSON,
                    context -> context.executionConfig(config -> config.writeOnly(true)));
        } catch (RuntimeException cannotBeJudged) {
            // The body is JSON and the checker still could not finish. Whatever the reason, it is
            // something about the declared shape rather than about the reply, so nothing is claimed.
            // A reply RESTest was unable to check is not evidence that the API did anything wrong.
            return List.of();
        }
        List<String> details = new ArrayList<>();
        for (Error error : errors) {
            if ("writeOnly".equals(error.getKeyword())) {
                // The same switch also objects to a write-only property that is there. The
                // specification only says it should not be, so that is no fault to report.
                continue;
            }
            if (hidesAKey && mayBeTheHidingsDoing(error)) {
                continue;
            }
            String where = String.valueOf(error.getInstanceLocation());
            details.add((where.isEmpty() ? "the body" : where) + ": " + error.getMessage());
        }
        if (details.isEmpty()) {
            return List.of();
        }
        return List.of(mismatch(interaction, statusCode, contentType,
                "the body does not match the shape the specification declares for it", details));
    }

    /**
     * Whether an objection may be the doing of the text written in a key's place rather than the
     * API's.
     *
     * <p>It may when it is reached through a choice between shapes or a condition - the shape the
     * reply really has can fail on the replacement, and then every other shape's objections are
     * made, about parts of the reply the replacement never touched - or when the way to it goes
     * through a member whose name holds the replacement. Otherwise it may when what it objects to
     * holds the replacement and is something the replacement could change: a length, a pattern, a
     * value from a list, or the names of an object's members when one of them holds it. Hiding a
     * key never changes what kind of value something is, nor how many members or items there are,
     * nor a number, so those objections stand; so does an object with the replacement in one of its
     * values, judged on its members' names.
     */
    private static boolean mayBeTheHidingsDoing(Error error) {
        if (anyStep(error.getInstanceLocation(), step -> step.contains(Secrets.MARKER))
                || throughAChoice(error.getEvaluationPath())) {
            return true;
        }
        String keyword = String.valueOf(error.getKeyword());
        if (NEVER_THE_HIDING.contains(keyword)) {
            return false;
        }
        String instance = String.valueOf(error.getInstanceNode());
        if (!instance.contains(Secrets.MARKER)) {
            return false;
        }
        if (!ABOUT_NAMES.contains(keyword)) {
            return true;
        }
        try {
            return !(JsonText.readFromAnApi(instance) instanceof JsonValue.JsonObject object)
                    || object.members().keySet().stream()
                            .anyMatch(name -> name.contains(Secrets.MARKER));
        } catch (JsonException unreadable) {
            return true;
        }
    }

    /**
     * Whether the way to an objection goes through a choice or a condition. A member of an object
     * may be called {@code else} or {@code dependencies}, so a step that names a member is told
     * apart from one that is a word of the shape by the word before it. A way the checker did not
     * give is taken to go through one.
     */
    private static boolean throughAChoice(NodePath path) {
        if (path == null) {
            return true;
        }
        boolean aName = false;
        for (int step = 0; step < path.getNameCount(); step++) {
            String word = String.valueOf(path.getName(step));
            if (aName) {
                aName = false;
            } else if (DEPENDS_ON_THE_REST.contains(word)) {
                return true;
            } else {
                aName = FOLLOWED_BY_A_NAME.contains(word);
            }
        }
        return false;
    }

    /** Whether any step of a path passes the test. A path the checker did not give passes it. */
    private static boolean anyStep(NodePath path, Predicate<String> test) {
        if (path == null) {
            return true;
        }
        for (int step = 0; step < path.getNameCount(); step++) {
            if (test.test(String.valueOf(path.getName(step)))) {
                return true;
            }
        }
        return false;
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
                                        // Its explanations come in the machine's own language
                                        // otherwise, which puts Spanish or German inside an
                                        // English report, and differently on every machine.
                                        .locale(Locale.ROOT)
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
