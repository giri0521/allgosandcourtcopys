package com.allgos.dms.department.entity;

import com.allgos.dms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One of the 43 secretariat departments, seeded by V2__seed_departments.sql. */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(unique = true)
    private String code;

    @Column
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
