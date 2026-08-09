package com.allgos.dms.department.repository;

import com.allgos.dms.department.entity.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Department> findByActiveTrueOrderByNameAsc();
}
