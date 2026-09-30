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

import java.util.Locale;
import java.util.Objects;

/**
 * Where in a request a key travels: a header, a parameter in the query of the address, a cookie, or
 * a field of a request body written as a web form - and the name it travels under there.
 *
 * <p>Two places are the same when a request could not tell them apart: headers are named without
 * regard to capitals, as HTTP says they are, and everything else is named exactly.
 *
 * @param where which part of the request
 * @param name the name the key goes under in that part
 */
public record Place(Where where, String name) {

    /** The parts of a request a key can travel in. */
    public enum Where {
        /** A header of the request. */
        HEADER("header"),
        /** A parameter in the query of the address. */
        QUERY("query parameter"),
        /** A cookie, inside the request's one {@code Cookie} header. */
        COOKIE("cookie"),
        /** A field of a body written as a web form. */
        FORM_FIELD("form field");

        private final String words;

        Where(String words) {
            this.words = words;
        }

        /** The words a sentence names this part of a request in: "query parameter". */
        public String words() {
            return words;
        }
    }

    public Place {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a place in a request has a name");
        }
    }

    /** Whether a request could not tell this place from the other one. */
    public boolean sameAs(Place other) {
        return where == other.where && (where == Where.HEADER
                ? name.equalsIgnoreCase(other.name) : name.equals(other.name));
    }

    /** The place in words: "the header api_key", "the query parameter apiKey". */
    public String described() {
        return "the " + where.words() + " " + name;
    }

    /** One key for the place, the same for every two places {@link #sameAs same as} each other. */
    String identity() {
        return where + " " + (where == Where.HEADER ? name.toLowerCase(Locale.ROOT) : name);
    }
}
