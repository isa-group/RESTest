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

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.ResponseModel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The specification document itself, and how to point at one shape inside it.
 *
 * <p>When RESTest checks that a reply looks the way it was promised to look, it checks against the
 * document the user handed in, not against RESTest's own reading of that document. That matters for
 * a reason worth stating plainly: if the tool judged replies by its own understanding of a
 * specification, then anything it misunderstood would turn into complaints about an API that was
 * behaving perfectly. The document is the promise, so the document is what a reply is held to.
 *
 * <p>To do that, two things are needed. The document as text, which is what the checker reads. And
 * a way of saying which shape inside it applies - "the body of the 200 reply, as JSON, of GET
 * /pets" - written the standard way as a trail of names through the document:
 *
 * <pre>
 * #/paths/~1pets/get/responses/200/content/application~1json/schema </pre>
 *
 * <p>Working that trail out is not a matter of pasting the names together, because a document may
 * spell them differently from how RESTest files them: {@code 2xx} against {@code 2XX}, {@code
 * Application/JSON} against {@code application/json}. Which reply and which media type apply is
 * decided once, by the model, so those rules live in one place; this only finds how the document
 * happens to spell the two names, and confirms the shape really is there.
 */
final class SpecificationDocument {

    /**
     * The name the document is filed under while it is being read. It is never fetched: it exists
     * only so that a reference inside the document has something to be relative to.
     */
    static final String URI = "https://restest.invalid/specification";

    /**
     * Characters a name keeps as they are; everything else is written as {@code %XX}.
     *
     * <p>Deliberately the smallest set there is - letters, digits, and these four - rather than
     * everything a web address happens to permit. The question is not what is legal to write but
     * what survives being read back, and those are not the same set: a {@code +} is perfectly legal
     * and is read back as a space, so a media type such as
     * {@code application/problem+json; charset=utf-8} would be looked for under a name it was never
     * filed under. Escaping more than is needed costs nothing and cannot be read wrongly.
     */
    private static final String SAFE = "-._~";

    /**
     * How many times a reply written as a reference is followed before giving up. Documents point
     * at things that point at things, and one pointing at itself would otherwise never finish.
     */
    private static final int MAX_REFERENCE_STEPS = 8;

    private final String text;
    private final JsonValue.JsonObject root;

    private SpecificationDocument(String text, JsonValue.JsonObject root) {
        this.text = text;
        this.root = root;
    }

    /**
     * The document an API model was read from, or nothing if it was not kept or will not be read
     * back. Nothing means replies go unchecked against their declared shape, which is a gap, not a
     * fault of the API.
     */
    static Optional<SpecificationDocument> of(ApiModel api) {
        Objects.requireNonNull(api, "api");
        return api.document().flatMap(text -> {
            try {
                return JsonText.read(text) instanceof JsonValue.JsonObject object
                        ? Optional.of(new SpecificationDocument(text, object))
                        : Optional.empty();
            } catch (JsonException notReadable) {
                return Optional.empty();
            }
        });
    }

    /** The document as text, for whoever checks replies against it. */
    String text() {
        return text;
    }

    /**
     * Which version of OpenAPI the document says it is written in, such as {@code 3.0.3}.
     *
     * <p>It matters because 3.0 and 3.1 say some things differently - most visibly how a value is
     * allowed to be absent - and whoever checks a reply has to be told which set of rules to read
     * the document by.
     */
    Optional<String> openApiVersion() {
        return text(root, "openapi");
    }

