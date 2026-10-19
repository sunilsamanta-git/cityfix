package com.cityfix.controller;

import com.cityfix.dto.Dtos.ComplaintResponse;
import com.cityfix.service.ComplaintService;
import org.springframework.web.bind.annotation.*;

/** No login needed: lets anyone follow a complaint using its tracking ID. */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final ComplaintService service;

    public PublicController(ComplaintService service) {
        this.service = service;
    }

    @GetMapping("/track/{trackingId}")
    public ComplaintResponse track(@PathVariable String trackingId) { return service.track(trackingId); }
}
