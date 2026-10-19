package com.cityfix.service;

import com.cityfix.dto.Dtos.*;
import com.cityfix.exception.ApiException;
import com.cityfix.model.*;
import com.cityfix.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ComplaintService {
    private final ComplaintRepository complaints;
    private final StatusHistoryRepository histories;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final FileStorageService storage;

    public ComplaintService(ComplaintRepository complaints, StatusHistoryRepository histories, UserRepository users, DepartmentRepository departments, FileStorageService storage) {
        this.complaints = complaints;
        this.histories = histories;
        this.users = users;
        this.departments = departments;
        this.storage = storage;
    }

    // ---------------------------------------------------------------- citizen

    /** Creates a complaint and, when the category has a default department, routes it automatically. */
    @Transactional
    public ComplaintResponse create(String email, ComplaintRequest req) {
        User citizen = userOf(email);

        Complaint c = new Complaint();
        c.setTrackingId("CF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setTitle(req.title().trim());
        c.setDescription(req.description().trim());
        c.setCategory(req.category());
        c.setLocation(req.location().trim());
        c.setReportedBy(citizen);
        c.setStatus(Status.REPORTED);
        complaints.save(c);
        log(c, null, Status.REPORTED, "Complaint registered", citizen.getName());

        String defaultDept = req.category().getDefaultDepartment();
        if (defaultDept != null) {
            departments.findByName(defaultDept).ifPresent(d -> {
                c.setDepartment(d);
                c.setStatus(Status.ASSIGNED);
                log(c, Status.REPORTED, Status.ASSIGNED, "Automatically sent to " + d.getName(), "System");
            });
        }
        return toDto(c, true, true);
    }

    @Transactional
    public ComplaintResponse attachPhoto(Long id, String email, MultipartFile file) {
        User citizen = userOf(email);
        Complaint c = load(id);
        if (!c.getReportedBy().getId().equals(citizen.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only add photos to your own complaints");
        }
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No photo received");
        c.setImageUrl(storage.store(file));
        return toDto(c, true, true);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> mine(String email) {
        return complaints.findByReportedByOrderByCreatedAtDesc(userOf(email))
                .stream().map(c -> toDto(c, false, true)).toList();
    }

    // ---------------------------------------------------------------- staff

    /** Officers only ever see their own department. Admins can filter by any department. */
    @Transactional(readOnly = true)
    public List<ComplaintResponse> list(String email, Status status, Long departmentId) {
        User user = userOf(email);
        Long deptId = user.getRole() == Role.OFFICER
                ? (user.getDepartment() == null ? -1L : user.getDepartment().getId())
                : departmentId;

        Specification<Complaint> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        if (deptId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("department").get("id"), deptId));

        return complaints.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(c -> toDto(c, false, true)).toList();
    }

    @Transactional
    public ComplaintResponse assign(Long id, Long departmentId, String email) {
        Complaint c = load(id);
        if (c.getStatus() != Status.REPORTED && c.getStatus() != Status.ASSIGNED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Work has already started, it can no longer be re-assigned");
        }
        Department dept = departments.findById(departmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        Status before = c.getStatus();
        c.setDepartment(dept);
        c.setStatus(Status.ASSIGNED);
        log(c, before, Status.ASSIGNED,
                before == Status.REPORTED ? "Assigned to " + dept.getName() : "Re-assigned to " + dept.getName(),
                userOf(email).getName());
        return toDto(c, true, true);
    }

    @Transactional
    public ComplaintResponse updateStatus(Long id, StatusUpdateRequest req, String email) {
        User user = userOf(email);
        Complaint c = load(id);
        checkAccess(c, user);

        if (req.status() == Status.ASSIGNED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use the assign action to send a complaint to a department");
        }
        if (!c.getStatus().canMoveTo(req.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "A complaint cannot move from " + c.getStatus() + " to " + req.status());
        }
        if ((req.status() == Status.REJECTED || req.status() == Status.RESOLVED)
                && (req.remarks() == null || req.remarks().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please add a short note for the citizen");
        }

        Status before = c.getStatus();
        c.setStatus(req.status());
        if (req.status() == Status.RESOLVED) c.setResolvedAt(LocalDateTime.now());
        log(c, before, req.status(), req.remarks(), user.getName());
        return toDto(c, true, true);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(Long id, String email) {
        Complaint c = load(id);
        checkAccess(c, userOf(email));
        return toDto(c, true, true);
    }

    // ---------------------------------------------------------------- public + stats

    /** Anyone with a tracking ID can see progress. The reporter's name is hidden. */
    @Transactional(readOnly = true)
    public ComplaintResponse track(String trackingId) {
        Complaint c = complaints.findByTrackingId(trackingId.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No complaint found with that tracking ID"));
        return toDto(c, true, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Status s : Status.values()) byStatus.put(s.name(), complaints.countByStatus(s));

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Category cat : Category.values()) byCategory.put(cat.name(), 0L);
        for (Object[] row : complaints.countByCategory()) byCategory.put(((Category) row[0]).name(), (Long) row[1]);

        double avgHours = complaints.findByStatus(Status.RESOLVED).stream()
                .filter(c -> c.getResolvedAt() != null)
                .mapToLong(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes())
                .average().orElse(0) / 60.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", complaints.count());
        out.put("byStatus", byStatus);
        out.put("byCategory", byCategory);
        out.put("avgResolutionHours", Math.round(avgHours * 10) / 10.0);
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private User userOf(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private Complaint load(Long id) {
        return complaints.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
    }

    /** Who may see or change a complaint: admin = all, officer = own department, citizen = own complaints. */
    private void checkAccess(Complaint c, User u) {
        boolean allowed = switch (u.getRole()) {
            case ADMIN -> true;
            case CITIZEN -> c.getReportedBy().getId().equals(u.getId());
            case OFFICER -> c.getDepartment() != null && u.getDepartment() != null
                    && c.getDepartment().getId().equals(u.getDepartment().getId());
        };
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this complaint");
    }

    private void log(Complaint c, Status from, Status to, String remarks, String by) {
        StatusHistory h = new StatusHistory();
        h.setComplaint(c);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setRemarks(remarks);
        h.setChangedBy(by);
        histories.save(h);
    }

    private ComplaintResponse toDto(Complaint c, boolean withHistory, boolean showReporter) {
        List<HistoryItem> history = withHistory
                ? histories.findByComplaintOrderByIdAsc(c).stream()
                    .map(h -> new HistoryItem(h.getFromStatus(), h.getToStatus(), h.getRemarks(), h.getChangedBy(), h.getChangedAt()))
                    .toList()
                : List.of();
        List<Status> next = c.getStatus().next().stream().filter(s -> s != Status.ASSIGNED).toList();
        return new ComplaintResponse(c.getId(), c.getTrackingId(), c.getTitle(), c.getDescription(),
                c.getCategory(), c.getStatus(), c.getLocation(), c.getImageUrl(),
                c.getDepartment() == null ? null : c.getDepartment().getName(),
                showReporter ? c.getReportedBy().getName() : null,
                c.getCreatedAt(), c.getUpdatedAt(), next, history);
    }
}
