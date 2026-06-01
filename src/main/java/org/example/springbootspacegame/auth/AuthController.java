package org.example.springbootspacegame.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP entry point for the four auth endpoints: register, login, logout, /me.
 * The controller is intentionally thin — business logic (user lookup, password
 * hashing, the DTO shape) lives in {@link AuthService}; this class is the
 * transport layer per the CLAUDE.md convention.
 *
 * <p>One thing that <i>isn't</i> in AuthService and lives here on purpose:
 * the session + SecurityContext handling around {@link #login}. Because we
 * authenticate manually via {@link AuthenticationManager} (we don't use
 * Spring's {@code UsernamePasswordAuthenticationFilter}), the work that
 * filter would normally do for us has to happen here explicitly:
 * <ul>
 *   <li>Session-fixation protection by rotating the session id if one
 *       already existed pre-auth. Mirrors
 *       {@code ChangeSessionIdAuthenticationStrategy}.</li>
 *   <li>Persisting the {@link SecurityContext} into the configured
 *       {@link SecurityContextRepository} so subsequent requests can
 *       resolve the authenticated principal from {@code JSESSIONID}
 *       without re-authenticating each time.</li>
 * </ul>
 *
 * <p>{@link #login} catches {@link BadCredentialsException} explicitly and
 * remaps it to a 401 — the default Spring response would be 500-ish via the
 * generic exception path, which leaks implementation detail and confuses
 * clients about whether to retry.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Authentication authRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password());

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(authRequest);
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // Session fixation protection: if the client already had a session id (e.g. an
        // anonymous one set by a prior request), rotate it now that we've authenticated.
        // Mirrors what Spring Security's default ChangeSessionIdAuthenticationStrategy does
        // inside the standard form-login filter chain — we have to do it ourselves here
        // because we authenticate manually rather than via UsernamePasswordAuthenticationFilter.
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.getCurrentUser();
    }
}
