package com.algaworks.algasensors.device.management.api.config.jackson;

import io.hypersistence.tsid.TSID;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class TSIDToStringSerializer extends ValueSerializer<TSID> {

    @Override
    public void serialize(TSID value, JsonGenerator gen, SerializationContext context) {
        gen.writeString(value.toString());
    }
}
