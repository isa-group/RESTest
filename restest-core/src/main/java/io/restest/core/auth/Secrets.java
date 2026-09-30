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
package io.restest.core.auth;

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.SequenceStep;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The keys a run was handed, and the one thing done with them after a request has gone out: every
 * appearance of one of them, in anything that is kept or written, replaced by the text that stands
 * for it.
 *
 * <p>An exchange with the API is kept in full - shown on the screen when it is a fault, written into
 * the report with a {@code curl} command that repeats it, stored with the run when that is asked for,
 * remembered by the parts of the tool that learn from replies. The key is in that exchange, where it
 * was put on the way out and wherever the API repeated it back. So each exchange passes through here
 * first, before anything else sees it, and comes out with the key gone and something like
 * {@code REDACTED-AUTH.api_key} in its place - text that says a key was there, and which one.
 *
 * <p>A key is looked for in every way it could have been written: as it is, the way an address or a
 * web form writes it, the way a JSON document or a web page escapes it. And not only whole: an API
 * that repeats an address back may break it across two lines with something else in between, so
 * any piece of a key eight characters or longer is hidden wherever it is, and what is left of a key
 * cut off at the very end of a reply kept only in part is hidden from four. Everything is replaced
 * in one pass over the text that never looks again at what it has written, so a key can never be
 * found inside the text put in the place of another one.
 *
 * <p>This must not fail, because an exchange that could not be passed on would be lost to every
 * report. If something here goes wrong nonetheless, the exchange is passed on with everything that
 * could hold a key - every header value, every body, the address - replaced whole, and the number of
 * times that happened is kept so a run can say so.
 */
public final class Secrets {

    /**
     * The text every replacement begins with, so that anything reading what a run kept can tell that
     * a key was taken out of it: the part of the tool that judges replies against the document, for
     * one, which does not judge a reply that was changed.
     */
    public static final String MARKER = "REDACTED-AUTH";

    /**
     * How much of a key has to be left at the end of a reply that was cut short before it is taken
     * for the start of the key and hidden. A fact about how much of a key is worth anything to
     * somebody, not something a run might want to choose.
     */
    private static final int SHORTEST_HALF_KEY = 4;

    /**
     * How long a piece of a key, found apart from the rest of it, has to be to be hidden: long enough
     * that a piece this long turning up in ordinary text by chance is next to impossible, and short
     * enough that a key an API breaks in two leaves little of itself behind. A safeguard of the same
     * kind as the one above.
     */
    private static final int SHORTEST_PIECE = 8;

    /** The number each character of a piece is weighed by, for the pieces' hashes. */
    private static final long BASE = 1_000_003L;

    /** What the first character of a piece is weighed by: the base to the piece's length less one. */
    private static final long FIRST_WEIGHT = weightOfTheFirst();

    /** Every way each key could be written, longest first, each with the text that replaces it. */
    private final List<Spelling> spellings;

    private final AtomicLong hiddenWhole = new AtomicLong();

    private record Spelling(String text, String mask) {
    }

    /**
     * A stretch of text to replace, with what replaces it. Whole when it is a whole way of writing a
     * key: of two keys, one the beginning of the other, a stretch that is the whole of the shorter
     * is named after the shorter.
     */
    private record Span(int start, int end, String mask, boolean whole) {
    }

    /** Where a piece of a key sits in one way of writing it. */
    private record Anchor(int spelling, int offset) {
    }

    /** Every piece of every way of writing a key long enough to be looked for, by its hash. */
    private final Map<Long, List<Anchor>> pieces;

    /**
     * One bit for each hash a piece of a key could have, folded into a table small enough to stay
     * in a processor's cache: a window of text whose bit is not set cannot be a piece, and is passed
     * over without looking anything up. Nearly every window of a long reply is.
     */
    private final long[] mightBeAPiece = new long[FOLDED_BITS / Long.SIZE];

    /** How many bits the hashes of the pieces are folded into. */
    private static final int FOLDED_BITS = 1 << 16;

