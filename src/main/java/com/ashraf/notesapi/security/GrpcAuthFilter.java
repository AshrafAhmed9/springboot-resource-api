package com.ashraf.notesapi.security;

import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenRequest;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenResponse;
import io.grpc.StatusRuntimeException;
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

    private final AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;

    public GrpcAuthFilter(AuthServiceGrpc.AuthServiceBlockingStub authServiceStub) {
        this.authServiceStub = authServiceStub;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                ValidateTokenResponse tokenResponse = authServiceStub.validateToken(
                        ValidateTokenRequest.newBuilder().setToken(token).build()
                );

                if (tokenResponse.getValid()) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (!tokenResponse.getRole().isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + tokenResponse.getRole().toUpperCase()));
                    }

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            String.valueOf(tokenResponse.getUserId()),
                            null,
                            authorities
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, tokenResponse.getError());
                    return;
                }
            } catch (StatusRuntimeException e) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Auth service unavailable");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
