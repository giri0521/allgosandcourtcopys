package com.allgos.dms.department.controller;

import com.allgos.dms.department.dto.DepartmentSummary;
import com.allgos.dms.department.repository.DepartmentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The department list needed by the registration form, before the applicant has an account.
 *
 * <p>Deliberately under /auth so it falls inside the small set of public routes, and deliberately
 * limited to id and name — nothing here reveals members, folders or documents.
 */
@RestController
@RequestMapping("/api/v1/auth/departments")
public class PublicDepartmentController {

    private final DepartmentRepository departmentRepository;

    public PublicDepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public List<DepartmentSummary> list() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(DepartmentSummary::from)
                .toList();
    }
}