    Secrets(Collection<Secret> secrets) {
        Objects.requireNonNull(secrets, "secrets");
        Map<String, String> written = new LinkedHashMap<>();
        for (Secret secret : secrets) {
            String value = secret.value();
            for (String spelling : List.of(value,
                    Encoding.percent(value),
                    Encoding.percentInLowerCase(value),
                    Encoding.formStyle(value),
                    Encoding.json(value, false),
                    Encoding.json(value, true),
                    Encoding.jsonEscapingAllPunctuation(value, true),
                    Encoding.jsonEscapingAllPunctuation(value, false),
                    Encoding.html(value, false),
                    Encoding.html(value, true))) {
                written.putIfAbsent(spelling, secret.mask());
            }
        }
        List<Spelling> ordered = new ArrayList<>();
        written.forEach((text, mask) -> ordered.add(new Spelling(text, mask)));
        ordered.sort(Comparator.comparingInt((Spelling spelling) -> spelling.text().length())
                .reversed());
        this.spellings = List.copyOf(ordered);
        Map<Long, List<Anchor>> anchors = new java.util.HashMap<>();
        for (int index = 0; index < spellings.size(); index++) {
            String text = spellings.get(index).text();
            for (int offset = 0; offset + SHORTEST_PIECE <= text.length(); offset++) {
                anchors.computeIfAbsent(hashOf(text, offset), ignored -> new ArrayList<>())
                        .add(new Anchor(index, offset));
            }
        }
        anchors.replaceAll((hash, list) -> List.copyOf(list));
        this.pieces = Map.copyOf(anchors);
        for (long hash : pieces.keySet()) {
            int folded = folded(hash);
            mightBeAPiece[folded >>> 6] |= 1L << folded;
        }
    }

    private static int folded(long hash) {
        return (int) (hash ^ (hash >>> 29) ^ (hash >>> 47)) & (FOLDED_BITS - 1);
    }

    /** Nothing to hide: what a run is when it was handed no key. */
    static Secrets none() {
        return new Secrets(List.of());
    }

    /** Whether there is anything to hide at all. */
    boolean isEmpty() {
        return spellings.isEmpty();
    }

    /**
     * How many exchanges could not have their keys picked out, and were passed on with everything
     * that could have held one replaced whole instead. Nothing should ever make this more than
     * nought; it is counted so that a run can say so if something does.
     */
    public long hiddenWhole() {
        return hiddenWhole.get();
    }

    /**
     * The exchange with every appearance of a key hidden, under the same identity, or the very same
     * exchange when there was nothing to hide. Never fails: see the class's own description.
     */
    Interaction hidden(Interaction interaction) {
        Objects.requireNonNull(interaction, "interaction");
        if (spellings.isEmpty()) {
            return interaction;
        }
        try {
            TestCase testCase = hidden(interaction.testCase());
            HttpRequestRecord request = hidden(interaction.request());
            InteractionOutcome outcome = hidden(interaction.outcome());
            if (testCase == interaction.testCase() && request == interaction.request()
                    && outcome == interaction.outcome()) {
                return interaction;
            }
            return new Interaction(interaction.id(), testCase, request, outcome,
                    interaction.sentAt(), interaction.elapsed());
        } catch (RuntimeException | StackOverflowError couldNotPickThemOut) {
            hiddenWhole.incrementAndGet();
            return hiddenWhole(interaction);
        }
    }

    /** A text with every appearance of a key replaced. */
    String hidden(String text) {
        return hidden(text, false);
    }

    private String hidden(String text, boolean mayEndHalfway) {
        if (text.isEmpty() || spellings.isEmpty()) {
            return text;
        }
        List<Span> spans = new ArrayList<>();
        for (Spelling spelling : spellings) {
            // A way of writing a key shorter than a piece can only be found whole.
            if (spelling.text().length() >= SHORTEST_PIECE) {
                continue;
            }
            int from = 0;
            int found;
            while ((found = text.indexOf(spelling.text(), from)) >= 0) {
                spans.add(new Span(found, found + spelling.text().length(), spelling.mask(),
                        true));
                from = found + 1;
            }
        }
        piecesOfAKey(text, spans);
        if (mayEndHalfway) {
            halfAKeyAtTheEnd(text, spans).ifPresent(spans::add);
        }
        if (spans.isEmpty()) {
            return text;
        }
        spans.sort(Comparator.comparingInt(Span::start)
                .thenComparing(Comparator.comparingInt(Span::end).reversed())
                .thenComparing(span -> !span.whole()));
        StringBuilder written = new StringBuilder(text.length());
        int cursor = 0;
        int next = 0;
        while (next < spans.size()) {
            Span first = spans.get(next);
            int end = first.end();
            next++;
            while (next < spans.size() && spans.get(next).start() < end) {
                end = Math.max(end, spans.get(next).end());
                next++;
            }
            written.append(text, cursor, first.start()).append(first.mask());
            cursor = end;
        }
        written.append(text, cursor, text.length());
        return written.toString();
    }

