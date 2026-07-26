package com.ashraf.notesapi;

// Runs once per request: no Bearer token, pass through unauthenticated
// (Spring Security's own rule turns that into 403 for protected routes).
// A token that's valid populates the SecurityContext; one that's invalid
// or the auth service being unreachable both stop the request here.
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GrpcAuthFilter extends OncePerRequestFilter {

    private final AuthValidationService authValidationService;

    public GrpcAuthFilter(AuthValidationService authValidationService) {
        this.authValidationService = authValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            AuthValidationService.Result result = authValidationService.validate(token);

            if (result instanceof AuthValidationService.Result.Valid valid) {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (valid.role() != null && !valid.role().isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + valid.role().toUpperCase()));
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        String.valueOf(valid.userId()), null, authorities
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else if (result instanceof AuthValidationService.Result.Invalid invalid) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, invalid.reason());
                return;
            } else {
                response.setHeader("Retry-After", "5");
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Auth service unavailable");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
