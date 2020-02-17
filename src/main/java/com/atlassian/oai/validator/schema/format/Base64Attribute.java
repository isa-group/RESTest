package com.atlassian.oai.validator.schema.format;

import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Base64Attribute extends AbstractFormatAttribute {

    private static final Pattern BASE64_PATTERN = Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");

    private static final FormatAttribute INSTANCE = new Base64Attribute();

    public static FormatAttribute getInstance() {
        return INSTANCE;
    }

    private Base64Attribute() {
        super("byte", NodeType.STRING);
    }

    @Override
    public void validate(final ProcessingReport report,
                         final MessageBundle bundle,
                         final FullData data) throws ProcessingException {
        final String value = data.getInstance().getNode().textValue();
        final Matcher matcher = BASE64_PATTERN.matcher(value);

        if (!matcher.matches()) {
            report.error(newMsg(data, bundle, "err.format.base64.invalid")
                    .put("key", "err.format.base64.invalid"));
        }
    }
}