    /**
     * Every stretch of the text that is a piece of a key at least {@link #SHORTEST_PIECE} long, a
     * whole key included, found by sliding a window of that length along the text once and, where
     * what is under the window is a piece of some key, stretching it as far as the text and that key
     * go on agreeing in both directions.
     */
    private void piecesOfAKey(String text, List<Span> spans) {
        if (pieces.isEmpty() || text.length() < SHORTEST_PIECE) {
            return;
        }
        int[] lastStart = new int[spellings.size()];
        int[] lastEnd = new int[spellings.size()];
        Arrays.fill(lastStart, -1);
        long hash = hashOf(text, 0);
        for (int at = 0; ; at++) {
            int folded = folded(hash);
            List<Anchor> found = (mightBeAPiece[folded >>> 6] & (1L << folded)) == 0
                    ? null : pieces.get(hash);
            if (found != null) {
                for (Anchor anchor : found) {
                    String spelling = spellings.get(anchor.spelling()).text();
                    if (!text.regionMatches(at, spelling, anchor.offset(), SHORTEST_PIECE)) {
                        continue;
                    }
                    int start = at;
                    int from = anchor.offset();
                    while (start > 0 && from > 0
                            && text.charAt(start - 1) == spelling.charAt(from - 1)) {
                        start--;
                        from--;
                    }
                    int end = at + SHORTEST_PIECE;
                    int to = anchor.offset() + SHORTEST_PIECE;
                    while (end < text.length() && to < spelling.length()
                            && text.charAt(end) == spelling.charAt(to)) {
                        end++;
                        to++;
                    }
                    if (start != lastStart[anchor.spelling()] || end != lastEnd[anchor.spelling()]) {
                        lastStart[anchor.spelling()] = start;
                        lastEnd[anchor.spelling()] = end;
                        spans.add(new Span(start, end, spellings.get(anchor.spelling()).mask(),
                                from == 0 && to == spelling.length()));
                    }
                }
            }
            if (at + SHORTEST_PIECE >= text.length()) {
                return;
            }
            hash = (hash - text.charAt(at) * FIRST_WEIGHT) * BASE + text.charAt(at + SHORTEST_PIECE);
        }
    }

    /** The hash of the piece of the text that starts at the given place. */
    private static long hashOf(String text, int from) {
        long hash = 0;
        for (int i = from; i < from + SHORTEST_PIECE; i++) {
            hash = hash * BASE + text.charAt(i);
        }
        return hash;
    }

    private static long weightOfTheFirst() {
        long weight = 1;
        for (int i = 1; i < SHORTEST_PIECE; i++) {
            weight *= BASE;
        }
        return weight;
    }

    /**
     * The longest beginning of a key, at least a few characters long, that the text ends with - what
     * is left of a key in a reply that was cut short in the middle of it.
     */
    private Optional<Span> halfAKeyAtTheEnd(String text, List<Span> whole) {
        int lastWholeEnd = whole.stream().mapToInt(Span::end).max().orElse(0);
        Span longest = null;
        for (Spelling spelling : spellings) {
            int most = Math.min(spelling.text().length() - 1, text.length() - lastWholeEnd);
            for (int length = most; length >= SHORTEST_HALF_KEY; length--) {
                if (text.endsWith(spelling.text().substring(0, length))) {
                    if (longest == null || length > longest.end() - longest.start()) {
                        longest = new Span(text.length() - length, text.length(), spelling.mask(),
                                false);
                    }
                    break;
                }
            }
        }
        return Optional.ofNullable(longest);
    }

