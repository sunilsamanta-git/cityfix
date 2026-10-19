package com.cityfix.controller;

import com.cityfix.dto.Dtos.*;
import com.cityfix.service.AuthService;
import com.cityfix.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final ComplaintService complaints;
    private final AuthService auth;

    public AdminController(ComplaintService complaints, AuthService auth) {
        this.complaints = complaints;
        this.auth = auth;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() { return complaints.stats(); }

    @PostMapping("/officers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createOfficer(@Valid @RequestBody OfficerRequest req) {
        AuthResponse created = auth.createOfficer(req);
        return Map.of("name", created.name(), "email", created.email(), "department", created.department());
    }
}
