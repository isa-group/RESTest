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
package io.restest.core.execution;

import java.util.Objects;
import java.util.Optional;

/**
 * The first line of an HTTP response: the status code, and the two facts that travel with it on
 * the wire.
 *
 * <p>Bundled into one type rather than three separate components on each of
 * {@link HttpResponseRecord} and {@link InteractionOutcome.MalformedResponse}, for two reasons.
 * First, the three facts cannot arrive independently on the wire: a reason phrase or a protocol
 * version without a status code is not a shape HTTP parsing can ever produce, and a bundled type
 * makes that shape unconstructable instead of merely undocumented. Second, {@code reasonPhrase} and
 * {@code protocolVersion} are both {@code Optional<String>} - the same type, adjacent - which on two
 * separate constructor parameters is exactly the transposition a caller can make silently and a
 * compiler cannot catch; naming them as components of one small, single-purpose type is what makes a
 * transposition visible in a diff.
 *
 * @param statusCode the status code
 * @param reasonPhrase the reason phrase, when the protocol carries one and it parsed - HTTP/2 and
 *     HTTP/3 do not
 * @param protocolVersion the protocol version, as reported by the HTTP client - {@code "HTTP/1.1"},
 *     {@code "HTTP/2"} - kept as a plain string rather than a fixed set of values, since it is simply
 *     reported as given and is not this record's job to standardise
 */
public record StatusLine(int statusCode, Optional<String> reasonPhrase,
        Optional<String> protocolVersion) {

    public StatusLine {
        Objects.requireNonNull(reasonPhrase, "reasonPhrase");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
    }

    /** A status line with only the code, nothing else known. */
    public static StatusLine of(int statusCode) {
        return new StatusLine(statusCode, Optional.empty(), Optional.empty());
    }
}
