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
package io.restest.report;

import io.restest.core.execution.Interaction;

/**
 * Which family the API's answer to a request belongs to: {@code 2xx} and the rest, or no answer at
 * all.
 *
 * <p>Every count of replies in the report a program reads is made this way - the whole run, each
 * stretch of it, each slice of its time - so that a count in one part of it can be laid beside a
 * count in another. The screen names the families the same way, and says "no reply" in words of
 * its own.
 *
 * <p>By family rather than by exact code on purpose. As more kinds of fault are looked for, an
 * exact-code list becomes a long tail of 401, 403, 404, 409, 422 that summarises nothing. The exact
 * codes are still counted, by {@link JsonReport}, and written beside the families.
 *
 * <p>An attempt that got nothing back - a timeout, a closed connection, a reply that was not HTTP -
 * has no status code, and is probably the worst thing an API can do. A count that only knew about
 * codes would lose it, so it has a name of its own here.
 */
final class StatusClass {

    /** What an attempt that got nothing back is counted under. */
    static final String NO_REPLY = "noReply";

    private StatusClass() {
    }

    /** The family of a status code: {@code 2xx} for 201, {@code 5xx} for 503. */
    static String of(int status) {
        return (status / 100) + "xx";
    }

    /** The family of what came back to an attempt, or {@link #NO_REPLY} when nothing did. */
    static String of(Interaction interaction) {
        return interaction.statusCode().map(StatusClass::of).orElse(NO_REPLY);
    }
}
