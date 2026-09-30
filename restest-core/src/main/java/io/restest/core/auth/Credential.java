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

import java.util.Objects;

/**
 * One secret value and the one place in a request it goes: what a request has added to it on its
 * way out, so that the API can tell who it comes from.
 *
 * <p>An API key given for a scheme becomes one of these for every operation that asks for the
 * scheme, and one more wherever an operation declares an input under the key's name. Printing one
 * shows the place and the text written in the value's place, never the value.
 *
 * @param place where in the request it goes
 * @param secret what goes there
 */
record Credential(Place place, Secret secret) {

    Credential {
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(secret, "secret");
    }
}
