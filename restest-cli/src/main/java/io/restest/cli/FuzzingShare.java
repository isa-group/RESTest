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
package io.restest.cli;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Reads the percentage a person typed after {@code --fuzzing}: how much of a run goes on values
 * chosen to be awkward.
 *
 * <p>Anything from 0 to 100 is a share a run can be given. Anything else is refused here, while the
 * command line is read, the way a word typed where a number belongs is refused - and so before
 * the API's description is fetched or a single request is built. Accepting it and finding out
 * later would make somebody wait for a large description to download only to be told they made a
 * typing mistake, and a missing description would be reported instead of the mistake they made.
 */
final class FuzzingShare implements ITypeConverter<Integer> {

    /** The most a share can be: all of the run. */
    static final int WHOLE = 100;

    @Override
    public Integer convert(String value) {
        int share;
        try {
            share = Integer.parseInt(value.strip());
        } catch (NumberFormatException notAWholeNumber) {
            throw new TypeConversionException("'" + value + "' is not a percentage; give a whole "
                    + "number between 0 and " + WHOLE + ", such as 25");
        }
        if (share < 0 || share > WHOLE) {
            throw new TypeConversionException("the share of requests built to be refused is a "
                    + "percentage, so it is between 0 and " + WHOLE + ": " + share);
        }
        return share;
    }
}
