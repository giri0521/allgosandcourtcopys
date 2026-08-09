package com.allgos.dms.user.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter extends LowercaseEnumConverter<UserStatus> {

    public UserStatusConverter() {
        super(UserStatus.class);
    }
}
