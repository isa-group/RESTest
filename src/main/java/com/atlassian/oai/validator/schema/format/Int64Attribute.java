package com.atlassian.oai.validator.schema.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

public final class Int64Attribute extends AbstractFormatAttribute {

    private static final FormatAttribute INSTANCE = new Int64Attribute();

    public static FormatAttribute getInstance() {
        return INSTANCE;
    }

    private Int64Attribute() {
        super("int64", NodeType.INTEGER);
    }

    @Override
    public void validate(final ProcessingReport report,
                         final MessageBundle bundle,
                         final FullData data) throws ProcessingException {
        final JsonNode instance = data.getInstance().getNode();

        if (!instance.canConvertToLong()) {
            report.warn(newMsg(data, bundle, "warn.format.int64.overflow")
                    .put("key", "warn.format.int64.overflow")
                    .putArgument("value", instance));
        }
    }
}
