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
package io.restest.core.oracle;

/**
 * How sure RESTest is that the API itself is what needs fixing, for a kind of fault.
 *
 * <p>Not every fault means the same thing. A reply of 500 says the API fell over, and nothing its
 * document says could excuse that: the API is wrong. A reply with a status code the document never
 * mentions says only that the API and its document disagree, and very often it is the document
 * that is out of date - it lists what goes well and leaves out the ways a request is refused. A
 * developer acts on the first at once and on the second when there is time, and a build should go
 * red for the first and not, unless somebody asks, for the second.
 *
 * <p>So every kind of fault is one of two: an error or a warning. Which one is decided for each
 * kind by {@link FaultReading}, the screen and the report file say it beside every fault, and how a
 * run ends depends on it.
 */
public enum Severity {

    /**
     * The API failed at its job: it fell over, never answered, or accepted what its own document
     * forbids. A run that finds one ends saying so.
     */
    ERROR("error"),

    /**
     * The API answered something its document does not describe, and the document may be the one
     * that is out of date. A run that finds only these ends as one that found nothing wrong, unless
     * it is told to treat them as errors.
     */
    WARNING("warning");

    private final String word;

    Severity(String word) {
        this.word = word;
    }

    /** The word the screen and the report file write for it: {@code error} or {@code warning}. */
    public String word() {
        return word;
    }
}
