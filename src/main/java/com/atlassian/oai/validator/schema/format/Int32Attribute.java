package com.atlassian.oai.validator.schema.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

public final class Int32Attribute extends AbstractFormatAttribute {

    private static final FormatAttribute INSTANCE = new Int32Attribute();

    public static FormatAttribute getInstance() {
        return INSTANCE;
    }

    private Int32Attribute() {
        super("int32", NodeType.INTEGER);
    }

    @Override
    public void validate(final ProcessingReport report,
                         final MessageBundle bundle,
                         final FullData data) throws ProcessingException {
        final JsonNode instance = data.getInstance().getNode();

        if (!instance.canConvertToInt()) {
            report.warn(newMsg(data, bundle, "warn.format.int32.overflow")
                    .put("key", "warn.format.int32.overflow")
                    .putArgument("value", instance));
        }
    }
}
