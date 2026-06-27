package com.algaworks.algasensors.device.management.api.config.jackson;

import io.hypersistence.tsid.TSID;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StringToTSIDDeserializer extends ValueDeserializer<TSID> {

    @Override
    public TSID deserialize(JsonParser p, DeserializationContext ctxt) {
        return TSID.from(p.getString());
    }
}
