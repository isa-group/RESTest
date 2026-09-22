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
package io.restest.core.settings;

import java.time.Duration;
import java.util.Objects;

/**
 * What the tool will accept when it goes and fetches the description of an API.
 *
 * <p>A description can be a file on this machine, but it can also be a web address, and then it is
 * something a stranger serves. These two numbers are what keeps that from being a way to stop the
 * run: a server that never answers costs the run the first of them and no more, and a server that
 * answers for ever costs it the second and no more.
 *
 * @param fetchTimeout how long to wait for a description fetched over the network, both to connect
 *     and to answer. Generous, but not unbounded: a hung server must not hang the run that asked
 *     for its document
 * @param mostBytesRead the largest description that is read at all. Far larger than any real one; a
 *     bound against an answer that never ends
 */
public record DocumentSettings(Duration fetchTimeout, int mostBytesRead) {

    private static final DocumentSettings DEFAULTS =
            new DocumentSettings(Duration.ofSeconds(10), 64 * 1024 * 1024);

    public DocumentSettings {
        Objects.requireNonNull(fetchTimeout, "fetchTimeout");
        if (fetchTimeout.isNegative() || fetchTimeout.isZero()) {
            throw new IllegalArgumentException("fetchTimeout must be greater than zero, or no "
                    + "description could ever be fetched: " + fetchTimeout);
        }
        if (mostBytesRead < 1) {
            throw new IllegalArgumentException("mostBytesRead must be at least 1; reading none of "
                    + "a description leaves nothing to test: " + mostBytesRead);
        }
    }

    /** What a run does when nobody has said otherwise. */
    public static DocumentSettings defaults() {
        return DEFAULTS;
    }
}
