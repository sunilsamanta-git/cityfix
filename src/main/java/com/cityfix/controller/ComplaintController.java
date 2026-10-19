package com.cityfix.controller;

import com.cityfix.dto.Dtos.*;
import com.cityfix.model.Status;
import com.cityfix.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {
    private final ComplaintService service;

    public ComplaintController(ComplaintService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintResponse create(@Valid @RequestBody ComplaintRequest req, Authentication auth) {
        return service.create(auth.getName(), req);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CITIZEN')")
    public ComplaintResponse photo(@PathVariable Long id, @RequestParam("file") MultipartFile file, Authentication auth) {
        return service.attachPhoto(id, auth.getName(), file);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CITIZEN')")
    public List<ComplaintResponse> mine(Authentication auth) { return service.mine(auth.getName()); }

    @GetMapping
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public List<ComplaintResponse> list(@RequestParam(required = false) Status status,
                                        @RequestParam(required = false) Long departmentId,
                                        Authentication auth) {
        return service.list(auth.getName(), status, departmentId);
    }

    @GetMapping("/{id}")
    public ComplaintResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName());
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req, Authentication auth) {
        return service.assign(id, req.departmentId(), auth.getName());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
    public ComplaintResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req, Authentication auth) {
        return service.updateStatus(id, req, auth.getName());
    }
}
