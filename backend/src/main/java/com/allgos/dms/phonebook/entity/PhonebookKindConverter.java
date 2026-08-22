package com.allgos.dms.phonebook.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PhonebookKindConverter extends LowercaseEnumConverter<PhonebookKind> {

    public PhonebookKindConverter() {
        super(PhonebookKind.class);
    }
}
