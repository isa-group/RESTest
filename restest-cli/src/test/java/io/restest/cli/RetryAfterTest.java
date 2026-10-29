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

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How long a reply asks to be left alone, read from its {@code Retry-After} header alone.
 */
class RetryAfterTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    /** A run with an hour left. */
    private static final Instant LATEST = NOW.plus(Duration.ofHours(1));

    @Test
    @DisplayName("a number of seconds is a wait of that many seconds from now")
    void seconds() {
        assertThat(RetryAfter.until(reply("Retry-After", "30"), NOW, LATEST))
                .contains(NOW.plusSeconds(30));
        assertThat(RetryAfter.until(reply("Retry-After", "  7 "), NOW, LATEST))
                .contains(NOW.plusSeconds(7));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Thu, 29 Oct 2026 10:00:45 GMT", "Thursday, 29-Oct-26 10:00:45 GMT",
            "Thu Oct 29 10:00:45 2026"})
    @DisplayName("a moment is read in each of the three ways HTTP writes one")
    void each_way_of_writing_a_moment(String moment) {
        assertThat(RetryAfter.until(reply("Retry-After", moment), NOW, LATEST))
                .contains(NOW.plusSeconds(45));
    }

    @Test
    @DisplayName("a moment whose day name is wrong is read by its date, in the current way as in the old ones")
    void a_wrong_day_name_changes_nothing() {
        // 29 October 2026 is a Thursday.
        for (String moment : List.of("Wed, 29 Oct 2026 10:00:45 GMT",
                "Wednesday, 29-Oct-26 10:00:45 GMT", "Wed Oct 29 10:00:45 2026")) {
            assertThat(RetryAfter.until(reply("Retry-After", moment), NOW, LATEST))
                    .describedAs(moment).contains(NOW.plusSeconds(45));
        }
    }

    @Test
    @DisplayName("asctime pads a day of one digit with a space")
    void asctime_with_a_day_of_one_digit() {
        Instant fifth = Instant.parse("2026-11-05T09:00:00Z");

        assertThat(RetryAfter.until(reply("Retry-After", "Thu Nov  5 09:00:30 2026"), fifth,
                fifth.plus(Duration.ofHours(1)))).contains(fifth.plusSeconds(30));
    }

    @Test
    @DisplayName("a two-digit year more than fifty years ahead is the one a century before, so 94 is past")
    void a_two_digit_year_is_read_as_http_says() {
        // 1994, not 2094: a wait of no time at all, never one past the end of the run.
        assertThat(RetryAfter.until(reply("Retry-After", "Sunday, 06-Nov-94 08:49:37 GMT"), NOW,
                LATEST)).contains(NOW);
    }

    @Test
    @DisplayName("a moment is measured from the reply's own Date, so a clock out of step changes nothing")
    void a_moment_is_measured_from_the_replys_date() {
        // The API's clock is five minutes behind ours; it asks for twenty seconds.
        Interaction reply = reply("Date", "Thu, 29 Oct 2026 09:55:00 GMT",
                "Retry-After", "Thu, 29 Oct 2026 09:55:20 GMT");

        assertThat(RetryAfter.until(reply, NOW, LATEST)).contains(NOW.plusSeconds(20));
    }

    @Test
    @DisplayName("a Date that cannot be read leaves the moment measured from the run's own clock")
    void an_unreadable_date_falls_back_to_the_clock() {
        Interaction reply = reply("Date", "yesterday",
                "Retry-After", "Thu, 29 Oct 2026 10:00:20 GMT");

        assertThat(RetryAfter.until(reply, NOW, LATEST)).contains(NOW.plusSeconds(20));
    }

    @Test
    @DisplayName("a moment already past, and 0, are a wait of no time at all")
    void past_and_zero_wait_no_time() {
        assertThat(RetryAfter.until(reply("Retry-After", "Thu, 29 Oct 2026 09:00:00 GMT"), NOW,
                LATEST)).contains(NOW);
        assertThat(RetryAfter.until(reply("Retry-After", "0"), NOW, LATEST)).contains(NOW);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"86400", "100000000000000000", "9223372036854775807",
            "99999999999999999999999999", "Fri, 31 Dec 9999 23:59:59 GMT"})
    @DisplayName("a wait longer than the run has left ends where the run does, and never fails")
    void a_wait_past_the_end_ends_with_the_run(String value) {
        assertThat(RetryAfter.until(reply("Retry-After", value), NOW, LATEST)).contains(LATEST);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "soon", "-5", "1.5", "1e3", "+30", "Thu, 32 Oct 2026 10:00:00 GMT",
            "Sunday, 06-Xyz-94 08:49:37 GMT", "Sun Nov 31 08:49:37 1994"})
    @DisplayName("anything that cannot be read is as if the header were not there")
    void what_cannot_be_read_is_nothing(String value) {
        assertThat(RetryAfter.until(reply("Retry-After", value), NOW, LATEST)).isEmpty();
    }

    @Test
    @DisplayName("a reply without the header says nothing")
    void no_header_says_nothing() {
        assertThat(RetryAfter.until(reply(), NOW, LATEST)).isEmpty();
    }

    @Test
    @DisplayName("of several Retry-After headers, the first that can be read counts")
    void the_first_readable_header_counts() {
        assertThat(RetryAfter.until(reply("Retry-After", "later", "Retry-After", "12",
                "Retry-After", "40"), NOW, LATEST)).contains(NOW.plusSeconds(12));
    }

    @Test
    @DisplayName("a run already past its end waits no time, whatever the reply says")
    void a_run_past_its_end_waits_nothing() {
        assertThat(RetryAfter.until(reply("Retry-After", "30"), NOW, NOW.minusSeconds(1)))
                .contains(NOW);
    }

    @Test
    @DisplayName("the headers of a reply whose body broke off are read too")
    void a_reply_cut_short_is_read() {
        Interaction cutShort = Interaction.malformedResponse(testCase(),
                HttpRequestRecord.of(HttpMethod.GET, "http://localhost/pets"),
                "the reply stopped after 3 bytes", Optional.of(StatusLine.of(503)),
                List.of(Header.of("Retry-After", "5")), Optional.empty(), NOW, Duration.ZERO);

        assertThat(RetryAfter.until(cutShort, NOW, LATEST)).contains(NOW.plusSeconds(5));
    }

    /** A reply of 429 carrying the given headers, as name and value in turn. */
    private static Interaction reply(String... namesAndValues) {
        return reply(429, NOW, namesAndValues);
    }

    /**
     * A reply with this status to a request sent at that moment, carrying the given headers, as
     * name and value in turn.
     */
    static Interaction reply(int status, Instant sentAt, String... namesAndValues) {
        List<Header> headers = new ArrayList<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            headers.add(Header.of(namesAndValues[i], namesAndValues[i + 1]));
        }
        return Interaction.answered(testCase(),
                HttpRequestRecord.of(HttpMethod.GET, "http://localhost/pets"),
                new HttpResponseRecord(StatusLine.of(status), headers, Optional.empty()), sentAt,
                Duration.ZERO);
    }

    private static TestCase testCase() {
        return TestCase.of(OperationId.synthesised(HttpMethod.GET, "/pets"), List.of());
    }
}
