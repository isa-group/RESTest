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

import io.restest.core.event.RunEvent;
import io.restest.core.event.RunListener;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.Payload;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonException;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.settings.MemorySettings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * What the API has already shown us, kept as the run goes along.
 *
 * <p>Everything else that suggests a value reads the document. This one listens to the API. Every
 * time a request comes back with a reply the API is happy with, what that reply contained is kept:
 * the identifier of a pet that really exists, an owner's real e-mail address, a whole owner exactly
 * as the API writes one down. The next request that needs a pet's identifier can then send one that
 * exists, instead of inventing a number and being told there is no such pet.
 *
 * <p>It is kept three ways, because three different questions get asked of it. The first two:
 *
 * <ul>
 *   <li><b>Under the name of the thing.</b> Every named piece of every reply, however deep inside
 *       it, goes under that name: a {@code petId} seen anywhere answers the next time anything
 *       called {@code petId} is wanted, whether that is a parameter or a property four levels
 *       inside a body. This is where nearly all of the value is - of the request bodies in our
 *       corpus of fifty specifications, 89% of the individual values inside them carry a name some
 *       reply also carries.</li>
 *   <li><b>Under the name of the shape.</b> A reply the document says is an {@code Owner} is also
 *       kept whole, under {@code Owner}, so that an operation asking to be sent an {@code Owner}
 *       can be sent one the API itself produced rather than one assembled from nothing.</li>
 * </ul>
 *
 * <p>Both of those are ordinary {@link Dictionary lists of values}, of the same kind somebody can
 * write in a file - the only difference is who fills them in. Nothing that asks them a question has
 * to know that these two were filled in by watching the API rather than by being read off a disk.
 *
 * <p>The third is for the gaps in a web address, such as {@code {petTypeId}} in
 * {@code /pettypes/{petTypeId}}, because the name is often no help there: the API calls a pet
 * type's identifier {@code id}, not {@code petTypeId}. So what the API returned at an address is
 * also kept under <b>the kind of thing that address is about</b> - everything {@code GET /pettypes}
 * lists goes under "pet type" - and the {@link ObservedValueProvider} fills {@code {petTypeId}}
 * with the {@code id} of one of them. This one is not a list of values anybody could write in a
 * file, because what it is keyed by is worked out from the document's addresses rather than
 * written down, and it is kept only when {@link MemorySettings#identifiersByResource()} is on.
 *
 * <p>It hears about replies through the run's stream of announcements rather than by reading the
 * record of the run afterwards, and it does so on purpose: what it wants is <em>recent</em>, not
 * complete. An identifier seen forty minutes ago may have been deleted since, so only the last
 * handful of values under any one name are kept and older ones are let go.
 *
 * <p>One consequence is worth stating plainly, because it changes a promise the tool otherwise
 * makes. A run that draws on this cannot be repeated by giving it the same starting number again:
 * the values it sends depend on what the API answered, and an API answers differently on a
 * different day. The same number gets a similar run rather than the same one, and what is kept of
 * the run that did happen - every request and every reply - is what somebody goes back to.
 */
public final class ObservedValues implements RunListener {

    /** The word a plan uses to ask for this source. */
    public static final String NAME = "observed";

    private final ApiModel model;
    private final MemorySettings settings;
    private final Remembered underTheirOwnNames;
    private final Remembered underTheNameOfTheirShape;
    private final Resources underTheKindOfThingTheyAre;

    /**
     * A memory for this API, of the size RESTest uses when nobody has said otherwise.
     *
     * @param model the API being tested, which is what says what shape a reply has
     */
    public ObservedValues(ApiModel model) {
        this(model, MemorySettings.defaults());
    }

    /**
     * A memory for this API, of the size asked for.
     *
     * @param model the API being tested, which is what says what shape a reply has
     * @param settings how much is remembered: how many values under one name or things of one
     *     kind, how many names or kinds, how large a value and a reply, how far into a reply the
     *     search goes, and whether things are kept by their kind at all
     */
    public ObservedValues(ApiModel model, MemorySettings settings) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.underTheirOwnNames = new Remembered(ValueDictionary.Keying.NAME, settings);
        this.underTheNameOfTheirShape = new Remembered(ValueDictionary.Keying.SCHEMA, settings);
        this.underTheKindOfThingTheyAre = new Resources(settings);
    }

    /** How much this memory keeps, which is also what decides how large a value may be. */
    MemorySettings settings() {
        return settings;
    }

    /** Every named piece of every reply, under its own name. */
    Remembered underTheirOwnNames() {
        return underTheirOwnNames;
    }

    /** The replies the document gives a name to, kept whole under that name. */
    Remembered underTheNameOfTheirShape() {
        return underTheNameOfTheirShape;
    }

    /**
     * The things each kind of address returned, for filling the gaps in addresses of that kind.
     * Empty for the whole run when {@link MemorySettings#identifiersByResource()} is off.
     */
    Resources underTheKindOfThingTheyAre() {
        return underTheKindOfThingTheyAre;
    }

    @Override
    public void on(RunEvent event) {
        Objects.requireNonNull(event, "event");
        if (!(event instanceof RunEvent.InteractionCompleted completed)) {
            return;
        }
        Interaction interaction = completed.interaction();
        HttpResponseRecord response = interaction.response().orElse(null);
        if (response == null || !theApiWasHappy(response.statusCode())) {
            return;
        }
        readable(response).ifPresent(reply -> {
            OperationId operation = interaction.testCase().operation();
            remember(reply, shapeOfOneThingIn(operation, response), interaction.id(), 0);
            if (settings.identifiersByResource()) {
                kindOfThingReturnedBy(operation).ifPresent(kind ->
                        rememberTheThingsIn(reply, kind, interaction.id(), 0));
            }
        });
    }

    /**
     * The kind of thing an operation's reply is about, when it can be told from its address.
     *
     * <p>Not for a deletion: what a {@code DELETE} sends back is the thing that has just stopped
     * existing, and keeping it would add an identifier that no longer works. An identifier kept
     * earlier, from a list or a read, is not forgotten when the thing is deleted afterwards - it
     * ages out like any other value here.
     */
    private Optional<String> kindOfThingReturnedBy(OperationId operation) {
        return model.operation(operation)
                .filter(known -> known.method() != HttpMethod.DELETE)
                .flatMap(known -> kindOfThingAt(known.path()));
    }

    /**
     * Keeps the things one reply is made of under the kind of thing they are.
     *
     * <p>A list is a list of things, each kept. An object is a thing. But an object with nothing
     * in it named like an identifier is usually a wrapper around the things rather than one of
     * them - {@code {"data": [...]}}, or {@code {"_embedded": {"pets": [...]}}} - so what is inside
     * it is looked at as well, as far down as a reply is ever read. An object that does carry an
     * identifier is a thing, and what is inside it - a pet's owner, an owner's pets - is some other
     * kind of thing and is not filed under this one.
     */
    private void rememberTheThingsIn(JsonValue reply, String kind, InteractionId from, int depth) {
        if (depth > settings.asDeepAsAReplyIsRead()) {
            return;
        }
        switch (reply) {
            case JsonValue.JsonArray list -> list.elements().forEach(element ->
                    rememberTheThingsIn(element, kind, from, depth + 1));
            case JsonValue.JsonObject thing -> {
                Map<String, JsonValue> single = new LinkedHashMap<>();
                thing.members().forEach((name, value) -> {
                    if ((value instanceof JsonValue.JsonString
                            || value instanceof JsonValue.JsonNumber)
                            && smallEnoughToSend(value)) {
                        single.put(name, value);
                    }
                });
                if (!single.isEmpty()) {
                    underTheKindOfThingTheyAre.remember(kind,
                            new JsonValue.JsonObject(single), from);
                }
                // Judged by the names the thing came with rather than by what was kept of it: a
                // thing whose identifier is empty or too long to keep is still a thing, not a
                // wrapper, and what is inside it is still some other kind.
                if (thing.members().keySet().stream()
                        .noneMatch(ObservedValues::looksLikeAnIdentifier)) {
                    for (JsonValue inside : thing.members().values()) {
                        if (inside instanceof JsonValue.JsonObject
                                || inside instanceof JsonValue.JsonArray) {
                            rememberTheThingsIn(inside, kind, from, depth + 1);
                        }
                    }
                }
            }
            default -> { }
        }
    }

    /**
     * The kind of thing an address is about: the last fixed part of it, once the gaps at its end
     * are left off. {@code /owners/{ownerId}/pets/{petId}} and {@code /pets} are both about pets.
     */
    static Optional<String> kindOfThingAt(String path) {
        List<String> parts = partsOf(path);
        for (int at = parts.size() - 1; at >= 0; at--) {
            if (!isAGap(parts.get(at))) {
                return kindOfThingNamed(parts.get(at));
            }
        }
        return Optional.empty();
    }

    /**
     * The fixed part of an address just before a gap, as the document writes it: {@code pettypes}
     * before {@code {petTypeId}} in {@code /pettypes/{petTypeId}}. Nothing when the gap comes
     * first or follows another gap.
     */
    static Optional<String> fixedPartBefore(String path, String gap) {
        List<String> parts = partsOf(path);
        int at = parts.indexOf("{" + gap + "}");
        return at > 0 && !isAGap(parts.get(at - 1)) ? Optional.of(parts.get(at - 1))
                : Optional.empty();
    }

    /**
     * The kind of thing whose identifier goes in a gap of an address: what the fixed part just
     * before the gap names. {@code {petTypeId}} in {@code /pettypes/{petTypeId}} is a pet type's.
     */
    static Optional<String> kindOfThingBefore(String path, String gap) {
        return fixedPartBefore(path, gap).flatMap(ObservedValues::kindOfThingNamed);
    }

    /**
     * A name with the ending identifiers are written with taken off: {@code pet} out of
     * {@code petId}, {@code hospital} out of {@code hospital_id}. Nothing for a name that does not
     * end that way, or that is nothing but the ending.
     */
    static Optional<String> nameWithoutTheIdentifierEnding(String name) {
        for (String ending : IDENTIFIER_ENDINGS) {
            if (name.endsWith(ending) && name.length() > ending.length()) {
                return Optional.of(name.substring(0, name.length() - ending.length()));
            }
        }
        return Optional.empty();
    }

    /**
     * The kind of thing a gap's own name says it is the identifier of: {@code petId} is a pet's.
     * Nothing for a name that does not end in one of the ways identifiers are written.
     */
    static Optional<String> kindOfThingInTheName(String gap) {
        return nameWithoutTheIdentifierEnding(gap).flatMap(ObservedValues::kindOfThingNamed);
    }

    /**
     * One spelling for a kind of thing, whichever way it was written: capitals ignored, anything
     * but letters and digits left out, and plural and singular brought to the same word -
     * {@code pet-types}, {@code PetTypes} and {@code pettype} are all one kind, and so are
     * {@code movies} and {@code movie}, {@code boxes} and {@code box}, {@code statuses} and
     * {@code status}.
     *
     * <p>The word it comes to is not always an English word - {@code movies} becomes
     * {@code movy} - and does not have to be: it is only ever compared with another word that went
     * through the same rules. A word in another language may keep its plural, as {@code hospitais}
     * does, and every address that says {@code hospitais} still becomes the same thing.
     */
    static Optional<String> kindOfThingNamed(String written) {
        StringBuilder kept = new StringBuilder();
        written.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(kept::appendCodePoint);
        String word = singular(kept.toString());
        return word.isEmpty() ? Optional.empty() : Optional.of(word);
    }

    /**
     * The plural and the singular of one word brought to the same spelling, by the few rules
     * English plurals mostly follow. Both sides of any comparison go through this, so what
     * matters is that {@code movies} and {@code movie} agree, not what either becomes.
     */
    private static String singular(String word) {
        if (word.length() > 3 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        for (String ending : PLURALS_ENDING_IN_ES) {
            if (word.length() > ending.length() && word.endsWith(ending)) {
                return word.substring(0, word.length() - 2);
            }
        }
        String without = word;
        if (word.length() > 1 && word.endsWith("s") && !word.endsWith("ss")
                && !word.endsWith("us") && !word.endsWith("is")) {
            without = word.substring(0, word.length() - 1);
        }
        if (without.length() > 2 && without.endsWith("ie")) {
            return without.substring(0, without.length() - 2) + "y";
        }
        return without;
    }

    /** The endings after which a plural adds -es rather than -s: addresses, boxes, statuses. */
    private static final List<String> PLURALS_ENDING_IN_ES =
            List.of("sses", "xes", "zes", "ches", "shes", "uses");

    /**
     * Whether a name is written the way identifiers are: {@code id} or {@code _id}, or ending in
     * {@code Id}, {@code ID}, {@code _id}, {@code -id}, {@code _ID} or {@code -ID}.
     */
    static boolean looksLikeAnIdentifier(String name) {
        return isABareIdentifier(name) || nameWithoutTheIdentifierEnding(name).isPresent();
    }

    /**
     * Whether a property is named the way the identifier of this kind of thing is: {@code id} or
     * {@code _id}, or the kind followed by id - {@code petId}, {@code pet_id} or {@code petID} for
     * a pet.
     */
    static boolean isTheIdentifierOf(String property, String kind) {
        return isABareIdentifier(property)
                || kindOfThingInTheName(property).filter(kind::equals).isPresent();
    }

    private static boolean isABareIdentifier(String name) {
        return name.equalsIgnoreCase("id") || name.equalsIgnoreCase("_id");
    }

    /** How identifiers are written at the end of a name. A fact about spelling, not a choice. */
    private static final List<String> IDENTIFIER_ENDINGS =
            List.of("Id", "ID", "_id", "-id", "_ID", "-ID");

    private static List<String> partsOf(String path) {
        return Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toList();
    }

    private static boolean isAGap(String part) {
        return part.contains("{");
    }

    /**
     * The reply as a value, when there is one worth reading.
     *
     * <p>Only a reply the API was happy with, only one written as JSON, and only one small enough
     * to be a thing rather than a listing. A reply that broke off halfway is left alone too: half a
     * JSON document is not a value, and the half that arrived cannot be told apart from a whole one
     * that happens to be invalid.
     */
    private Optional<JsonValue> readable(HttpResponseRecord response) {
        Payload body = response.body().orElse(null);
        if (body == null || body.truncated() || body.size() == 0
                || body.size() > settings.longestReplyRead() || !isJson(body.mediaType())) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    JsonText.readFromAnApi(new String(body.content(), StandardCharsets.UTF_8)));
        } catch (JsonException notReallyJson) {
            // An API that announces JSON and sends something else is a fault somebody else's
            // rule reports. Here it is simply nothing worth keeping.
            return Optional.empty();
        }
    }

    /** Whether what came back is what an API sends when it is content with the request. */
    private static boolean theApiWasHappy(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Whether a reply announcing this content type is JSON.
     *
     * <p>{@code application/json} and everything written as JSON with a name of its own -
     * {@code application/hal+json}, {@code application/problem+json} - which is the same rule the
     * rest of the tool reads a reply by.
     */
    private static boolean isJson(String mediaType) {
        String announced = mediaType.toLowerCase(Locale.ROOT);
        int parameters = announced.indexOf(';');
        String type = (parameters < 0 ? announced : announced.substring(0, parameters)).trim();
        return type.equals("application/json") || type.endsWith("+json");
    }

    /**
     * The name the document gives to one thing in this reply, when it gives one.
     *
     * <p>"One thing" rather than "the reply", because a reply that is a list of owners is a list of
     * things each of which is an {@code Owner}, and it is the owner that is worth keeping under
     * that name. What the document declares for the exact content type that came back is what is
     * read, which may be an entry it wrote as {@code application/json}, as {@code application/*} or
     * as <code>*&#47;*</code> - documents in our own corpus use all three for ordinary JSON.
     */
    private Optional<String> shapeOfOneThingIn(OperationId operation,
            HttpResponseRecord response) {
        Payload body = response.body().orElseThrow();
        return model.operation(operation)
                .flatMap(known -> known.responseFor(response.statusCode()))
                .flatMap(declared -> declared.schemaFor(body.mediaType()))
                .flatMap(schema -> nameOfOneThing(schema, 0));
    }

    private Optional<String> nameOfOneThing(CanonicalSchema schema, int hops) {
        if (hops > settings.asDeepAsAReplyIsRead()) {
            return Optional.empty();
        }
        if (schema instanceof ArraySchema list) {
            return nameOfOneThing(list.items(), hops + 1);
        }
        if (schema instanceof SchemaReference reference) {
            // A name that turns out to point at a list names the list, and what is worth keeping
            // is still the thing inside it.
            CanonicalSchema named = model.resolve(reference).orElse(null);
            return named instanceof ArraySchema list
                    ? nameOfOneThing(list.items(), hops + 1)
                    : Optional.of(reference.name());
        }
        return Optional.empty();
    }

    /**
     * Keeps what one reply carried.
     *
     * <p>A reply that is a list of things is taken apart first: each thing in it is one of whatever
     * the document named, not the list. A list of lists is taken apart the same way, and only so
     * far: JSON puts no limit on how deeply a reply may be nested, and following one down without a
     * limit would end this thread - the one every listener is served from - on a reply built to do
     * exactly that.
     */
    private void remember(JsonValue reply, Optional<String> shape, InteractionId from, int depth) {
        if (depth > settings.asDeepAsAReplyIsRead()) {
            return;
        }
        if (reply instanceof JsonValue.JsonArray list) {
            list.elements().forEach(element -> remember(element, shape, from, depth + 1));
            return;
        }
        if (reply instanceof JsonValue.JsonObject thing) {
            shape.ifPresent(named ->
                    underTheNameOfTheirShape.remember(named, thing, from));
            rememberNamedPieces(thing, from, 0);
        }
    }

    /**
     * Files every named piece of a reply under its own name, however deep it lies.
     *
     * <p>A piece is kept whole as well as taken apart: an address inside an owner goes under
     * {@code address}, and the town inside that address goes under {@code town}. Both get asked
     * for - one by an operation wanting an address, the other by one wanting a town - and whichever
     * kind of value is wanted, the ones of the wrong kind are simply not offered.
     *
     * <p>A piece with no value in it is not kept. Knowing that an API sometimes sends nothing for a
     * property is not knowing a value for it.
     */
    private void rememberNamedPieces(JsonValue.JsonObject thing, InteractionId from, int depth) {
        if (depth > settings.asDeepAsAReplyIsRead()) {
            return;
        }
        for (Map.Entry<String, JsonValue> piece : thing.members().entrySet()) {
            JsonValue value = piece.getValue();
            if (value instanceof JsonValue.JsonNull || !smallEnoughToSend(value)) {
                continue;
            }
            underTheirOwnNames.remember(piece.getKey(), value, from);
            switch (value) {
                case JsonValue.JsonObject inside -> rememberNamedPieces(inside, from, depth + 1);
                case JsonValue.JsonArray list -> {
                    for (JsonValue element : list.elements()) {
                        if (element instanceof JsonValue.JsonNull) {
                            continue;
                        }
                        // Under the name of the list it is in, which is the only name an element
                        // has - and is the name whatever fills one piece of a list asks under.
                        if (smallEnoughToSend(element)) {
                            underTheirOwnNames.remember(piece.getKey(), element, from);
                        }
                        if (element instanceof JsonValue.JsonObject inside) {
                            rememberNamedPieces(inside, from, depth + 1);
                        }
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Whether a value is one anybody could send: every word in it short enough, every number in it
     * writeable.
     *
     * <p>A number is judged by how long it would be if it were written out in full rather than by
     * how it arrived, which is the difference between a value somebody could send and a
     * ten-million-character web address - and, for a number small enough, between a value that can
     * be written down at all and one that cannot. {@code 1e-10000} is eight characters on the wire
     * and is not a number JSON can write.
     *
     * <p>A whole object or list is judged by what is in it, all the way down. Answering "yes, it is
     * a container" was a hole rather than a shortcut: an owner with a four-hundred-kilobyte photo
     * in it is exactly as unsendable as the photo, and is what actually arrives, because a reply's
     * awkward values sit inside the things it returns rather than on their own.
     */
    boolean smallEnoughToSend(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString text ->
                    text.value().length() <= settings.longestValueKept();
            case JsonValue.JsonNumber number ->
                    number.value().precision() + Math.abs((long) number.value().scale())
                            <= settings.longestValueKept();
            case JsonValue.JsonObject thing ->
                    thing.members().values().stream().allMatch(this::smallEnoughToSend);
            case JsonValue.JsonArray list ->
                    list.elements().stream().allMatch(this::smallEnoughToSend);
            // Nothing and true or false, neither of which has a size.
            case JsonValue.JsonNull ignored -> true;
            case JsonValue.JsonBoolean ignored -> true;
        };
    }

    /** One value the API sent back, and the exchange it was read out of. */
    record Observation(JsonValue value, InteractionId from) {

        Observation {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(from, "from");
        }
    }

    /**
     * One of the two lists, and the only part of this that anything else asks questions of.
     *
     * <p>Written to by whichever thread is carrying announcements out to the listeners, and read by
     * the one building the next request. The list under a name is replaced whole rather than added
     * to, so a reader always sees one complete list and never waits for a writer: a request is
     * never held up by a reply arriving.
     */
    static final class Remembered implements Dictionary {

        private final ValueDictionary.Keying keying;
        private final MemorySettings settings;
        private final ConcurrentMap<String, List<Observation>> byKey = new ConcurrentHashMap<>();

        Remembered(ValueDictionary.Keying keying, MemorySettings settings) {
            this.keying = keying;
            this.settings = settings;
        }

        void remember(String key, JsonValue value, InteractionId from) {
            // One thread writes here: the one the run's announcements are carried out on. That is
            // what lets the cap be read and then acted on, and it is why nothing here locks.
            if (!byKey.containsKey(key) && byKey.size() >= settings.mostNames()) {
                return;
            }
            byKey.compute(key, (ignored, kept) -> {
                List<Observation> latest =
                        new ArrayList<>(kept == null ? List.of() : kept);
                latest.removeIf(seen -> seen.value().equals(value));
                latest.add(new Observation(value, from));
                while (latest.size() > settings.mostValuesUnderOneName()) {
                    latest.remove(0);
                }
                return List.copyOf(latest);
            });
        }

        /** What was seen for this value, most recently seen last. */
        List<Observation> observationsFor(ValueRequest request) {
            Objects.requireNonNull(request, "request");
            return keyFor(request).map(key -> byKey.getOrDefault(key, List.of()))
                    .orElse(List.of());
        }

        private Optional<String> keyFor(ValueRequest request) {
            // Every keying named rather than a catch-all, so that adding one to the format is a
            // compiler error here - in the one class whose whole subject is which keying it uses.
            return switch (keying) {
                // The last step of the way down to the value, wherever it turns up: a petId is a
                // petId whether it is a parameter or a property deep inside a body.
                case NAME -> Optional.of(request.name());
                // Only a shape the document named, which is the only thing a whole value can be
                // recognised by later.
                case SCHEMA -> request.shape();
                // Nothing watching an API's replies can fill these: they are about what a document
                // says a value is, not about what came back under a name.
                case TYPE, FORMAT, OPERATION_AND_PARAMETER -> Optional.empty();
            };
        }

        @Override
        public List<JsonValue> valuesFor(ValueRequest request) {
            return observationsFor(request).stream().map(Observation::value).toList();
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public boolean isAboutOneValueInParticular() {
            return keying.isAboutOneValueInParticular();
        }

        /** How many different names or shapes this holds anything for. */
        int size() {
            return byKey.size();
        }

        @Override
        public String toString() {
            return "what this run has seen, by "
                    + keying.name().toLowerCase(Locale.ROOT) + " (" + byKey.size() + " so far)";
        }
    }

    /**
     * The things the API returned, kept under the kind of thing each is: pets under "pet".
     *
     * <p>Only the words and numbers of each thing are kept, since only a word or a number can fill
     * a gap in a web address. Bounded exactly as the lists under a name are, and read and written
     * the same way: one thread writes, and a reader always gets one complete list.
     */
    static final class Resources {

        private final MemorySettings settings;
        private final ConcurrentMap<String, List<Observation>> byKind = new ConcurrentHashMap<>();

        Resources(MemorySettings settings) {
            this.settings = settings;
        }

        void remember(String kind, JsonValue.JsonObject thing, InteractionId from) {
            if (!byKind.containsKey(kind) && byKind.size() >= settings.mostNames()) {
                return;
            }
            byKind.compute(kind, (ignored, kept) -> {
                List<Observation> latest = new ArrayList<>(kept == null ? List.of() : kept);
                latest.removeIf(seen -> seen.value().equals(thing));
                latest.add(new Observation(thing, from));
                while (latest.size() > settings.mostValuesUnderOneName()) {
                    latest.remove(0);
                }
                return List.copyOf(latest);
            });
        }

        /** The things of this kind, most recently seen last, each a {@code JsonObject}. */
        List<Observation> thingsOfKind(String kind) {
            return byKind.getOrDefault(Objects.requireNonNull(kind, "kind"), List.of());
        }

        /** How many kinds of thing this holds anything for. */
        int size() {
            return byKind.size();
        }

        @Override
        public String toString() {
            return "what this run has seen, by the kind of thing (" + byKind.size() + " so far)";
        }
    }
}
