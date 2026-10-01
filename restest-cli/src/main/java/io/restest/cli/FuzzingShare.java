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

import io.restest.gen.Campaign;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Reads the percentage a person typed after {@code --fuzzing}: how much of a run goes on values
 * chosen to be awkward, such as empty text or enormous numbers.
 *
 * <p>The run command hands that percentage to the plan the run follows, which shares it among the
 * plan's strategies that push at the API; {@link Campaign#aShareOfPushing} is where a share is
 * judged, so the command line and the plan refuse the same values in the same words. This class
 * only makes that judgement happen while the command line is read, the way a word typed where a
 * number belongs is refused - and so before the API's description is fetched or a single request
 * is built. Accepting the mistake and finding out later would make somebody wait for a large
 * description to download only to be told they made a typing mistake, and a missing description
 * would be reported instead of the mistake they made.
 */
final class FuzzingShare implements ITypeConverter<Integer> {

    @Override
    public Integer convert(String value) {
        int share;
        try {
            share = Integer.parseInt(value.strip());
        } catch (NumberFormatException notAWholeNumber) {
            throw new TypeConversionException("'" + value + "' is not a percentage; give a whole "
                    + "number between 0 and " + Campaign.WHOLE + ", such as 25");
        }
        try {
            return Campaign.aShareOfPushing(share);
        } catch (IllegalArgumentException notAPercentage) {
            throw new TypeConversionException(notAPercentage.getMessage());
        }
    }
}
