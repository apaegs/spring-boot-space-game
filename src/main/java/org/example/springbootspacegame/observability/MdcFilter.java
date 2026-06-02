package org.example.springbootspacegame.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.example.springbootspacegame.auth.AuthenticatedUser;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-request MDC population: stamps every log line emitted during a request
 * with {@code requestId} (UUID generated here) and {@code userId} (resolved
 * from the {@link SecurityContextHolder}, or {@code "anonymous"} when no
 * session). The same UUID goes out as an {@code X-Request-Id} response
 * header so a player can quote it in a bug report and we can grep the logs
 * straight to their request.
 *
 * <p>Placement matters. The filter is wired into the Spring Security chain
 * via {@code .addFilterAfter(new MdcFilter(),
 * SecurityContextHolderFilter.class)} in {@code SecurityConfig}, so:
 *
 * <ul>
 *   <li>It runs <i>after</i> the security context has been loaded from the
 *       session — so {@link #resolveUserId()} sees the real authenticated
 *       principal, not anonymous.</li>
 *   <li>It runs <i>before</i> the authorization filter rejects unauthenticated
 *       requests — so {@code X-Request-Id} is on the response even for 401s
 *       and 403s. That's important: those are the response codes a player
 *       most often quotes in a bug report.</li>
 * </ul>
 *
 * <p>NOT a {@code @Component} on purpose — Spring Boot auto-registers
 * {@code @Component} servlet filters at {@code LOWEST_PRECEDENCE}, which
 * would put it after Spring Security in the chain (breaking the second
 * bullet above). Instantiated explicitly in {@code SecurityConfig} instead.
 *
 * <p>The MDC is thread-local; we clear it in {@code finally} to avoid
 * leaking values to whichever request the thread services next out of the
 * pool.
 */
public class MdcFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "requestId";
    static final String USER_ID = "userId";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ANONYMOUS = "anonymous";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String userId = resolveUserId();
        MDC.put(REQUEST_ID, requestId);
        MDC.put(USER_ID, userId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Thread-local cleanup — without this, a recycled thread carries
            // the previous request's userId into the next request's logs.
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }

    /**
     * The authenticated user's UUID as a string, or {@code "anonymous"} if
     * the security context has no authentication or one with a principal
     * we don't recognize (form login attempts before our manual auth runs,
     * background filter passes, etc.). The "anonymous" literal — not null
     * / missing — keeps the MDC key consistently present so log queries
     * can {@code GROUP BY userId} without losing the no-auth rows.
     */
    private static String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return ANONYMOUS;
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser u) return u.getUserId().toString();
        return ANONYMOUS;
    }
}
