package org.example.springbootspacegame.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Stable JSON shape returned by every non-2xx response from this API.
 *
 * <p>The client ({@code frontend/src/api/client.ts}) parses this directly into
 * its {@code ApiError} so error-rendering logic doesn't have to branch on
 * "which error format did Spring use this time".
 *
 * <p>{@link #details} is included only for validation failures (where the
 * caller needs per-field messages). Suppressed via
 * {@link JsonInclude.Include#NON_NULL} on everything else so the wire shape
 * stays minimal.
 *
 * <p>{@link #errorId} is included on internal 5xx responses so a player who
 * reports "I got an error" can copy a short id that we then grep the logs
 * for. It's a UUID, opaque, and the only externally-visible identifier of
 * the failed request — the actual stack trace stays server-side.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String message,
        Map<String, String> details,
        String errorId
) {
    public static ApiErrorResponse of(int status, String message) {
        return new ApiErrorResponse(status, message, null, null);
    }

    public static ApiErrorResponse withDetails(int status, String message, Map<String, String> details) {
        return new ApiErrorResponse(status, message, details, null);
    }

    public static ApiErrorResponse withErrorId(int status, String message, String errorId) {
        return new ApiErrorResponse(status, message, null, errorId);
    }
}
