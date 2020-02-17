package com.atlassian.oai.validator.report;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

class ImmutableMessage implements ValidationReport.Message {

    private final String key;
    private final ValidationReport.Level level;
    private final String message;
    private final List<String> additionalInfo;

    ImmutableMessage(@Nonnull final String key,
                     @Nonnull final ValidationReport.Level level,
                     @Nonnull final String message,
                     @Nonnull final String... additionalInfo) {
        this(key, level, message, asList(additionalInfo));
    }

    ImmutableMessage(@Nonnull final String key,
                     @Nonnull final ValidationReport.Level level,
                     @Nonnull final String message,
                     @Nonnull final List<String> additionalInfo) {

        this.key = requireNonNull(key, "A key is required");
        this.level = requireNonNull(level, "A level is required");
        this.message = requireNonNull(message, "A message is required");
        this.additionalInfo = unmodifiableList(requireNonNull(additionalInfo));
    }

    @Override
    public ValidationReport.Message withLevel(final ValidationReport.Level level) {
        return new ImmutableMessage(key, level, message, additionalInfo.toArray(new String[additionalInfo.size()]));
    }

    @Override
    public ValidationReport.Message withAdditionalInfo(final String info) {
        return new ImmutableMessage(
                key, level, message,
                ImmutableList.<String>builder().addAll(additionalInfo).add(info).build()
        );
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public ValidationReport.Level getLevel() {
        return level;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return level + " - "
                + message.replace("\n", "\n\t")
                + ": [" + additionalInfo.stream().collect(joining(", ")) + "]";
    }

    @Override
    public List<String> getAdditionalInfo() {
        return additionalInfo;
    }
}
