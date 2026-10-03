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
package io.restest.spec;

import io.restest.core.model.ParameterLocation;
import io.restest.core.model.SecurityRequirement;
import io.restest.core.model.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads what a document says a request has to prove before the API answers it: the ways of proving
 * who one is that it declares, and which of them it asks for.
 *
 * <p>Nothing here may cost a document anything else. A run that was handed no key has to test an
 * API exactly as it did before any of this was read, so a declaration this class cannot make sense
 * of - a scheme with no type, a reference that goes nowhere or round in a circle, an alternative
 * that is not there - becomes a scheme marked unreadable, or no requirement at all, and never an
 * exception that would take an operation, or the whole document, down with it.
 *
 * <p>A document written for Swagger 2.0 arrives here already converted, its
 * {@code securityDefinitions} as the schemes of the newer format and its {@code basic} as an HTTP
 * scheme; so every version is read by the same few lines.
 */
final class SecurityConverter {

    private SecurityConverter() {
    }

    /**
     * The ways of proving who one is that a document declares, under their names, in its order.
     *
     * @param components where the document declares them, if it has anywhere at all
     * @return each scheme as the model holds it; one that cannot be used is kept, marked as such, so
     *     that a key handed over for it can be refused with the reason
     */
    static Map<String, SecurityScheme> schemes(Components components) {
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        try {
            Map<String, io.swagger.v3.oas.models.security.SecurityScheme> declared =
                    components == null ? null : components.getSecuritySchemes();
            if (declared == null) {
                return schemes;
            }
            declared.forEach((name, scheme) -> {
                if (name == null) {
                    return;
                }
                schemes.put(name, converted(scheme, declared));
            });
        } catch (RuntimeException unexpected) {
            // Nothing above is expected to throw. If something does, the document keeps every
            // scheme read so far and loses the rest - never its operations.
            return schemes;
        }
        return schemes;
    }

    /**
     * What a document's {@code security} list asks for, as the model holds it.
     *
     * @param written the list as the document wrote it, or {@code null} where it wrote none
     * @return nothing when the document said nothing; a requirement with no alternatives when it
     *     said {@code security: []}
     */
    static Optional<SecurityRequirement> requirement(
            List<io.swagger.v3.oas.models.security.SecurityRequirement> written) {
        if (written == null) {
            return Optional.empty();
        }
        try {
            List<Set<String>> alternatives = new ArrayList<>();
            for (io.swagger.v3.oas.models.security.SecurityRequirement alternative : written) {
                Set<String> names = new LinkedHashSet<>();
                // An entry written as nothing at all - `security: [~]` - asks for no scheme, which
                // is how the document says anybody may call: read as the empty alternative.
                if (alternative != null) {
                    alternative.keySet().stream()
                            .filter(name -> name != null && !name.isBlank())
                            .forEach(names::add);
                }
                alternatives.add(names);
            }
            return Optional.of(new SecurityRequirement(alternatives));
        } catch (RuntimeException unexpected) {
            return Optional.empty();
        }
    }

    private static SecurityScheme converted(io.swagger.v3.oas.models.security.SecurityScheme written,
            Map<String, io.swagger.v3.oas.models.security.SecurityScheme> declared) {
        try {
            io.swagger.v3.oas.models.security.SecurityScheme scheme = resolved(written, declared);
            if (scheme == null) {
                return new SecurityScheme.Unreadable("it refers to a scheme the document does "
                        + "not declare, or to one that refers back to it");
            }
            if (scheme.getType() == null) {
                return new SecurityScheme.Unreadable("it does not say what kind of scheme it is");
            }
            return switch (scheme.getType()) {
                case APIKEY -> key(scheme);
                case HTTP -> scheme.getScheme() == null || scheme.getScheme().isBlank()
                        ? new SecurityScheme.Unreadable("it is an HTTP scheme that does not say "
                                + "which one, such as bearer or basic")
                        : new SecurityScheme.Http(scheme.getScheme(),
                                Optional.ofNullable(scheme.getBearerFormat())
                                        .filter(format -> !format.isBlank()));
                case OAUTH2, OPENIDCONNECT, MUTUALTLS ->
                        new SecurityScheme.Other(scheme.getType().toString());
            };
        } catch (RuntimeException unexpected) {
            return new SecurityScheme.Unreadable("RESTest could not read it: "
                    + (unexpected.getMessage() == null
                            ? unexpected.getClass().getSimpleName() : unexpected.getMessage()));
        }
    }

    private static SecurityScheme key(io.swagger.v3.oas.models.security.SecurityScheme scheme) {
        if (scheme.getIn() == null) {
            return new SecurityScheme.Unreadable("it is a key that does not say whether it goes in "
                    + "a header, the query or a cookie");
        }
        ParameterLocation location = switch (scheme.getIn()) {
            case HEADER -> ParameterLocation.HEADER;
            case QUERY -> ParameterLocation.QUERY;
            case COOKIE -> ParameterLocation.COOKIE;
        };
        if (scheme.getName() == null || scheme.getName().isBlank()) {
            return new SecurityScheme.Unreadable("it is a key that goes in the "
                    + location.written() + " but does not say under which name");
        }
        return new SecurityScheme.ApiKey(location, scheme.getName());
    }

    /**
     * The scheme itself, or the one a chain of references to others ends at; {@code null} if a link
     * is missing or the chain comes back to a name it has already been through.
     */
    private static io.swagger.v3.oas.models.security.SecurityScheme resolved(
            io.swagger.v3.oas.models.security.SecurityScheme scheme,
            Map<String, io.swagger.v3.oas.models.security.SecurityScheme> declared) {
        Set<String> visited = new HashSet<>();
        io.swagger.v3.oas.models.security.SecurityScheme current = scheme;
        while (current != null) {
            String reference = current.get$ref();
            if (reference == null) {
                return current;
            }
            if (!visited.add(reference)) {
                return null;
            }
            int lastSlash = reference.lastIndexOf('/');
            current = declared.get(lastSlash < 0 ? reference : reference.substring(lastSlash + 1));
        }
        return null;
    }
}
