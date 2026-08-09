package com.allgos.dms.auth.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RegistrationStatusConverter extends LowercaseEnumConverter<RegistrationStatus> {

    public RegistrationStatusConverter() {
        super(RegistrationStatus.class);
    }
}
