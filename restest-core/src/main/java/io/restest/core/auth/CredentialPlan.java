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

import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.SecurityRequirement;
import io.restest.core.model.SecurityScheme;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which of the keys the person running the tool handed over goes with which operation, and where in
 * each request it goes.
 *
 * <p>A document says where a key goes and the person running the tool says what it is. This is
 * where the two meet. Each key handed over with {@code --auth}, or left in {@code RESTEST_AUTH}, is
 * read against the document: it may name the scheme it is for, or say where it goes, or be the key
 * alone for a document that declares only one. Then, operation by operation, the document's own
 * requirements decide which keys go with it:
 *
 * <ul>
 *   <li>a key given with the place it goes goes with every operation;
 *   <li>a key for a scheme goes with an operation that asks for that scheme, through the first of
 *       the ways it offers for which every key is to hand - never with one that says it asks for
 *       nothing, and never through a way that also asks for something that is not a key;
 *   <li>a key for a scheme the document declares and never asks for goes with every operation that
 *       does not say it asks for nothing, since a document that bothered to declare one meant it to
 *       be used;
 *   <li>and wherever an operation declares an input of its own under the key's name, in the same part
 *       of the request - or, for a key that goes in the address, as a field of a web form - the key
 *       fills that input.
 * </ul>
 *
 * <p>That last rule is why the part of the tool that invents values is given the API without those
 * inputs ({@link #modelToFillIn(ApiModel)}): nothing is invented for them, nothing changes them, and
 * no test case carries the key. The key is added to each request only as it leaves, by
 * {@link CredentialedEngine}.
 *
 * <p>What the document asks for and nobody handed over is kept too, so that a run can say so before
 * it sends anything. A run handed no key at all has a plan with nothing in it, and is exactly the run
 * it was before any of this existed.
 */
public final class CredentialPlan {

    /** The names of headers RESTest's HTTP client writes itself, which a key cannot take over. */
    private static final Set<String> WRITTEN_BY_THE_CLIENT = Set.of("host", "content-length",
            "content-type", "transfer-encoding", "connection", "keep-alive", "te", "trailer",
            "upgrade", "expect", "proxy-connection", "accept-encoding", "cookie");

    /** Where a key goes, as the place a person writes before the key: {@code header:X-API-Key=}. */
    private static final Map<String, Place.Where> PLACES_A_PERSON_CAN_NAME = Map.of(
            "header:", Place.Where.HEADER,
            "query:", Place.Where.QUERY,
            "cookie:", Place.Where.COOKIE);

    private static final String HOW_TO_SAY_WHERE = "say where it goes instead: "
            + AuthGiven.OPTION + " header:<name>=<key>, query:<name>=<key> or cookie:<name>=<key>";

    /**
     * What reading the keys against the document gave: the plan, what was refused, and what was
     * left out with a warning.
     *
     * @param plan which key goes where; nothing at all when anything was refused
     * @param refusals why a key typed on the command line cannot be used. Any at all, and the run
     *     does not start, because it would test the API without a key somebody meant it to have
     * @param warnings why a key left in the environment is not used. The run goes on without it: a
     *     variable can outlive the command it was set for, and a key that fits nothing in this
     *     document is no reason not to test the API
     */
    public record Found(CredentialPlan plan, List<String> refusals, List<String> warnings) {

        public Found {
            Objects.requireNonNull(plan, "plan");
            refusals = List.copyOf(refusals);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Where one key handed over goes.
     *
     * @param named how a message names the key without repeating it
     * @param mask what everything the run writes shows in the key's place
     * @param places every place in a request it goes, in the order they were found
     * @param operations the operations it goes with
     * @param whyWithNone when it goes with none, why not
     */
    public record Placed(String named, String mask, List<Place> places,
            Set<OperationId> operations, Optional<String> whyWithNone) {

        public Placed {
            Objects.requireNonNull(named, "named");
            Objects.requireNonNull(mask, "mask");
            Objects.requireNonNull(whyWithNone, "whyWithNone");
            places = List.copyOf(places);
            operations = Set.copyOf(operations);
        }
    }

    /**
     * A key the document asks for that nobody handed over.
     *
     * @param scheme the name the document declares it under
     * @param place where it would go
     * @param operations the operations that ask for it and got no other key they would take instead
     * @param askedForNowhere whether the document declares it without asking for it on any operation,
     *     in which case it would go with every operation that does not say it asks for nothing
     */
    public record Missing(String scheme, Place place, Set<OperationId> operations,
            boolean askedForNowhere) {

        public Missing {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(place, "place");
            operations = Set.copyOf(operations);
        }
    }

    /** One key as read, before it is placed: the key, and the place it goes when nothing else says. */
    private record Key(AuthGiven given, Optional<String> scheme, Place place, Secret secret) {
    }

    /** What the part of the tool that invents values must leave out for one operation. */
    record Trim(Set<String> parameters, Set<String> formFields) {
    }

    private static final CredentialPlan NOTHING = new CredentialPlan(Map.of(), Map.of(), List.of(),
            List.of(), Secrets.none());

    private final Map<OperationId, List<Credential>> credentials;
    private final Map<OperationId, Trim> trims;
    private final List<Placed> placed;
    private final List<Missing> missing;
    private final Secrets secrets;

    private CredentialPlan(Map<OperationId, List<Credential>> credentials,
            Map<OperationId, Trim> trims, List<Placed> placed, List<Missing> missing,
            Secrets secrets) {
        this.credentials = credentials;
        this.trims = trims;
        this.placed = List.copyOf(placed);
        this.missing = List.copyOf(missing);
        this.secrets = secrets;
    }

    /** The plan of a run handed no key, for a document asking for none either. */
    public static CredentialPlan none() {
        return NOTHING;
    }

    /**
     * Reads every key handed over against the document, and works out where each goes.
     *
     * @param given every key handed over, the ones left in the environment first and then the ones
     *     typed, in the order they were typed; one typed for the same place as one left in the
     *     environment wins over it
     * @param model the API as its document describes it
     * @param followRedirects whether requests follow a redirection to wherever it points, which a
     *     key in a header or a cookie would then be carried to
     * @return the plan, what was refused, and what was left out
     */
    public static Found gather(List<AuthGiven> given, ApiModel model, boolean followRedirects) {
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(model, "model");
        List<String> refusals = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<Key> read = new ArrayList<>();
        for (AuthGiven key : given) {
            Objects.requireNonNull(key, "a key given");
            try {
                read.add(read(key, model));
            } catch (Refused refused) {
                problem(key, refused.getMessage(), refusals, warnings);
            }
        }
        List<Key> keys = withTextsOfTheirOwn(oneForEachPlace(read, refusals, warnings));
        List<Key> usable = new ArrayList<>();
        for (Key key : keys) {
            Optional<String> wrong = whatStopsItGoing(key, keys, followRedirects);
            if (wrong.isPresent()) {
                problem(key.given(), wrong.get(), refusals, warnings);
            } else {
                usable.add(key);
            }
        }
        if (!refusals.isEmpty()) {
            return new Found(NOTHING, refusals, warnings);
        }
        return placed(usable, model, refusals, warnings);
    }

    /** Whether there is no key to send: a run that goes exactly as it would with none of this. */
    public boolean isEmpty() {
        return credentials.values().stream().allMatch(List::isEmpty) && placed.isEmpty();
    }

    /** Where each key handed over goes, in the order they were handed over. */
    public List<Placed> placed() {
        return placed;
    }

    /** The keys the document asks for and nobody handed over, in the order it declares them. */
    public List<Missing> missing() {
        return missing;
    }

    /**
     * The API as the part of the tool that invents values should see it: without the inputs a key
     * fills, so that nothing is invented for them. The very same model when no key fills any input,
     * which is what keeps a run handed no key the run it always was.
     */
    public ApiModel modelToFillIn(ApiModel model) {
        return trims.isEmpty() ? model : ModelToFillIn.without(model, trims);
    }

    /** What goes with a request for this operation. */
    List<Credential> forOperation(OperationId operation) {
        return credentials.getOrDefault(operation, List.of());
    }

    /** The keys, and how each appearance of one is hidden. */
    Secrets secrets() {
        return secrets;
    }

    /** What each operation must have left out, for the model {@link #modelToFillIn} gives. */
    Map<OperationId, Trim> trims() {
        return trims;
    }

    // --- Reading one key -----------------------------------------------------------------------

    /** Why a key handed over cannot be used. The message never holds the key. */
    private static final class Refused extends Exception {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        Refused(String why) {
            super(why, null, false, false);
        }
    }

    private static Key read(AuthGiven given, ApiModel model) throws Refused {
        String text = given.text();
        String named = given.named();
        if (text.isEmpty()) {
            throw new Refused(named + " is empty, and a key is needed after " + AuthGiven.OPTION);
        }
        for (Map.Entry<String, Place.Where> prefix : PLACES_A_PERSON_CAN_NAME.entrySet()) {
            if (text.regionMatches(true, 0, prefix.getKey(), 0, prefix.getKey().length())) {
                return atAPlace(given, prefix.getKey(), prefix.getValue(),
                        text.substring(prefix.getKey().length()));
            }
        }
        Optional<String> scheme = schemeNamedAtTheStart(text, model.securitySchemes().keySet());
        if (scheme.isPresent()) {
            String value = text.substring(scheme.get().length() + 1);
            if (value.isEmpty()) {
                throw new Refused(named + " names the scheme '" + scheme.get()
                        + "' and gives no key after the '='");
            }
            return forAScheme(given, scheme.get(), model.securitySchemes().get(scheme.get()),
                    value);
        }
        List<String> keySchemes = keySchemes(model);
        // A key on its own may end in '=', as one written in Base64 does. An '=' with more after it
        // is where the name of a scheme ends, and this text names none the document declares:
        // sending all of it as the key would send a mistyped name along with it.
        boolean namesAScheme = holdsANameBeforeAnEquals(text);
        if (keySchemes.size() == 1 && !namesAScheme) {
            String only = keySchemes.get(0);
            SecurityScheme.ApiKey scheme1 = (SecurityScheme.ApiKey) model.securitySchemes().get(only);
            return new Key(given, Optional.of(only), placeOf(scheme1),
                    new Secret(text, Secrets.MARKER));
        }
        String what = namesAScheme
                ? named + " reads as <name>=<key>, and the document declares no scheme of that name"
                : named + " is a key on its own";
        if (keySchemes.isEmpty()) {
            throw new Refused(what + (namesAScheme ? ", nor any API key at all"
                    : ", and the document declares no API key for it to answer")
                    + "; " + HOW_TO_SAY_WHERE);
        }
        if (keySchemes.size() == 1) {
            throw new Refused(what + ": its one API key is " + keySchemes.get(0) + ". Name it: "
                    + AuthGiven.OPTION + " " + keySchemes.get(0) + "=<key>, which also sends a key "
                    + "holding an '=' of its own as it is");
        }
        throw new Refused(what + (namesAScheme ? ": it declares " : ", and the document declares ")
                + keySchemes.size() + " API keys: " + String.join(", ", keySchemes)
                + ". Name the one it is for: " + AuthGiven.OPTION + " " + keySchemes.get(0)
                + "=<key>");
    }

    /** Whether a text has an {@code =} with something other than more of them after it. */
    private static boolean holdsANameBeforeAnEquals(String text) {
        int equals = text.indexOf('=');
        return equals >= 0 && text.chars().skip(equals).anyMatch(character -> character != '=');
    }

    private static Key atAPlace(AuthGiven given, String prefix, Place.Where where, String rest)
            throws Refused {
        String named = given.named();
        int equals = rest.indexOf('=');
        if (equals < 0) {
            throw new Refused(named + " says which part of the request a key goes in, and not "
                    + "the name it goes under and the key: write it as " + prefix
                    + "<name>=<key>");
        }
        String name = rest.substring(0, equals);
        String value = rest.substring(equals + 1);
        if (name.isEmpty()) {
            throw new Refused(named + " does not say under which name the key goes");
        }
        if (value.isEmpty()) {
            throw new Refused(named + " gives no key after the '='");
        }
        if (value.chars().allMatch(character -> character == '=')) {
            // A key written in Base64 ends in '=', so one typed without the name it goes under
            // reads as a name followed by nothing but '='. The name is not repeated: it is most
            // likely the key.
            throw new Refused(named + " gives nothing but '=' after the name it goes under, which "
                    + "is how a key written in Base64 ends: write the name first, as " + prefix
                    + "<name>=<key>");
        }
        Place place = new Place(where, name);
        String mask = Secrets.MARKER + "." + where.name().toLowerCase(Locale.ROOT) + "."
                + label(name);
        return new Key(given, Optional.empty(), place, new Secret(value, mask));
    }

    private static Key forAScheme(AuthGiven given, String name, SecurityScheme scheme,
            String value) throws Refused {
        String named = given.named();
        return switch (scheme) {
            case SecurityScheme.ApiKey key -> new Key(given, Optional.of(name), placeOf(key),
                    new Secret(value, Secrets.MARKER + "." + label(name)));
            case SecurityScheme.Http http -> throw new Refused(named + " is for '" + name
                    + "', which is an HTTP " + http.scheme() + " scheme; RESTest 2.0 sends only "
                    + "API keys, so it cannot send this one");
            case SecurityScheme.Other other -> throw new Refused(named + " is for '" + name
                    + "', which is a scheme of type " + other.type() + "; RESTest 2.0 sends only "
                    + "API keys, so it cannot send this one");
            case SecurityScheme.Unreadable unreadable -> throw new Refused(named + " is for '"
                    + name + "', which the document declares in a way RESTest cannot use: "
                    + unreadable.why() + "; " + HOW_TO_SAY_WHERE);
        };
    }

    /**
     * The scheme a text names before its first {@code =}, if it names one the document declares:
     * the longest such name, written exactly, and otherwise the only one written in other capitals.
     * A key on its own may hold {@code =} itself, as a key written in Base64 ends with, so a text
     * that names no declared scheme is the key alone.
     */
    private static Optional<String> schemeNamedAtTheStart(String text, Collection<String> schemes) {
        Optional<String> exactly = schemes.stream()
                .filter(name -> text.startsWith(name + "="))
                .max((one, other) -> Integer.compare(one.length(), other.length()));
        if (exactly.isPresent()) {
            return exactly;
        }
        List<String> otherwise = schemes.stream()
                .filter(name -> text.length() > name.length()
                        && text.regionMatches(true, 0, name + "=", 0, name.length() + 1))
                .toList();
        return otherwise.size() == 1 ? Optional.of(otherwise.get(0)) : Optional.empty();
    }

    private static List<String> keySchemes(ApiModel model) {
        return model.securitySchemes().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof SecurityScheme.ApiKey)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Place placeOf(SecurityScheme.ApiKey key) {
        Place.Where where = switch (key.location()) {
            case HEADER -> Place.Where.HEADER;
            case QUERY -> Place.Where.QUERY;
            case COOKIE -> Place.Where.COOKIE;
            default -> throw new IllegalStateException("a key travels in a header, the query or a "
                    + "cookie, and this one says " + key.location().written());
        };
        return new Place(where, key.name());
    }

    /** A name as the text written in a key's place can carry it: safe inside an address. */
    private static String label(String name) {
        StringBuilder written = new StringBuilder(name.length());
        for (char character : name.toCharArray()) {
            boolean safe = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '.' || character == '_'
                    || character == '~';
            written.append(safe ? character : '_');
        }
        return written.toString();
    }

    private static void problem(AuthGiven given, String why, List<String> refusals,
            List<String> warnings) {
        if (given.source() == AuthGiven.Source.ENVIRONMENT) {
            warnings.add(why + "; the run goes on without it");
        } else {
            refusals.add(why);
        }
    }

    /**
     * One key for each place. One typed for the place a key left in the environment goes wins over
     * it; two typed for the same place are one too many, since nothing could say which was meant.
     */
    private static List<Key> oneForEachPlace(List<Key> read, List<String> refusals,
            List<String> warnings) {
        Map<String, Key> byPlace = new LinkedHashMap<>();
        for (Key key : read) {
            String place = key.place().identity();
            Key earlier = byPlace.get(place);
            if (earlier == null) {
                byPlace.put(place, key);
            } else if (earlier.given().source() == AuthGiven.Source.ENVIRONMENT
                    && key.given().source() == AuthGiven.Source.COMMAND_LINE) {
                byPlace.put(place, key);
            } else if (key.given().source() == AuthGiven.Source.COMMAND_LINE) {
                refusals.add(key.given().named() + " goes in " + key.place().described()
                        + ", and so does " + earlier.given().named() + ": one key for each place");
            }
        }
        return new ArrayList<>(byPlace.values());
    }

    /**
     * The keys, each with a text of its own to stand in its place. Two names can come out the same
     * in that text - {@code API Key} and {@code API_Key} are both written {@code API_Key} - and then
     * each one after the first is told apart by a number, so that whoever puts the keys back into
     * what a run wrote can tell which went where.
     */
    private static List<Key> withTextsOfTheirOwn(List<Key> keys) {
        Set<String> written = new HashSet<>();
        keys.forEach(key -> written.add(key.secret().mask()));
        Set<String> taken = new HashSet<>();
        List<Key> own = new ArrayList<>(keys.size());
        for (Key key : keys) {
            String mask = key.secret().mask();
            if (taken.add(mask)) {
                own.add(key);
                continue;
            }
            int number = 2;
            while (written.contains(mask + "." + number) || !taken.add(mask + "." + number)) {
                number++;
            }
            own.add(new Key(key.given(), key.scheme(), key.place(),
                    new Secret(key.secret().value(), mask + "." + number)));
        }
        return own;
    }

    /** What would stop a key reaching the API as it was given, or reaching it safely. */
    private static Optional<String> whatStopsItGoing(Key key, List<Key> keys,
            boolean followRedirects) {
        String named = key.given().named();
        String value = key.secret().value();
        if (value.length() < Secrets.SHORTEST_KEY) {
            return Optional.of(named + " is shorter than " + Secrets.SHORTEST_KEY + " characters: "
                    + "a key is hidden wherever it appears in what the run writes, and one this "
                    + "short would be hidden inside ordinary words and numbers too");
        }
        for (char character : value.toCharArray()) {
            if (character < 0x20 || character > 0x7E) {
                return Optional.of(named + " holds a character that cannot be sent as it is: a "
                        + "line break, a tab or a character outside plain ASCII");
            }
        }
        if (value.charAt(0) == ' ' || value.charAt(value.length() - 1) == ' ') {
            return Optional.of(named + " begins or ends with a space, which would not reach the API "
                    + "as it was given");
        }
        Place place = key.place();
        if (place.where() == Place.Where.COOKIE && !fitsACookie(value)) {
            return Optional.of(named + " goes in a cookie, and holds a character a cookie cannot "
                    + "carry: a space, a quotation mark, a comma, a semicolon or a backslash");
        }
        if ((place.where() == Place.Where.HEADER || place.where() == Place.Where.COOKIE)
                && !isAToken(place.name())) {
            // The name is not repeated: a name that is no name is as likely as not the key itself,
            // typed where the name should have been.
            String what = place.where() == Place.Where.HEADER ? "header" : "cookie";
            return Optional.of(named + " goes in a " + what + " whose name is not one a " + what
                    + " can have; " + (key.scheme().isPresent()
                            ? HOW_TO_SAY_WHERE : "write it as " + what + ":<name>=<key>"));
        }
        if (place.where() == Place.Where.HEADER
                && WRITTEN_BY_THE_CLIENT.contains(place.name().toLowerCase(Locale.ROOT))) {
            return Optional.of(named + " goes in " + place.described() + ", which RESTest's HTTP "
                    + "client writes itself"
                    + (place.name().equalsIgnoreCase("cookie")
                            ? "; for a cookie, write " + AuthGiven.OPTION + " cookie:<name>=<key>"
                            : ""));
        }
        if (followRedirects
                && (place.where() == Place.Where.HEADER || place.where() == Place.Where.COOKIE)) {
            return Optional.of(named + " goes in " + place.described() + ", and "
                    + "engine.followRedirects is on: a redirection to another machine would carry "
                    + "the key there. Leave redirections off, or give the key in the query");
        }
        for (Key other : keys) {
            if (other.secret().mask().contains(value) || Secrets.MARKER.contains(value)
                    || value.contains(Secrets.MARKER)) {
                return Optional.of(named + " is part of the text RESTest writes in the place of a "
                        + "key, so it could not be hidden");
            }
        }
        return Optional.empty();
    }

    /** Whether every character is one a cookie's value may hold, as the rules for cookies say. */
    private static boolean fitsACookie(String value) {
        for (char character : value.toCharArray()) {
            boolean allowed = character == 0x21
                    || (character >= 0x23 && character <= 0x2B)
                    || (character >= 0x2D && character <= 0x3A)
                    || (character >= 0x3C && character <= 0x5B)
                    || (character >= 0x5D && character <= 0x7E);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** Whether a name is one HTTP allows a header, or a cookie, to have. */
    private static boolean isAToken(String name) {
        for (char character : name.toCharArray()) {
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || "!#$%&'*+-.^_`|~".indexOf(character) >= 0;
            if (!allowed) {
                return false;
            }
        }
        return !name.isEmpty();
    }

    // --- Placing every key ---------------------------------------------------------------------

    private static Found placed(List<Key> keys, ApiModel model, List<String> refusals,
            List<String> warnings) {
        Map<String, Key> forScheme = new LinkedHashMap<>();
        keys.stream().filter(key -> key.scheme().isPresent())
                .forEach(key -> forScheme.put(key.scheme().get(), key));
        // A key given with the place a scheme's key goes is that scheme's key.
        model.securitySchemes().forEach((name, scheme) -> {
            if (scheme instanceof SecurityScheme.ApiKey declared && !forScheme.containsKey(name)) {
                Place place = placeOf(declared);
                keys.stream().filter(key -> key.scheme().isEmpty() && key.place().sameAs(place))
                        .findFirst().ifPresent(key -> forScheme.put(name, key));
            }
        });
        Set<String> askedForSomewhere = new LinkedHashSet<>();
        model.security().ifPresent(root -> askedForSomewhere.addAll(root.schemesNamed()));
        model.operations().forEach(operation -> operation.security()
                .ifPresent(own -> askedForSomewhere.addAll(own.schemesNamed())));

        Map<OperationId, List<Credential>> credentials = new LinkedHashMap<>();
        Map<OperationId, Trim> trims = new LinkedHashMap<>();
        Map<Key, Set<OperationId>> wentWith = new LinkedHashMap<>();
        Map<Key, Set<Place>> wentTo = new LinkedHashMap<>();
        keys.forEach(key -> {
            wentWith.put(key, new LinkedHashSet<>());
            wentTo.put(key, new LinkedHashSet<>());
        });

        for (Operation operation : model.operations()) {
            Map<String, Credential> here = new LinkedHashMap<>();
            Map<String, Key> whose = new LinkedHashMap<>();
            Optional<SecurityRequirement> requirement = model.securityFor(operation);
            boolean asksForNothing = requirement.map(SecurityRequirement::asksForNothing)
                    .orElse(false);

            for (Key key : keys) {
                if (key.scheme().isEmpty()) {
                    put(key, key.place(), operation, here, whose, refusals);
                }
            }
            if (requirement.isPresent() && !asksForNothing) {
                for (Set<String> alternative : requirement.get().alternatives()) {
                    if (!alternative.isEmpty() && alternative.stream().allMatch(forScheme::containsKey)) {
                        for (String scheme : alternative) {
                            Key key = forScheme.get(scheme);
                            put(key, placeOf((SecurityScheme.ApiKey) model.securitySchemes()
                                    .get(scheme)), operation, here, whose, refusals);
                        }
                        break;
                    }
                }
            }
            if (!asksForNothing) {
                forScheme.forEach((scheme, key) -> {
                    if (!askedForSomewhere.contains(scheme)) {
                        put(key, placeOf((SecurityScheme.ApiKey) model.securitySchemes()
                                .get(scheme)), operation, here, whose, refusals);
                    }
                });
            }
            Set<String> parametersFilled = new LinkedHashSet<>();
            Set<String> fieldsFilled = new LinkedHashSet<>();
            for (Key key : keys) {
                for (Parameter parameter : inputsUnderTheKeysName(operation, key.place())) {
                    Place filled = new Place(placeOf(parameter.location()), parameter.name());
                    put(key, filled, operation, here, whose, refusals);
                    parametersFilled.add(parameter.location() + " " + parameter.name());
                }
                if (key.place().where() == Place.Where.QUERY
                        && ModelToFillIn.formFields(operation, model).contains(key.place().name())) {
                    put(key, new Place(Place.Where.FORM_FIELD, key.place().name()), operation, here,
                            whose, refusals);
                    fieldsFilled.add(key.place().name());
                }
            }
            if (!parametersFilled.isEmpty() || !fieldsFilled.isEmpty()) {
                trims.put(operation.id(), new Trim(Set.copyOf(parametersFilled),
                        Set.copyOf(fieldsFilled)));
            }
            credentials.put(operation.id(), List.copyOf(here.values()));
            whose.forEach((place, key) -> {
                wentWith.get(key).add(operation.id());
                wentTo.get(key).add(here.get(place).place());
            });
        }
        if (!refusals.isEmpty()) {
            return new Found(NOTHING, refusals, warnings);
        }

        List<Placed> placed = new ArrayList<>();
        for (Key key : keys) {
            Set<OperationId> operations = wentWith.get(key);
            placed.add(new Placed(key.given().named(), key.secret().mask(),
                    new ArrayList<>(wentTo.get(key)), operations,
                    operations.isEmpty() ? Optional.of(whyWithNone(key, model)) : Optional.empty()));
        }
        List<Secret> secrets = keys.stream().map(Key::secret).toList();
        return new Found(new CredentialPlan(Map.copyOf(credentials), Map.copyOf(trims), placed,
                missing(model, forScheme, askedForSomewhere), new Secrets(secrets)), refusals,
                warnings);
    }

    private static void put(Key key, Place place, Operation operation, Map<String, Credential> here,
            Map<String, Key> whose, List<String> refusals) {
        String identity = place.identity();
        Credential already = here.get(identity);
        if (already == null) {
            here.put(identity, new Credential(place, key.secret()));
            whose.put(identity, key);
        } else if (!already.secret().equals(key.secret())) {
            String problem = key.given().named() + " and " + whose.get(identity).given().named()
                    + " would both go in " + place.described() + " of " + operation.id().value()
                    + ": one key for each place";
            if (!refusals.contains(problem)) {
                refusals.add(problem);
            }
        }
    }

    /**
     * The inputs an operation declares under a key's name in the part of the request the key goes
     * in: a header named the same whatever its capitals, a parameter in the query, a cookie. Never a
     * part of the path, which names what is being asked for rather than who asks.
     */
    private static List<Parameter> inputsUnderTheKeysName(Operation operation, Place place) {
        return switch (place.where()) {
            case HEADER -> operation.parameters(ParameterLocation.HEADER).stream()
                    .filter(parameter -> parameter.name().equalsIgnoreCase(place.name()))
                    .toList();
            case QUERY -> operation.parameters(ParameterLocation.QUERY).stream()
                    .filter(parameter -> parameter.name().equals(place.name()))
                    .toList();
            case COOKIE -> operation.parameters(ParameterLocation.COOKIE).stream()
                    .filter(parameter -> parameter.name().equals(place.name()))
                    .toList();
            case FORM_FIELD -> List.of();
        };
    }

    private static Place.Where placeOf(ParameterLocation location) {
        return switch (location) {
            case HEADER -> Place.Where.HEADER;
            case QUERY -> Place.Where.QUERY;
            case COOKIE -> Place.Where.COOKIE;
            default -> throw new IllegalStateException("a key never fills the " + location.written());
        };
    }

    private static String whyWithNone(Key key, ApiModel model) {
        if (model.operations().isEmpty()) {
            return "the document describes no operation";
        }
        return key.scheme()
                .map(scheme -> "every operation that asks for " + scheme + " asks for something "
                        + "else with it that is not a key RESTest was given, or says it asks for "
                        + "nothing")
                .orElse("every operation says it asks for nothing");
    }

    /**
     * What the document asks for and nobody handed over: for each operation that no key given
     * satisfies and that nobody may call without proving something, the schemes of the ways it
     * offers that are made of keys alone; and the keys the document declares and never asks for,
     * unless one of those was given.
     */
    private static List<Missing> missing(ApiModel model, Map<String, Key> forScheme,
            Set<String> askedForSomewhere) {
        Map<String, Set<OperationId>> asking = new LinkedHashMap<>();
        for (Operation operation : model.operations()) {
            Optional<SecurityRequirement> requirement = model.securityFor(operation);
            if (requirement.isEmpty() || requirement.get().asksForNothing()) {
                continue;
            }
            List<Set<String>> alternatives = requirement.get().alternatives();
            boolean anybody = alternatives.stream().anyMatch(Set::isEmpty);
            boolean satisfied = alternatives.stream().anyMatch(alternative -> !alternative.isEmpty()
                    && alternative.stream().allMatch(forScheme::containsKey));
            if (anybody || satisfied) {
                continue;
            }
            for (Set<String> alternative : alternatives) {
                boolean keysAlone = alternative.stream().allMatch(name ->
                        model.securitySchemes().get(name) instanceof SecurityScheme.ApiKey);
                if (keysAlone) {
                    alternative.stream().filter(name -> !forScheme.containsKey(name))
                            .forEach(name -> asking.computeIfAbsent(name,
                                    ignored -> new LinkedHashSet<>()).add(operation.id()));
                }
            }
        }
        List<String> declaredAndNeverAskedFor = keySchemes(model).stream()
                .filter(name -> !askedForSomewhere.contains(name))
                .toList();
        boolean oneOfThoseWasGiven = declaredAndNeverAskedFor.stream()
                .anyMatch(forScheme::containsKey);

        List<Missing> missing = new ArrayList<>();
        model.securitySchemes().forEach((name, scheme) -> {
            if (!(scheme instanceof SecurityScheme.ApiKey key) || forScheme.containsKey(name)) {
                return;
            }
            if (asking.containsKey(name)) {
                missing.add(new Missing(name, placeOf(key), asking.get(name), false));
            } else if (declaredAndNeverAskedFor.contains(name) && !oneOfThoseWasGiven) {
                Set<OperationId> everyOperation = new LinkedHashSet<>();
                for (Operation operation : model.operations()) {
                    boolean saysNothing = model.securityFor(operation)
                            .map(SecurityRequirement::asksForNothing).orElse(false);
                    if (!saysNothing) {
                        everyOperation.add(operation.id());
                    }
                }
                missing.add(new Missing(name, placeOf(key), everyOperation, true));
            }
        });
        return missing;
    }
}
