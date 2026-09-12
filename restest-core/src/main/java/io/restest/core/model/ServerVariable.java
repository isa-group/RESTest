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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A variable in a server's URL template, and what the document says to put there.
 *
 * <p>OpenAPI requires a default for every server variable, which is what makes
 * {@link Server#resolvedUrl()} possible and design principle 1 - zero configuration to start -
 * keepable for an API whose only declared server is templated. Dropping the variables would leave
 * the tool unable to send a single request to such an API without {@code --url}, using a document
 * that said exactly where to send it.
 *
 * @param defaultValue the value to use when nobody chooses another. Always present in a valid
 *     document
 * @param allowed the values the document restricts the variable to, empty when it does not
 * @param description what the document says the variable selects
 */
public record ServerVariable(String defaultValue, List<String> allowed,
        Optional<String> description) {

    public ServerVariable {
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(description, "description");
        allowed = List.copyOf(allowed);
        if (!allowed.isEmpty() && !allowed.contains(defaultValue)) {
            throw new IllegalArgumentException("the default '" + defaultValue
                    + "' is not one of the values the document allows: " + allowed);
        }
    }

    /** A variable with the given default and no restriction on its values. */
    public static ServerVariable of(String defaultValue) {
        return new ServerVariable(defaultValue, List.of(), Optional.empty());
    }
}
