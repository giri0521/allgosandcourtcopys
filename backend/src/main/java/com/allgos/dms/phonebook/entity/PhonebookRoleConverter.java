package com.allgos.dms.phonebook.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PhonebookRoleConverter extends LowercaseEnumConverter<PhonebookRole> {

    public PhonebookRoleConverter() {
        super(PhonebookRole.class);
    }
}
