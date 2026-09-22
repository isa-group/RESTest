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

import io.restest.core.settings.LengthOfTime;
import java.time.Duration;
import picocli.CommandLine.ITypeConverter;

/**
 * Reads the length of time a person typed after {@code --budget}.
 *
 * <p>How long to spend testing an API is the one number every run needs, and it is written the way
 * people write lengths of time: {@code 30s}, {@code 5m}, {@code 2h}, {@code 500ms}. That spelling
 * is not this class's own - it is {@link LengthOfTime}, which reads every length of time RESTest
 * accepts anywhere, so that a timeout somebody sets and a budget somebody types are written the
 * same way and neither has to be explained twice.
 *
 * <p>What is this class's own is the one thing a budget asks that a length of time does not: it
 * cannot be zero or negative. A run that is over before it starts sends nothing, and silently
 * accepting that would produce an empty report rather than an explanation.
 */
final class BudgetDuration implements ITypeConverter<Duration> {

    @Override
    public Duration convert(String value) {
        return parse(value);
    }

    /**
     * The length of time the text describes, which has to be one a run can be given.
     *
     * @param value what was typed, for instance {@code 30s}
     * @return that length of time
     * @throws IllegalArgumentException if the text is not a length of time, or is not a positive one
     */
    static Duration parse(String value) {
        Duration parsed = LengthOfTime.parse(value);
        if (parsed.isZero() || parsed.isNegative()) {
            throw new IllegalArgumentException("a budget of '" + value + "' would end the run "
                    + "before it sent anything; give a length of time greater than zero, such as "
                    + "30s");
        }
        return parsed;
    }
}
