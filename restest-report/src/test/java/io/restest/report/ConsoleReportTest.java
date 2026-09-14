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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsoleReportTest {

    private final StringBuilder screen = new StringBuilder();
    private final ConsoleReport report = ConsoleReport.to(screen);

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
        report.on(new RunEvent.RunFinished(Instant.EPOCH, Duration.ofMillis(412), Runs.engine()));

        assertThat(screen.toString()).contains("no faults found");
        assertThat(report.faults()).isZero();
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
}
