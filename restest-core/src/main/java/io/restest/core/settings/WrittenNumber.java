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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * How long a number is once it is written out, and how long one is allowed to be.
 *
 * <p>Length and size are different questions, and this is about length. {@code 1e999999999} is
 * eleven characters to type and a thousand million to write out - so a single line of a settings
 * file can ask the tool for a number that fills its memory the moment anything tries to print it,
 * record it in a report, or put it in a request.
 *
 * <p>The length is worked out from how the number is held rather than by writing it out, which is
 * the whole point: writing it out to find out how long it is has already done the damage. Anything
 * that reads a number somebody typed asks here first.
 */
public final class WrittenNumber {

    /**
     * How many characters a number a run uses may take to write out.
     *
     * <p>Far longer than anything an API would accept in a request, and far shorter than what it
     * takes to run a machine out of memory.
     */
    public static final int LONGEST = 1_000;

    private WrittenNumber() {
    }

    /**
     * Whether this number is too long to write out.
     *
     * @param value the number
     * @return whether writing it out would produce more than {@link #LONGEST} characters
     */
    public static boolean tooLongToWrite(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        // The digits it is held as, plus how far the decimal point is moved. Both come out of the
        // number's own bookkeeping, so neither costs anything however long the number would be.
        return (long) value.precision() + Math.abs((long) value.scale()) > LONGEST;
    }

    /**
     * This number written out, or a refusal if it is too long for that to be safe.
     *
     * @param value the number
     * @param what the name to put in the refusal, so a person knows which line to change
     * @return the number, written the way a person writes one
     * @throws SettingsException if writing it out would produce more than {@link #LONGEST}
     *     characters
     */
    public static String written(BigDecimal value, String what) {
        if (tooLongToWrite(value)) {
            throw new SettingsException(what + " takes more than " + LONGEST + " characters to "
                    + "write down, which is longer than anything a request could carry");
        }
        return value.toPlainString();
    }
}
