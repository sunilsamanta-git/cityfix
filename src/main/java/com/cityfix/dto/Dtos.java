package com.cityfix.dto;

import com.cityfix.model.*;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

/** All request/response shapes in one place. Records keep them short and immutable. */
public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 60) String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record OfficerRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 60) String password,
            @NotNull Long departmentId) {}

    public record AuthResponse(String token, String name, String email, Role role, String department) {}

    public record ComplaintRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 1000) String description,
            @NotNull Category category,
            @NotBlank @Size(max = 200) String location) {}

    public record AssignRequest(@NotNull Long departmentId) {}

    public record StatusUpdateRequest(@NotNull Status status, @Size(max = 500) String remarks) {}

    public record DepartmentRequest(@NotBlank @Size(max = 80) String name) {}

    public record DepartmentResponse(Long id, String name) {}

    public record HistoryItem(Status from, Status to, String remarks, String changedBy, LocalDateTime changedAt) {}

    public record ComplaintResponse(
            Long id, String trackingId, String title, String description,
            Category category, Status status, String location, String imageUrl,
            String department, String reportedBy,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            List<Status> nextStatuses, List<HistoryItem> history) {}
}
