package org.example.springbootspacegame.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.example.springbootspacegame.errors.JsonSecurityErrorHandlers;
import org.example.springbootspacegame.observability.MdcFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Persists the SecurityContext into the HTTP session so subsequent requests stay
     * authenticated via the JSESSIONID cookie.
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http
                // SPA-style CSRF: token is exposed in a non-HttpOnly cookie (XSRF-TOKEN)
                // and echoed by the frontend in an X-XSRF-TOKEN header on every
                // state-changing request. /api/auth/register and /api/auth/login are
                // exempted because they're unauthenticated by design — there's no
                // session for an attacker to ride, so CSRF protection is moot, and
                // requiring a token there would force a separate "fetch token first"
                // round-trip into the login flow.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/api/auth/register", "/api/auth/login"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // MdcFilter sits right after SecurityContextHolderFilter so it
                // can see the resolved principal (real userId, not anonymous)
                // while still running before the authorization filter — so
                // 401/403 responses also carry the X-Request-Id header. See
                // MdcFilter's class Javadoc for the full rationale.
                .addFilterAfter(new MdcFilter(), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                        .anyRequest().authenticated()
                )
                // For a REST API, an unauthenticated request should be 401 and a
                // forbidden one should be 403 — both with the stable ApiErrorResponse
                // JSON body, not Spring's default empty body. CSRF rejections are
                // routed through AccessDeniedHandler too; the handler disambiguates
                // them in the response message.
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(JsonSecurityErrorHandlers.authenticationEntryPoint(mapper))
                        .accessDeniedHandler(JsonSecurityErrorHandlers.accessDeniedHandler(mapper)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable()) // we implement /api/auth/logout ourselves
                .build();
    }

    /**
     * Forces the deferred CSRF token to load on every request so the {@code XSRF-TOKEN}
     * cookie is always set before the SPA needs it. Without this, the token is only
     * issued lazily — i.e., on the first state-changing request — which would 403
     * a fresh client that hasn't seen any prior response yet.
     *
     * <p>Lifted from the Spring Security reference's "Single-Page Applications"
     * recipe (servlet/exploits/csrf#csrf-integration-javascript-spa).
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
            if (csrfToken != null) {
                csrfToken.getToken(); // trigger deferred cookie write
            }
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Handles CSRF tokens for SPA clients. Token generation uses the XOR variant
     * (BREACH-defense): the rendered cookie value is a fresh XOR-masked token per
     * request, so a TLS-level compressor can't leak it. Resolution prefers the
     * plain header path: when a request carries an {@code X-XSRF-TOKEN} header
     * (set by the SPA from the cookie), we read it as-is — the cookie value the
     * SPA echoes is already the masked variant and matches the server's
     * expectation. The XOR handler is the fallback for form-encoded clients
     * (which don't apply here but cost nothing to keep).
     *
     * <p>Lifted from the Spring Security reference's "Single-Page Applications"
     * recipe (servlet/exploits/csrf#csrf-integration-javascript-spa).
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                return super.resolveCsrfTokenValue(request, csrfToken);
            }
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
