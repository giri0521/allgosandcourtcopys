package com.allgos.dms.department.dto;

import com.allgos.dms.department.entity.Department;
import java.util.UUID;

public record DepartmentSummary(UUID id, String name) {

    public static DepartmentSummary from(Department department) {
        return new DepartmentSummary(department.getId(), department.getName());
    }
}
