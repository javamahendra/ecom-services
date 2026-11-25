package com.app.controller;

import com.app.entity.AddressDocument;
import com.app.entity.DispatchRecord;
import com.app.request.AddressRequest;
import com.app.request.DispatchStatusRequest;
import com.app.service.DispatchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class DispatchController {
    private final DispatchService dispatch;
    public DispatchController(DispatchService dispatch) { this.dispatch = dispatch; }

    @GetMapping("/api/addresses")
    public List<AddressDocument> addresses(Authentication auth) { return dispatch.addresses(auth.getName()); }
    @PostMapping("/api/addresses")
    public AddressDocument createAddress(Authentication auth, @Valid @RequestBody AddressRequest request) {
        return dispatch.saveAddress(auth.getName(), null, request, false);
    }
    @PutMapping("/api/addresses/{id}")
    public AddressDocument updateAddress(Authentication auth, @PathVariable String id,
                                         @Valid @RequestBody AddressRequest request) {
        return dispatch.saveAddress(auth.getName(), id, request, isAdmin(auth));
    }
    @DeleteMapping("/api/addresses/{id}")
    public void deleteAddress(Authentication auth, @PathVariable String id) {
        dispatch.deleteAddress(auth.getName(), id, isAdmin(auth));
    }

    @GetMapping("/api/dispatches")
    public Map<String, Object> search(Authentication auth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return dispatch.search(auth.getName(), isStaff(auth), status, search, fromDate, toDate,
                page, size, sortBy, direction);
    }

    @GetMapping("/api/dispatches/count")
    public long count(Authentication auth) {
        return dispatch.count(auth.getName(), isStaff(auth));
    }

    @GetMapping("/api/dispatches/{orderId}")
    public DispatchRecord get(Authentication auth, @PathVariable String orderId) {
        return dispatch.get(orderId, auth.getName(), isStaff(auth));
    }
    @PatchMapping("/api/dispatches/{orderId}/status")
    public DispatchRecord updateStatus(Authentication auth, @PathVariable String orderId,
                                       @Valid @RequestBody DispatchStatusRequest request) {
        if (!isStaff(auth)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Only administrators and managers can update dispatch status");
        }
        return dispatch.updateStatus(orderId, request);
    }
    private boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_admin") || a.getAuthority().equals("ROLE_manager"));
    }
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_admin"));
    }
}
