package com.cruise.auth.controller;

import com.cruise.auth.dto.VerifyTokenRequest;
import com.cruise.common.dto.ApiResponse;
import com.cruise.common.enums.Role;
import com.cruise.common.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AccessControlController {
    private final AccessTokenVerifier verifier;
    public AccessControlController(AccessTokenVerifier verifier) { this.verifier = verifier; }

    /** The token itself is proof. Invalid tokens return no identity and no detailed claims. */
    @PostMapping("/access-context")
    public ApiResponse<VerifiedCaller> accessContext(@RequestBody VerifyTokenRequest request) {
        String token = request.getToken();
        if (token == null || token.isBlank() || token.length() > 8192) return ApiResponse.ok(null);
        return ApiResponse.ok(verifier.verify(token).orElse(null));
    }

    @GetMapping("/roles")
    public ApiResponse<Map<Role, Set<Permission>>> roles() {
        Map<Role, Set<Permission>> grants = new LinkedHashMap<>();
        Arrays.stream(Role.values()).forEach(role -> grants.put(role, RolePermissions.permissions(role)));
        return ApiResponse.ok(grants);
    }

    @GetMapping("/permissions")
    public ApiResponse<Set<Permission>> permissions(HttpServletRequest request) {
        var caller = (VerifiedCaller) request.getAttribute(VerifiedCaller.ATTRIBUTE);
        return ApiResponse.ok(RolePermissions.permissions(caller.role()));
    }
}
