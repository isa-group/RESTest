package com.atlassian.oai.validator.report;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ValidationReport} serving as container for multiple {@link ValidationReport}s.
 * <p>
 * This {@link MergedValidationReport} is immutable.
 */
public class MergedValidationReport implements ValidationReport {

    private final ImmutableList<ValidationReport> reports;

    MergedValidationReport(final ValidationReport validationReport1, final ValidationReport validationReport2) {
        final ImmutableList.Builder<ValidationReport> reportsBuilder = new ImmutableList.Builder<>();
        collect(reportsBuilder, validationReport1);
        collect(reportsBuilder, validationReport2);
        this.reports = reportsBuilder.build();
    }

    @Nonnull
    @Override
    public List<Message> getMessages() {
        return unmodifiableList(reports.stream().flatMap(r -> r.getMessages().stream()).collect(toList()));
    }

    @Override
    public boolean hasErrors() {
        return reports.stream().anyMatch(ValidationReport::hasErrors);
    }

    private ImmutableList<ValidationReport> getReports() {
        return reports;
    }

    private static void collect(final ImmutableList.Builder<ValidationReport> reportBuilder, final ValidationReport report) {
        if (report instanceof EmptyValidationReport) {
            return;
        }

        if (report instanceof MergedValidationReport) {
            reportBuilder.addAll(((MergedValidationReport) report).getReports());
            return;
        }

        reportBuilder.add(report);
    }
}
