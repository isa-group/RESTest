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
 * What kind of thing one setting holds, so that a value nobody could use is refused with an
 * explanation rather than accepted and misread.
 *
 * <p>Every setting is typed in as text - in a file, in an environment variable, after {@code --set}
 * - and every one of them means something more particular than text. This says which, in the words
 * a refusal uses.
 */
public enum SettingKind {

    /** A count of something: {@code 8}, {@code 1000}. */
    WHOLE_NUMBER("a whole number"),

    /** A number that may have a decimal point: {@code 2.5}, {@code 0.25}. */
    NUMBER("a number"),

    /** A length of time, written {@code 500ms}, {@code 30s}, {@code 5m} or {@code 2h}. */
    LENGTH_OF_TIME("a length of time, such as 30s"),

    /** {@code true} or {@code false}. */
    YES_OR_NO("true or false"),

    /** Anything else somebody types. */
    TEXT("some text");

    private final String described;

    SettingKind(String described) {
        this.described = described;
    }

    /** What this kind is called when a refusal has to name it. */
    public String described() {
        return described;
    }
}
