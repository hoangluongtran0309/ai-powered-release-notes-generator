package com.hoangluongtran0309.releaseflow.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
final class StatusController {

    @GetMapping
    StatusResponse status() {
        return new StatusResponse("ReleaseFlow", "UP");
    }

    record StatusResponse(String application, String status) {
    }
}
