package com.cityfix.controller;

import com.cityfix.dto.Dtos.*;
import com.cityfix.exception.ApiException;
import com.cityfix.model.Department;
import com.cityfix.repository.DepartmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentRepository departments;

    public DepartmentController(DepartmentRepository departments) {
        this.departments = departments;
    }

    @GetMapping
    public List<DepartmentResponse> all() {
        return departments.findAll().stream().map(d -> new DepartmentResponse(d.getId(), d.getName())).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest req) {
        String name = req.name().trim();
        if (departments.existsByName(name)) throw new ApiException(HttpStatus.CONFLICT, "Department already exists");
        Department d = departments.save(new Department(name));
        return new DepartmentResponse(d.getId(), d.getName());
    }
}
