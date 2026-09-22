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

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.event.RunEvent;
import java.io.Flushable;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsoleReportTest {

    private final StringBuilder screen = new StringBuilder();
    private final ConsoleReport report = ConsoleReport.to(screen);

    @Test
    @DisplayName("how many faults belong on a screen is a setting: told two, it prints two and "
            + "says so, while still counting every one of them")
    void how_many_faults_reach_the_screen_is_a_setting() {
        StringBuilder screen = new StringBuilder();
        ConsoleReport report = ConsoleReport.to(screen, java.util.Set.of(),
                new io.restest.core.settings.ReportSettings(5, 1_000, 24L * 1024, 2));

        for (int found = 0; found < 6; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));
        }

        assertThat(screen.toString().split("F100", -1).length - 1)
                .describedAs("two written out, and the line that says the screen has stopped "
                        + "being the right place for them")
                .isEqualTo(2);
        assertThat(report.faults())
                .describedAs("the count is of all of them, printed or not")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("a fault is printed with its kind, its operation, what is wrong, and a command")
    void a_fault_is_printed_with_everything_needed_to_check_it() {
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));

        assertThat(screen.toString())
                .contains("F100")
                .contains("HTTP Status 500")
                .contains("GET /pets - GET https://api.example/pets")
                .contains("the API answered 500")
                .contains("curl -i -X GET 'https://api.example/pets'");
    }

    @Test
    @DisplayName("a fault says what the API answered, which is the first thing anybody asks")
    void a_fault_says_what_came_back() {
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));

        assertThat(screen.toString())
                .describedAs("a developer reads a status code before anything else, and until now "
                        + "this line named the request and never what came back to it")
                .contains("GET /pets - GET https://api.example/pets  ->  500");
    }

    @Test
    @DisplayName("an attempt that got no reply says so rather than showing a code it never got")
    void an_attempt_with_no_reply_says_so() {
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.neverAnswered("GET /gone")));

        assertThat(screen.toString())
                .describedAs("getting nothing back is probably the worst thing an API can do, and "
                        + "it has no status code; inventing a zero would state something false")
                .contains("->  no reply");
    }

    @Test
    @DisplayName("the run says how the API answered overall, not only where it went wrong")
    void the_run_says_how_the_api_answered_overall() {
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 400)));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 400)));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        assertThat(screen.toString())
                .describedAs("two answers in three refused means the requests were the problem, not "
                        + "the API - and a screen that only ever mentions faults cannot say so")
                .contains("1 2xx, 2 4xx");
    }

    @Test
    @DisplayName("the run says on how many operations the API fell over, counting operations not replies")
    void the_run_counts_the_operations_that_fell_over() {
        // Three operations. One answers 500 many times over, one answers 503, one behaves. The
        // number a benchmark compares tools on is how many operations broke, not how many replies
        // were broken, so asking the same broken operation a hundred times must not count a hundred.
        for (int again = 0; again < 100; again++) {
            report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                    Runs.attempt("GET /pets", "/pets", 500)));
        }
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /shelters", "/shelters", 503)));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /vets", "/vets", 200)));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        assertThat(screen.toString())
                .contains("1 operation(s) answered 500, 2 answered some 5xx");
    }

    @Test
    @DisplayName("a run where nothing fell over says nothing about server errors")
    void a_clean_run_says_nothing_about_server_errors() {
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        assertThat(screen.toString()).doesNotContain("answered some 5xx");
    }

    @Test
    @DisplayName("the particular disagreements are listed under the fault")
    void the_details_are_listed() {
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.wrongShape(2)));

        assertThat(screen.toString())
                .contains("/field0: string found, integer expected")
                .contains("/field1: string found, integer expected");
    }

    @Test
    @DisplayName("a fault with very many disagreements is cut short, saying how many remain")
    void a_long_list_of_details_is_cut_short() {
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.wrongShape(25)));

        assertThat(screen.toString())
                .contains("/field9: ")
                .doesNotContain("/field10: ")
                .contains("... and 15 more");
    }

    @Test
    @DisplayName("a run finding the same fault over and over stops filling the screen with it")
    void very_many_faults_stop_being_printed_but_are_still_counted() {
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 500)));
        for (int found = 0; found < 120; found++) {
            report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));
        }
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        String printed = screen.toString();
        assertThat(printed.split("HTTP Status 500", -1).length - 1)
                .describedAs("the screen holds the first 50 faults and the summary line, not 120")
                .isEqualTo(51);
        assertThat(printed)
                .describedAs("and it says why it stopped, rather than quietly losing them")
                .contains("... more faults are being found");
        assertThat(printed)
                .describedAs("while the count at the end is of every one of them")
                .contains("120 faults:")
                .contains("120 x F100");
        assertThat(report.faults()).isEqualTo(120);
    }

    @Test
    @DisplayName("the end of a run says how much was done, what was found, and how much was idle")
    void the_summary_says_what_happened() {
        report.on(new RunEvent.RunStarted(Instant.EPOCH, "Pets", Runs.BASE));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 500)));
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets/{petId}", "/pets/7", 200)));
        report.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofSeconds(10), Runs.engine()));

        assertThat(screen.toString())
                .contains("RESTest testing Pets at https://api.example")
                .contains("2 requests to 2 operations in 10.0s, 30% of it idle")
                .contains("1 fault:")
                .contains("1 x F100  HTTP Status 500");
    }

    @Test
    @DisplayName("a run that found nothing says so plainly")
    void a_clean_run_says_so() {
        report.on(new RunEvent.InteractionCompleted(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 200)));
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofMillis(412), Runs.engine()));

        assertThat(screen.toString()).contains("no faults found");
        assertThat(report.faults()).isZero();
    }

    @Test
    @DisplayName("a run that asked the API nothing does not claim to have found nothing wrong")
    void a_run_that_tested_nothing_says_that_instead() {
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofMillis(412), Runs.engine()));

        assertThat(screen.toString())
                .describedAs("'no faults found' would read as 'this API is fine', which a run that "
                        + "sent nothing has no evidence for")
                .contains("nothing was tested")
                .doesNotContain("no faults found");
    }

    @Test
    @DisplayName("a planned test that was never sent is not printed")
    void a_plan_is_not_news() {
        report.on(new RunEvent.TestCasePlanned(Instant.EPOCH,
                Runs.attempt("GET /pets", "/pets", 200).testCase()));

        assertThat(screen.toString()).isEmpty();
    }

    @Test
    @DisplayName("a length of time is written the way a person would say it")
    void a_duration_reads_the_way_people_say_it() {
        assertThat(ConsoleReport.readable(Duration.ofMillis(412))).isEqualTo("412ms");
        assertThat(ConsoleReport.readable(Duration.ofMillis(6400))).isEqualTo("6.4s");
        assertThat(ConsoleReport.readable(Duration.ofSeconds(124))).isEqualTo("2m 4s");
    }

    @Test
    @DisplayName("a fault reaches the screen the moment it is found, not when the run ends")
    void a_fault_is_sent_on_its_way_as_soon_as_it_is_printed() {
        BufferedScreen terminal = new BufferedScreen();
        ConsoleReport live = ConsoleReport.to(terminal);

        live.on(new RunEvent.FaultFound(Instant.EPOCH, Runs.fellOver()));

        assertThat(terminal.shown())
                .describedAs("the terminal only shows what has actually been sent to it; a fault "
                        + "left sitting in the buffer is a fault nobody watching the run can see, "
                        + "which is what printing them one at a time was for")
                .contains("F100");
    }

    /** A screen that shows only what has been sent on its way, the way a real terminal does. */
    private static final class BufferedScreen implements Appendable, Flushable {

        private final StringBuilder waiting = new StringBuilder();
        private final StringBuilder shown = new StringBuilder();

        @Override
        public Appendable append(CharSequence text) {
            waiting.append(text);
            return this;
        }

        @Override
        public Appendable append(CharSequence text, int start, int end) {
            waiting.append(text, start, end);
            return this;
        }

        @Override
        public Appendable append(char character) {
            waiting.append(character);
            return this;
        }

        @Override
        public void flush() {
            shown.append(waiting);
            waiting.setLength(0);
        }

        String shown() {
            return shown.toString();
        }
    }
}
