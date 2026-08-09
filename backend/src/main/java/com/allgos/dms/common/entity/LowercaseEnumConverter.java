package com.allgos.dms.common.entity;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;

/**
 * The schema stores enum columns in lowercase ('admin', 'pending', 'court_order') so that SQL
 * reports and the CHECK constraints read naturally. Java enums are uppercase, so every enum-backed
 * column extends this converter rather than using {@code @Enumerated}, which would write names that
 * violate those constraints.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowercaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(type, dbData.toUpperCase(Locale.ROOT));
    }
}