    /**
     * Where in the document the shape of this reply's body is written, if it is written at all.
     *
     * @param operation   the operation that was called
     * @param response    the reply the document declares for the status that came back
     * @param contentType which of that reply's declared media types applies
     */
    Optional<String> pointerToSchema(Operation operation, ResponseModel response,
            String contentType) {
        String method = operation.method().name().toLowerCase(Locale.ROOT);
        Optional<JsonValue.JsonObject> responses = member(root, "paths")
                .flatMap(paths -> member(paths, operation.path()))
                .flatMap(path -> member(path, method))
                .flatMap(declared -> member(declared, "responses"));
        if (responses.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> statusKey = keyMatching(responses.get(),
                candidate -> candidate.trim().equalsIgnoreCase(response.status()));
        if (statusKey.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonValue.JsonObject> declared = member(responses.get(), statusKey.get());
        if (declared.isEmpty()) {
            return Optional.empty();
        }
        String trail = "#/paths/" + step(operation.path()) + "/" + step(method)
                + "/responses/" + step(statusKey.get());
        Optional<Named> reply = followReferences(declared.get(), trail);
        if (reply.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonValue.JsonObject> content = member(reply.get().value(), "content");
        if (content.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> contentKey = keyMatching(content.get(),
                candidate -> withoutParameters(candidate).equalsIgnoreCase(contentType));
        if (contentKey.isEmpty()) {
            return Optional.empty();
        }
        boolean present = member(content.get(), contentKey.get())
                .flatMap(declaredContent -> declaredContent.member("schema"))
                .isPresent();
        if (!present) {
            return Optional.empty();
        }
        return Optional.of(reply.get().trail() + "/content/" + step(contentKey.get()) + "/schema");
    }

    /** Something found in the document, and the trail of names that leads to it. */
    private record Named(String trail, JsonValue.JsonObject value) {
    }

    /**
     * Follows a reply that is written as a pointer to one declared elsewhere in the document.
     *
     * <p>Documents very often write their replies once, under {@code components}, and point at them
     * from every operation that sends them - one of the five APIs this tool is measured on does it
     * three hundred times over. Without following the pointer there is nothing under the reply to
     * look at, and every one of those replies would go unchecked without anybody being told.
     *
     * <p>Only pointers within the same document are followed. One naming another file cannot be
     * followed without reading that file, and guessing is worse than saying nothing. A chain is
     * followed a few steps and then given up on, so a document pointing at itself cannot spin.
     */
    private Optional<Named> followReferences(JsonValue.JsonObject declared, String trail) {
        Named here = new Named(trail, declared);
        for (int hop = 0; hop < MAX_REFERENCE_STEPS; hop++) {
            Optional<String> reference = text(here.value(), "$ref");
            if (reference.isEmpty()) {
                return Optional.of(here);
            }
            if (!reference.get().startsWith("#/")) {
                return Optional.empty();
            }
            Optional<Named> target = at(reference.get());
            if (target.isEmpty()) {
                return Optional.empty();
            }
            here = target.get();
        }
        return Optional.empty();
    }

    /**
     * Whatever a trail of names within this document leads to, and the trail written our own way.
     *
     * <p>The rewriting is the point, and it is not tidiness. A document writes these trails however
     * it likes: some write a brace as a brace, others write it as {@code %7B}, which is what a web
     * address requires and what several tools that produce documents emit. Whoever reads the trail
     * afterwards assumes the second, so a trail copied across as it stands is read wrongly - at
     * best finding nothing, at worst being refused outright over a stray per-cent sign. Each name
     * is therefore worked out here and written afresh, so that what leaves this class is always
     * spelled one way.
     *
     * <p>A name is tried exactly as the document wrote it and then, failing that, with its
     * {@code %XX} escapes undone. Trying both is deliberate: the two spellings cannot be told apart
     * by looking, since a name may perfectly well contain a per-cent sign, and only the document
     * itself can settle which was meant - by holding a name of that spelling, or not.
     */
    private Optional<Named> at(String pointer) {
        Named here = new Named("#", root);
        for (String written : pointer.substring(2).split("/", -1)) {
            Optional<Named> next = child(here, unescape(written));
            if (next.isEmpty()) {
                Optional<String> decoded = withoutPercentEscapes(written);
                if (decoded.isPresent()) {
                    next = child(here, unescape(decoded.get()));
                }
            }
            if (next.isEmpty()) {
                return Optional.empty();
            }
            here = next.get();
        }
        return Optional.of(here);
    }

    /** What a parent holds under one name, and the trail that now leads to it. */
    private Optional<Named> child(Named parent, String name) {
        return member(parent.value(), name)
                .map(value -> new Named(parent.trail() + "/" + step(name), value));
    }

    /** A name as the document means it, with the two escapes a trail of names uses undone. */
    private static String unescape(String written) {
        return written.replace("~1", "/").replace("~0", "~");
    }

    /**
     * The same name with its {@code %XX} escapes undone, or nothing if it carries none, or carries
     * one that is not a complete escape. Nothing rather than a complaint: a name RESTest cannot
     * read is a reply it cannot check, which is not the same as evidence about the API.
     *
     * <p>Undone by hand rather than with the reader Java offers for web forms, which also turns a
     * {@code +} into a space. That rule belongs to forms and not to names, and applying it here
     * would quietly rename anything holding a plus sign. Package-private so it can be checked on
     * its own: it is small, it is fiddly, and everything downstream of it trusts it.
     */
    static Optional<String> withoutPercentEscapes(String written) {
        if (written.indexOf('%') < 0) {
            return Optional.empty();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(written.length());
        int at = 0;
        while (at < written.length()) {
            char c = written.charAt(at);
            if (c != '%') {
                bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
                at++;
                continue;
            }
            if (at + 2 >= written.length()) {
                return Optional.empty();
            }
            int high = Character.digit(written.charAt(at + 1), 16);
            int low = Character.digit(written.charAt(at + 2), 16);
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            bytes.write(high * 16 + low);
            at += 3;
        }
        return Optional.of(bytes.toString(StandardCharsets.UTF_8));
    }

    private static Optional<String> text(JsonValue.JsonObject parent, String name) {
        return parent.member(name)
                .filter(JsonValue.JsonString.class::isInstance)
                .map(value -> ((JsonValue.JsonString) value).value());
    }

    private static Optional<JsonValue.JsonObject> member(JsonValue.JsonObject parent, String name) {
        return parent.member(name)
                .filter(JsonValue.JsonObject.class::isInstance)
                .map(JsonValue.JsonObject.class::cast);
    }

    private static Optional<String> keyMatching(JsonValue.JsonObject parent,
            Predicate<String> matches) {
        return parent.members().keySet().stream().filter(matches).findFirst();
    }

    /** A media type without what follows its first semicolon: {@code text/plain}, not a charset. */
    private static String withoutParameters(String mediaType) {
        int parameters = mediaType.indexOf(';');
        return (parameters < 0 ? mediaType : mediaType.substring(0, parameters)).trim();
    }

    /**
     * One name on the trail, written so that it survives being part of one.
     *
     * <p>Two escapings, in this order and for two different reasons. A name containing {@code /}
     * would otherwise look like two names, and one containing {@code ~} like the start of that
     * first escape, so those become {@code ~1} and {@code ~0} - the standard way of writing a trail
     * of names. Then anything a web address may not carry as it stands, which for a specification
     * means the braces around a path's variable parts, is written as {@code %} and its number.
     */
    private static String step(String name) {
        String pointer = name.replace("~", "~0").replace("/", "~1");
        StringBuilder escaped = new StringBuilder(pointer.length());
        for (byte b : pointer.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (Character.isLetterOrDigit(c) && c < 128 || SAFE.indexOf(c) >= 0) {
                escaped.append(c);
            } else {
                escaped.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return escaped.toString();
    }
}
