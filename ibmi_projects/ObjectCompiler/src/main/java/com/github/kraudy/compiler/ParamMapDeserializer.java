package com.github.kraudy.compiler;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ParamMapDeserializer extends JsonDeserializer<Map<ParamCmd, Object>> {

    @Override
    public Map<ParamCmd, Object> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JacksonException {

        ObjectNode node = p.getCodec().readTree(p);
        Map<ParamCmd, Object> result = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode valueNode = field.getValue();

            ParamCmd param = safeValueOf(key);
            Object value = valueNode.isTextual() ? valueNode.asText() :
                           valueNode.isBoolean() ? valueNode.asBoolean() :
                           valueNode.isInt() ? valueNode.asInt() :
                           valueNode.traverse(p.getCodec()).readValueAs(Object.class);

            // Optional: auto-convert "*SOURCE" → ValCmd.SOURCE
            if (value instanceof String) {
              String str = (String) value;
              try {
                  value = com.github.kraudy.compiler.CompilationPattern.ValCmd.fromString(str);
              } catch (Exception ignored) {}
            }

            result.put(param, value);
        }
        return result;
    }

    private ParamCmd safeValueOf(String name) {
        try {
            return ParamCmd.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid compilation parameter: '" + name + "'. " +
                "Valid parameters: " + validParamList());
        }
    }

    private static String validParamList() {
        return String.join(", ", 
            java.util.Arrays.stream(ParamCmd.values())
                .map(Enum::name)
                .toList());
    }
}