    /**
     * Bytes with every appearance of a key replaced, read one byte to one character so that bytes
     * that are not text come out exactly as they went in. The same array when nothing was there.
     */
    private byte[] hidden(byte[] bytes, boolean mayEndHalfway) {
        String read = new String(bytes, StandardCharsets.ISO_8859_1);
        String written = hidden(read, mayEndHalfway);
        return written.equals(read) ? bytes : written.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * A body with every appearance of a key replaced. One the API stopped sending, or that was kept
     * only in part, may end halfway through a key; and one kept in part never grows past what was
     * kept, so that it still says, truthfully, that it is only part of what arrived.
     */
    private Payload hidden(Payload payload, boolean stoppedEarly) {
        byte[] content = payload.content();
        byte[] written = hidden(content, stoppedEarly || payload.truncated());
        String mediaType = hidden(payload.mediaType());
        if (written == content && mediaType.equals(payload.mediaType())) {
            return payload;
        }
        if (payload.wireLength().isPresent() && written.length > content.length) {
            written = Arrays.copyOf(written, content.length);
        }
        return new Payload(written, mediaType, payload.wireLength());
    }

    private HttpRequestRecord hidden(HttpRequestRecord request) {
        String url = hidden(request.url());
        List<Header> headers = hidden(request.headers());
        Optional<Payload> body = request.body().map(payload -> hidden(payload, false));
        if (url.equals(request.url()) && headers == request.headers()
                && same(body, request.body())) {
            return request;
        }
        return new HttpRequestRecord(request.method(), url, headers, body);
    }

    private InteractionOutcome hidden(InteractionOutcome outcome) {
        return switch (outcome) {
            case InteractionOutcome.Answered answered -> {
                HttpResponseRecord response = answered.response();
                StatusLine statusLine = hidden(response.statusLine());
                List<Header> headers = hidden(response.headers());
                Optional<Payload> body = response.body().map(payload -> hidden(payload, false));
                yield statusLine == response.statusLine() && headers == response.headers()
                        && same(body, response.body())
                        ? outcome
                        : new InteractionOutcome.Answered(
                                new HttpResponseRecord(statusLine, headers, body));
            }
            case InteractionOutcome.MalformedResponse malformed -> {
                String reason = hidden(malformed.reason());
                Optional<StatusLine> statusLine = malformed.statusLine().map(this::hidden);
                List<Header> headers = hidden(malformed.headers());
                Optional<Payload> partial = malformed.partial().map(payload -> hidden(payload, true));
                yield reason.equals(malformed.reason()) && same(statusLine, malformed.statusLine())
                        && headers == malformed.headers() && same(partial, malformed.partial())
                        ? outcome
                        : new InteractionOutcome.MalformedResponse(reason, statusLine, headers,
                                partial);
            }
            case InteractionOutcome.TransportFailure failure -> {
                String reason = hidden(failure.reason());
                yield reason.equals(failure.reason())
                        ? outcome : new InteractionOutcome.TransportFailure(reason);
            }
        };
    }

    private StatusLine hidden(StatusLine statusLine) {
        Optional<String> reason = statusLine.reasonPhrase().map(this::hidden);
        return same(reason, statusLine.reasonPhrase())
                ? statusLine
                : new StatusLine(statusLine.statusCode(), reason, statusLine.protocolVersion());
    }

    private List<Header> hidden(List<Header> headers) {
        List<Header> written = new ArrayList<>(headers.size());
        boolean changed = false;
        for (Header header : headers) {
            String name = hidden(header.name());
            String value = hidden(header.value());
            changed |= !name.equals(header.name()) || !value.equals(header.value());
            written.add(Header.of(name, value));
        }
        return changed ? List.copyOf(written) : headers;
    }

    /**
     * A test case with every value it carries and every description of how it was made hidden, or
     * the same test case when none of them holds a key. The names of its inputs are never touched:
     * a key is a value, and a name changed here could clash with another.
     */
    TestCase hidden(TestCase testCase) {
        List<ParameterValue> values = new ArrayList<>(testCase.parameterValues().size());
        boolean changed = false;
        for (ParameterValue value : testCase.parameterValues()) {
            JsonValue written = hidden(value.value());
            ValueOrigin origin = hidden(value.origin());
            changed |= written != value.value() || origin != value.origin();
            values.add(new ParameterValue(value.name(), value.location(), written, origin));
        }
        Optional<BodyValue> body = testCase.body().map(this::hidden);
        Optional<Mutation> mutation = testCase.mutation().map(this::hidden);
        Optional<SequenceStep> sequence = testCase.sequence().map(this::hidden);
        if (!changed && same(body, testCase.body()) && same(mutation, testCase.mutation())
                && same(sequence, testCase.sequence())) {
            return testCase;
        }
        return new TestCase(testCase.id(), testCase.operation(), values, body, testCase.intent(),
                mutation, sequence);
    }

    private BodyValue hidden(BodyValue body) {
        JsonValue value = hidden(body.value());
        ValueOrigin origin = hidden(body.origin());
        Optional<String> sentAs = body.sentAs().map(this::hidden);
        return value == body.value() && origin == body.origin() && same(sentAs, body.sentAs())
                ? body : new BodyValue(body.mediaType(), value, origin, sentAs);
    }

    private Mutation hidden(Mutation mutation) {
        String path = hidden(mutation.path());
        String description = hidden(mutation.description());
        return path.equals(mutation.path()) && description.equals(mutation.description())
                ? mutation
                : new Mutation(mutation.of(), mutation.operator(), mutation.location(), path,
                        description);
    }

    private SequenceStep hidden(SequenceStep step) {
        String description = hidden(step.description());
        return description.equals(step.description())
                ? step
                : new SequenceStep(step.shape(), step.step(), step.follows(), description);
    }

    private ValueOrigin hidden(ValueOrigin origin) {
        return switch (origin) {
            case ValueOrigin.Declared declared -> declared;
            case ValueOrigin.Generated generated -> {
                String source = hidden(generated.source());
                yield source.equals(generated.source())
                        ? generated : new ValueOrigin.Generated(source);
            }
            case ValueOrigin.Derived derived -> {
                String description = hidden(derived.description());
                yield description.equals(derived.description())
                        ? derived : new ValueOrigin.Derived(derived.from(), description);
            }
        };
    }

    /**
     * A value with every text in it hidden, and every number whose digits hold a key turned into
     * the text that replaces it, since a number cannot hold letters. The names of an object's
     * members are left as they are.
     */
    JsonValue hidden(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString text -> {
                String written = hidden(text.value());
                yield written.equals(text.value()) ? value : JsonValue.of(written);
            }
            case JsonValue.JsonNumber number -> {
                String digits = number.value().toPlainString();
                String written = hidden(digits);
                yield written.equals(digits) ? value : JsonValue.of(written);
            }
            case JsonValue.JsonArray array -> {
                List<JsonValue> elements = new ArrayList<>(array.elements().size());
                boolean changed = false;
                for (JsonValue element : array.elements()) {
                    JsonValue written = hidden(element);
                    changed |= written != element;
                    elements.add(written);
                }
                yield changed ? JsonValue.array(elements) : value;
            }
            case JsonValue.JsonObject object -> {
                Map<String, JsonValue> members = new LinkedHashMap<>();
                boolean changed = false;
                for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
                    JsonValue written = hidden(member.getValue());
                    changed |= written != member.getValue();
                    members.put(member.getKey(), written);
                }
                yield changed ? JsonValue.object(members) : value;
            }
            case JsonValue.JsonBoolean ignored -> value;
            case JsonValue.JsonNull ignored -> value;
        };
    }

    /**
     * The exchange with everything that could hold a key replaced whole: what is passed on when the
     * keys could not be picked out of it. It says which operation was tried and what the API
     * answered with, and nothing else.
     */
    static Interaction hiddenWhole(Interaction interaction) {
        TestCase original = interaction.testCase();
        List<ParameterValue> values = original.parameterValues().stream()
                .map(value -> new ParameterValue(value.name(), value.location(),
                        JsonValue.of(MARKER), ValueOrigin.DECLARED))
                .toList();
        Optional<BodyValue> body = original.body().map(value -> new BodyValue(value.mediaType(),
                JsonValue.of(MARKER), ValueOrigin.DECLARED));
        Optional<Mutation> mutation = original.mutation().map(change -> new Mutation(change.of(),
                change.operator(), change.location(), MARKER, MARKER));
        Optional<SequenceStep> sequence = original.sequence().map(step -> new SequenceStep(
                step.shape(), step.step(), step.follows(), MARKER));
        TestCase testCase = new TestCase(original.id(), original.operation(), values, body,
                original.intent(), mutation, sequence);
        HttpRequestRecord request = new HttpRequestRecord(interaction.request().method(), MARKER,
                everyValueHidden(interaction.request().headers()), Optional.empty());
        String why = "RESTest could not pick the key out of this exchange, so it is kept without "
                + "its details";
        InteractionOutcome outcome = switch (interaction.outcome()) {
            case InteractionOutcome.Answered answered -> new InteractionOutcome.Answered(
                    new HttpResponseRecord(StatusLine.of(answered.response().statusCode()),
                            everyValueHidden(answered.response().headers()), Optional.empty()));
            case InteractionOutcome.MalformedResponse malformed ->
                    new InteractionOutcome.MalformedResponse(why,
                            malformed.statusLine().map(line -> StatusLine.of(line.statusCode())),
                            everyValueHidden(malformed.headers()), Optional.empty());
            case InteractionOutcome.TransportFailure ignored ->
                    new InteractionOutcome.TransportFailure(why);
        };
        return new Interaction(interaction.id(), testCase, request, outcome, interaction.sentAt(),
                interaction.elapsed());
    }

    private static List<Header> everyValueHidden(List<Header> headers) {
        return headers.stream().map(header -> Header.of(header.name(), MARKER)).toList();
    }

    private static <T> boolean same(Optional<T> one, Optional<T> other) {
        return one.isPresent() == other.isPresent()
                && (one.isEmpty() || one.get() == other.get());
    }
}
