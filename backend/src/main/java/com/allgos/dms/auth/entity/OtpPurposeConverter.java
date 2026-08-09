package com.allgos.dms.auth.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OtpPurposeConverter extends LowercaseEnumConverter<OtpPurpose> {

    public OtpPurposeConverter() {
        super(OtpPurpose.class);
    }
}
