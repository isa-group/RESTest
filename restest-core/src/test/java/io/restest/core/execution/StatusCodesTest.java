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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which status codes are a success and which a server error, decided in one place for every module.
 *
 * <p>Only the edges of each family are worth trying, and 999, a code no family holds: everything
 * between the edges is the same answer.
 */
class StatusCodesTest {

    @ParameterizedTest
    @CsvSource({"199, false", "200, true", "299, true", "300, false", "999, false"})
    @DisplayName("a success is a code from 200 to 299")
    void successes(int statusCode, boolean success) {
        assertThat(StatusCodes.isSuccess(statusCode)).isEqualTo(success);
    }

    @ParameterizedTest
    @CsvSource({"499, false", "500, true", "599, true", "600, false", "999, false"})
    @DisplayName("a server error is a code from 500 to 599, however wrong a code above it is")
    void server_errors(int statusCode, boolean serverError) {
        assertThat(StatusCodes.isServerError(statusCode)).isEqualTo(serverError);
    }
}
