package com.allgos.dms.phonebook.entity;

import com.allgos.dms.common.entity.BaseEntity;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One number in the office phonebook.
 *
 * <p>A contact is a person to ring, not an account: nothing here requires them to have a login, and
 * most of them will not — the point of the book is the office down the road.
 *
 * <p>Which fields are filled depends on {@link #kind}, and the database enforces that pairing (see
 * V6). A department contact names a department and a designation; a taluk contact names a taluk and
 * whether they are its tahsildar or one of its group members.
 */
@Entity
@Table(name = "phonebook_contacts")
@Getter
@Setter
@NoArgsConstructor
public class PhonebookContact extends BaseEntity {

    @Column(nullable = false)
    private PhonebookKind kind;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Free text, because a designation is whatever the office calls it. */
    @Column
    private String designation;

    /** Taluk contacts only; null for a department entry. */
    @Column
    private PhonebookRole role;

    /**
     * Stored as written rather than normalised to ten digits: this book holds office landlines with
     * STD codes and extensions as well as mobiles, and stripping them to fit the login format would
     * lose the very numbers a phonebook exists for.
     */
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "alternate_phone")
    private String alternatePhone;

    @Column
    private String email;

    /**
     * Where they sit. Free text and optional: a state department is rung across the state, and the
     * same post in two districts is two different people to ask for.
     */
    @Column
    private String district;

    /** Department contacts only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Taluk contacts only. The name as typed; taluks are not a table of their own. */
    @Column
    private String taluk;

    /** Who added it, so a wrong number can be asked about rather than only corrected. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
