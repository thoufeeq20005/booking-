package com.bookeasy.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleCodeConverter implements AttributeConverter<RoleCode, Short> {

    @Override
    public Short convertToDatabaseColumn(RoleCode role) {
        return role == null ? null : role.id();
    }

    @Override
    public RoleCode convertToEntityAttribute(Short id) {
        return id == null ? null : RoleCode.fromId(id);
    }
}
