package org.example.springbootspacegame.errors;

import static org.example.springbootspacegame.MockMvcHelper.registerAndLogin;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end coverage of the {@link GlobalExceptionHandler} JSON shape.
 * Each test exercises one branch and asserts both the status and the
 * stable {@link ApiErrorResponse} fields.
 *
 * <p>Other ITs (Auth/Ship/Order/etc.) only assert HTTP status on error
 * paths — this class is the single place that pins the wire shape, so a
 * future refactor of the handler can't silently change the contract the
 * frontend depends on.
 */
@IntegrationTest
class GlobalExceptionHandlerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // MdcFilter is wired into the security chain in SecurityConfig
        // (.addFilterAfter(new MdcFilter(), SecurityContextHolderFilter.class)),
        // so .apply(springSecurity()) picks it up automatically. No explicit
        // .addFilters() needed.
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // --- 400: validation ----------------------------------------------------

    @Test
    void invalidRegisterBodyReturns400WithFieldDetails() throws Exception {
        // Empty username + invalid email + too-short password → three field
        // errors. We assert at least one specific field's presence to confirm
        // the details map is populated correctly.
        String body = """
                { "username": "", "email": "not-an-email", "password": "x" }
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.username").exists())
                .andExpect(jsonPath("$.errorId").doesNotExist());
    }

    // --- 401: unauthenticated -----------------------------------------------

    @Test
    void unauthenticatedRequestReturns401WithStableShape() throws Exception {
        mockMvc.perform(get("/api/ships"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.errorId").doesNotExist());
    }

    @Test
    void wrongPasswordReturns401WithStableShape() throws Exception {
        // Register first so the account exists.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "scotty",
                                  "email": "scotty@enterprise.example",
                                  "password": "warp-core-stable" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "scotty", "password": "wrong" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    // --- 403: CSRF missing on state-changing -------------------------------

    @Test
    void postWithoutCsrfReturns403WithStableShape() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper,
                "csrf-shape", "csrf-shape@example.com", "password-shape-1");
        mockMvc.perform(post("/api/ships").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("CSRF token missing or invalid"));
    }

    // --- 404: ResponseStatusException passthrough ---------------------------

    @Test
    void crossUser404ReturnsStableShape() throws Exception {
        // Two players, Bob tries to PATCH Alice's ship — service throws
        // ResponseStatusException(NOT_FOUND), which our advice normalizes
        // to the stable shape.
        MockHttpSession alice = registerAndLogin(mockMvc, objectMapper,
                "shape-alice", "shape-alice@example.com", "password-alice-1");
        MockHttpSession bob = registerAndLogin(mockMvc, objectMapper,
                "shape-bob", "shape-bob@example.com", "password-bob-12");
        String aliceShipId = readFirstShipId(alice);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/ships/" + aliceShipId).session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "stolen" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- 500: catch-all -----------------------------------------------------

    @Test
    void unhandledRuntimeReturns500WithOpaqueErrorId() throws Exception {
        // /api/test-errors/runtime throws RuntimeException("boom"). The
        // GlobalExceptionHandler.onUnhandled branch should:
        //  - log the stack server-side (we don't assert on the log here)
        //  - return 500 with a generic message
        //  - include an errorId (UUID) so a player can quote it
        //  - NOT leak the underlying "boom" message into the wire body
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper,
                "shape-runtime", "shape-runtime@example.com", "password-runtime-1");
        mockMvc.perform(get("/api/test-errors/runtime").session(session).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.errorId")
                        .value(matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    // --- request id header --------------------------------------------------

    @Test
    void everyResponseCarriesRequestIdHeader() throws Exception {
        // The MdcFilter sets X-Request-Id on every response, error or not.
        // Verifying on a 401 path (no auth needed) is the simplest happy
        // case to assert against.
        mockMvc.perform(get("/api/ships"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id",
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    // --- helpers ------------------------------------------------------------

    private String readFirstShipId(MockHttpSession session) throws Exception {
        var result = mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get(0).get("id").asText();
    }
}
