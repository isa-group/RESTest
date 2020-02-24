package com.atlassian.oai.validator.schema.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

import java.math.BigDecimal;

public final class DoubleAttribute extends AbstractFormatAttribute {

    private static final FormatAttribute INSTANCE = new DoubleAttribute();

    public static FormatAttribute getInstance() {
        return INSTANCE;
    }

    private DoubleAttribute() {
        super("double", NodeType.NUMBER);
    }

    @Override
    public void validate(final ProcessingReport report,
                         final MessageBundle bundle,
                         final FullData data) throws ProcessingException {
        final JsonNode instance = data.getInstance().getNode();

        final BigDecimal dec = instance.decimalValue();
        final BigDecimal converted = BigDecimal.valueOf(dec.doubleValue());

        if (dec.compareTo(converted) != 0) {
            report.warn(newMsg(data, bundle, "warn.format.double.overflow")
                    .put("key", "warn.format.double.overflow")
                    .putArgument("value", instance)
                    .putArgument("converted", converted));
        }
    }
}
