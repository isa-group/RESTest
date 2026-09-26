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

/**
 * Whether a run sends requests in sequences - one request, then another built from what the first
 * came back with - rather than only one at a time.
 *
 * <p>Most requests a run sends stand alone. Some cannot: asking for one pet needs the identifier of
 * a pet that exists, and when the run knows of none, the only way to have one is to create it and
 * ask straight after, with the identifier the creation came back with. That pair of requests is a
 * sequence, and this is where it is switched on or off, so an experiment can measure what it is
 * worth without a change to the tool.
 *
 * @param pairs whether a request that needs the identifier of a thing nobody has is sent straight
 *     after a request that creates one, with the identifier that creation came back with
 */
public record SequenceSettings(boolean pairs) {

    private static final SequenceSettings DEFAULTS = new SequenceSettings(true);

    /** What a run does when nobody has said otherwise. */
    public static SequenceSettings defaults() {
        return DEFAULTS;
    }
}
