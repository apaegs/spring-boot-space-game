package org.example.springbootspacegame;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.auth.LoginRequest;
import org.example.springbootspacegame.auth.RegisterRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helper for integration tests. Provides static utility methods that
 * would otherwise be duplicated across every {@code *IT} class.
 */
public final class MockMvcHelper {

    private MockMvcHelper() {
        // utility class
    }

    /**
     * Registers a new user via {@code POST /api/auth/register} and immediately
     * logs them in via {@code POST /api/auth/login}, returning the resulting
     * {@link MockHttpSession} so the caller can make authenticated requests.
     *
     * @param mockMvc        the {@link MockMvc} instance from the calling test
     * @param objectMapper   the {@link ObjectMapper} instance from the calling test
     * @param username       the username to register
     * @param email          the email address to register
     * @param password       the password to register and then log in with
     * @return an authenticated {@link MockHttpSession}
     */
    public static MockHttpSession registerAndLogin(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String username,
            String email,
            String password) throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        if (session == null) {
            throw new AssertionError("Login did not create an authenticated session for user '" + username + "'");
        }
        return session;
    }
}
