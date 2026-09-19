package com.ticketrush.hold;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class LongListJsonConverter implements AttributeConverter<List<Long>, String> {

    @Override
    public String convertToDatabaseColumn(List<Long> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < attribute.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(attribute.get(i));
        }
        builder.append(']');
        return builder.toString();
    }

    @Override
    public List<Long> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }

        String normalized = dbData.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.equals("[]")) {
            return List.of();
        }
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            throw new IllegalArgumentException("Invalid seat_ids JSON: " + dbData);
        }

        String inner = normalized.substring(1, normalized.length() - 1).trim();
        if (inner.isEmpty()) {
            return List.of();
        }

        String[] tokens = inner.split(",");
        List<Long> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            values.add(Long.parseLong(token.trim()));
        }
        return values;
    }
}
