package com.ticketrush.admin;

import com.ticketrush.admin.dto.AdminHealthResponse;
import com.ticketrush.admin.dto.AdminUserSuspensionResponse;
import com.ticketrush.event.dto.EventResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/events/{id}/suspend")
    public EventResponse suspendEvent(@PathVariable Long id) {
        return adminService.suspendEvent(id);
    }

    @PostMapping("/users/{id}/suspend")
    public AdminUserSuspensionResponse suspendUser(@PathVariable Long id) {
        return adminService.suspendUser(id);
    }

    @GetMapping("/health")
    public AdminHealthResponse health() {
        return adminService.checkHealth();
    }
}
