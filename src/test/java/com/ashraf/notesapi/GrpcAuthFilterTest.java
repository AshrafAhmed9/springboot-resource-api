package com.ashraf.notesapi;

// Unit-level coverage of the filter itself, without a Spring context: the
// integration tests exercise it end to end (including the 503 fail-closed
// path), but nothing directly checks that a role string coming back from
// gRPC becomes the right Spring Security ROLE_ authority.
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GrpcAuthFilterTest {

    private final AuthValidationService authValidationService = mock(AuthValidationService.class);
    private final GrpcAuthFilter filter = new GrpcAuthFilter(authValidationService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenSetsUppercaseRoleAuthority() throws Exception {
        when(authValidationService.validate("good-token"))
                .thenReturn(new AuthValidationService.Result.Valid(7L, "a@b.com", "admin"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("7");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(chain).doFilter(request, response);
    }
}
