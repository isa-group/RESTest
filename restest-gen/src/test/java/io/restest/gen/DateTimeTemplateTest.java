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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A date or a time written in the form a description shows, and in no other. */
class DateTimeTemplateTest {

    private static final RandomGenerator RANDOM = Schemas.fixedRandom();

    @ParameterizedTest(name = "{0} is written like {1}")
    @CsvSource(delimiter = '|', value = {
        "YYYY-MM-DDThh:mm:ssZ | \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z",
        "yyyy-MM-dd'T'HH:mm:ss | \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}",
        "YYYY-MM-DDTHH:MM:SSZ | \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z",
        "yyyy-MM-ddTHH:mm:ss.SSSXXX | \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00",
        "YYYYMMDD | \\d{8}",
        "YYYY-MM | \\d{4}-\\d{2}",
        "YYYY | \\d{4}",
        "dd/MM/yyyy | \\d{2}/\\d{2}/\\d{4}",
        "MM/dd/yyyy | \\d{2}/\\d{2}/\\d{4}",
        "hh:mm | \\d{2}:\\d{2}",
        "yyyy-MM-dd HH:mm | \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"})
    void a_template_is_followed(String template, String shape) {
        String value = DateTimeTemplate.fromLetters(template).orElseThrow().valueFor(RANDOM);

        assertThat(value).matches(shape);
    }

    @RepeatedTest(20)
    @DisplayName("the month, the day and the hour are always ones that exist")
    void every_part_is_in_range() {
        String value = DateTimeTemplate.fromLetters("dd/MM/yyyy HH:mm").orElseThrow()
                .valueFor(RANDOM);
        int day = Integer.parseInt(value.substring(0, 2));
        int month = Integer.parseInt(value.substring(3, 5));
        int hour = Integer.parseInt(value.substring(11, 13));

        assertThat(day).isBetween(1, 31);
        assertThat(month).isBetween(1, 12);
        assertThat(hour).isBetween(0, 23);
    }

    @ParameterizedTest(name = "{0} reads as {1}")
    @CsvSource(delimiter = '|', value = {
        "1996-08-01T00:00:00 | yyyy-MM-dd'T'HH:mm:ss",
        "1963-11-22T18:30:00Z | yyyy-MM-dd'T'HH:mm:ssZ",
        "1970-01-01T01:00+01:00 | yyyy-MM-dd'T'HH:mm+hh:mm",
        "2016-05-25 | yyyy-MM-dd",
        "2011-01 | yyyy-MM",
        "2010-08-14T13:00:00.123Z | yyyy-MM-dd'T'HH:mm:ss.SSSZ"})
    void a_sample_gives_its_form(String sample, String template) {
        assertThat(DateTimeTemplate.fromSample(sample)).map(DateTimeTemplate::written)
                .contains(template);
    }

    @ParameterizedTest(name = "{0} is no date")
    @ValueSource(strings = {"2017-13-01", "2017-08-32", "2017-08-01T25:00", "1234-56"})
    void a_sample_that_could_not_exist(String sample) {
        assertThat(DateTimeTemplate.fromSample(sample)).isEmpty();
    }

    @ParameterizedTest(name = "{0} is no template")
    @ValueSource(strings = {"ZZ", "MM-DD", "hello", "yyyy-QQ"})
    void words_that_are_not_a_template(String words) {
        assertThat(DateTimeTemplate.fromLetters(words)).isEmpty();
    }
}
