package com.allgos.dms.user.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter extends LowercaseEnumConverter<UserRole> {

    public UserRoleConverter() {
        super(UserRole.class);
    }
}
