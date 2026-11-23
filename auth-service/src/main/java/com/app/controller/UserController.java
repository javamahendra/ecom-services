package com.app.controller;

import com.app.request.CreateUserRequest;
import com.app.request.AdminCreateUserRequest;
import com.app.request.UserRoleRequest;
import com.app.request.UserStatusRequest;
import com.app.request.UserRequest;
import com.app.response.AdminUserResponse;
import com.app.response.UserResponse;
import com.app.service.UserService;
import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = users.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @PostMapping("/admin")
    public ResponseEntity<AdminUserResponse> createByAdmin(@Valid @RequestBody AdminCreateUserRequest request) {
        AdminUserResponse user = users.createByAdmin(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @GetMapping
    public List<AdminUserResponse> findAll() {
        return users.findAllWithRoles();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable @NotBlank @Size(max = 255) String id,
                                 Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_admin"));
        if (authentication == null || !authentication.getName().equals(id) && !isAdmin) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Cannot access another user's profile");
        }
        return users.findById(id);
    }

    @PutMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable @NotBlank @Size(max = 255) String id,
                                        @Valid @RequestBody UserRoleRequest request,
                                        Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_admin"));
        if (isAdmin && authentication.getName().equals(id) && !"admin".equals(request.role())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Administrators cannot remove their own admin role");
        }
        return users.updateRole(id, request.role());
    }

    @PutMapping("/{id}/status")
    public AdminUserResponse updateStatus(@PathVariable @NotBlank @Size(max = 255) String id,
                                         @Valid @RequestBody UserStatusRequest request,
                                         Authentication authentication) {
        if (authentication != null && authentication.getName().equals(id) && !request.enabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Administrators cannot disable their own account");
        }
        return users.updateStatus(id, request.enabled());
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable @NotBlank @Size(max = 255) String id,
            @Valid @RequestBody UserRequest request,
            Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_admin"));
        if (authentication == null || !authentication.getName().equals(id) && !isAdmin) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Cannot update another user's profile");
        }
        return users.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank @Size(max = 255) String id,
                                       Authentication authentication) {
        if (authentication != null && authentication.getName().equals(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Administrators cannot delete their own account");
        }
        users.delete(id);
        return ResponseEntity.noContent().build();
    }
}
