package com.atlassian.oai.validator.parameter.format;

import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.google.common.net.InetAddresses;

import static com.atlassian.oai.validator.report.ValidationReport.empty;
import static com.atlassian.oai.validator.report.ValidationReport.singleton;
import static java.util.Objects.requireNonNull;

public class IPv4FormatValidator implements FormatValidator<String> {

    private static final String MESSAGE_KEY = "validation.request.parameter.string.ipv4.invalid";

    private final MessageResolver messages;

    public IPv4FormatValidator(final MessageResolver messages) {
        this.messages = requireNonNull(messages, "A message resolver is required");
    }

    @Override
    public boolean supports(final String format) {
        return "ipv4".equalsIgnoreCase(format);
    }

    @Override
    public ValidationReport validate(final String value) {
        if (InetAddresses.isInetAddress(value) &&
                InetAddresses.forString(value).getAddress().length == 4) {
            return empty();
        }
        return singleton(messages.get(MESSAGE_KEY, value));
    }
}
