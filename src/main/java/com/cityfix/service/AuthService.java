package com.cityfix.service;

import com.cityfix.dto.Dtos.*;
import com.cityfix.exception.ApiException;
import com.cityfix.model.*;
import com.cityfix.repository.*;
import com.cityfix.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, DepartmentRepository departments, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.departments = departments;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Public sign-up always creates a CITIZEN. Officers are created by an admin. */
    public AuthResponse register(RegisterRequest req) {
        return create(req.name(), req.email(), req.password(), Role.CITIZEN, null);
    }

    public AuthResponse createOfficer(OfficerRequest req) {
        Department dept = departments.findById(req.departmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        return create(req.name(), req.email(), req.password(), Role.OFFICER, dept);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        return toResponse(user);
    }

    private AuthResponse create(String name, String email, String password, Role role, Department dept) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User u = new User();
        u.setName(name.trim());
        u.setEmail(normalized);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setDepartment(dept);
        return toResponse(users.save(u));
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwt.generate(u), u.getName(), u.getEmail(), u.getRole(),
                u.getDepartment() == null ? null : u.getDepartment().getName());
    }
}
