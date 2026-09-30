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
package io.restest.core.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One way an API's document says a caller can prove who it is, under the name the document gives
 * that way.
 *
 * <p>An API that does not answer just anybody says so in its document, in two parts. It declares the
 * ways of proving who one is that it accepts - "a key in the header X-API-Key", "a bearer token",
 * "OAuth 2" - each under a name of its own, and then says, for the whole API or for one operation,
 * which of those a request has to use: that second part is a {@link SecurityRequirement}. The
 * document never says what the key or the token actually is. That belongs to whoever runs the tool,
 * and a document published for everybody to read has no business holding one.
 *
 * <p>RESTest sends one of these kinds, the {@link ApiKey key}: a value handed over by the person
 * running the tool and put where the document says it goes. The other kinds are read and kept as
 * what they are, so that a run can say what it cannot yet do and a later version has them to hand,
 * but nothing is sent for them.
 */
public sealed interface SecurityScheme {

    /**
     * A key the API expects under a name of its choosing, in a header, in the query of the address,
     * or in a cookie.
     *
     * <p>This is what an OpenAPI document calls a scheme of type {@code apiKey}. The same key goes
     * with every request that asks for it; nothing has to be fetched or renewed first.
     *
     * @param location where the key travels: a header, the query or a cookie, and nowhere else
     * @param name what it travels under, such as {@code X-API-Key} or {@code api_key}
     */
    record ApiKey(ParameterLocation location, String name) implements SecurityScheme {

        public ApiKey {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(name, "name");
            if (location != ParameterLocation.HEADER && location != ParameterLocation.QUERY
                    && location != ParameterLocation.COOKIE) {
                throw new IllegalArgumentException("a key travels in a header, the query or a "
                        + "cookie, not in the " + location.written());
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("a key travels under a name");
            }
        }
    }

    /**
     * A credential carried in the {@code Authorization} header in one of the ways HTTP itself
     * defines, a bearer token or a user name with its password most often.
     *
     * @param scheme HTTP's name for the way, in lower case: {@code bearer}, {@code basic}, and so on
     * @param bearerFormat what the document says a bearer token looks like, such as {@code JWT},
     *     where it says
     */
    record Http(String scheme, Optional<String> bearerFormat) implements SecurityScheme {

        public Http {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(bearerFormat, "bearerFormat");
            if (scheme.isBlank()) {
                throw new IllegalArgumentException("an HTTP scheme has a name, such as bearer");
            }
            scheme = scheme.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A way of proving who one is that is kept by its name only: OAuth 2, OpenID Connect, or a
     * certificate the client presents.
     *
     * @param type the document's own word for it, such as {@code oauth2}
     */
    record Other(String type) implements SecurityScheme {

        public Other {
            Objects.requireNonNull(type, "type");
            if (type.isBlank()) {
                throw new IllegalArgumentException("a scheme has a type");
            }
        }
    }

    /**
     * A scheme the document declares in a way that cannot be used: a key with no name, or no place
     * to go, or a reference to a scheme the document does not have.
     *
     * @param why what is wrong with it, in words a person reading the document can act on
     */
    record Unreadable(String why) implements SecurityScheme {

        public Unreadable {
            Objects.requireNonNull(why, "why");
            if (why.isBlank()) {
                throw new IllegalArgumentException("an unreadable scheme says why it is");
            }
        }
    }

    /** What kind of scheme this is, in the words a sentence about it needs. */
    default String described() {
        return switch (this) {
            case ApiKey key -> "an API key in the " + key.location().written() + " " + key.name();
            case Http http -> "an HTTP " + http.scheme() + " credential";
            case Other other -> "a scheme of type " + other.type();
            case Unreadable unreadable -> "a scheme that cannot be used: " + unreadable.why();
        };
    }
}
