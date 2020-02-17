package com.atlassian.oai.validator.report;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ValidationReport} containing a single {@link Message}.
 * <p>
 * This {@link ImmutableValidationReport} is immutable.
 */
public class ImmutableValidationReport implements ValidationReport {

    private final List<Message> messages;

    ImmutableValidationReport(final Message message) {
        if (message == null) {
            messages = Collections.emptyList();
            return;
        }
        this.messages = ImmutableList.of(message);
    }

    ImmutableValidationReport(final Message... messages) {
        if (messages == null || messages.length == 0) {
            this.messages = Collections.emptyList();
            return;
        }
        this.messages = ImmutableList.copyOf(stream(messages).filter(Objects::nonNull).collect(toList()));
    }

    @Nonnull
    @Override
    public List<Message> getMessages() {
        return messages;
    }

    @Override
    public String toString() {
        return getMessages().toString();
    }
}
