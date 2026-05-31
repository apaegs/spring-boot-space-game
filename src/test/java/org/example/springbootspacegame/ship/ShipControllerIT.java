package org.example.springbootspacegame.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.example.springbootspacegame.auth.LoginRequest;
import org.example.springbootspacegame.auth.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ShipControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void newlyRegisteredUserGetsAShipAtSpawn() throws Exception {
        String username = "spock";
        MockHttpSession session = registerAndLogin(username, "spock@enterprise.example", "live-long-prosper");

        mockMvc.perform(get("/api/ship").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value(username + "'s ship"))
                .andExpect(jsonPath("$.x").value(50))
                .andExpect(jsonPath("$.y").value(50))
                .andExpect(jsonPath("$.destinationX").doesNotExist())
                .andExpect(jsonPath("$.destinationY").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void twoUsersGetTwoDifferentShips() throws Exception {
        MockHttpSession alice = registerAndLogin("alice", "alice@example.com", "password-alice-1");
        MockHttpSession bob = registerAndLogin("bob", "bob@example.com", "password-bob-12");

        String aliceShipId = readShipId(alice);
        String bobShipId = readShipId(bob);

        // Different rows: different IDs, both names follow the spawn convention.
        org.junit.jupiter.api.Assertions.assertNotEquals(aliceShipId, bobShipId);
    }

    @Test
    void shipEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/ship"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shipPositionIsWithinGrid() throws Exception {
        MockHttpSession session = registerAndLogin("uhura", "uhura@enterprise.example", "frequency-open-1");

        mockMvc.perform(get("/api/ship").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.x", lessThan(100)))
                .andExpect(jsonPath("$.y", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.y", lessThan(100)));
    }

    // --- helpers ---

    private MockHttpSession registerAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isNoContent())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private String readShipId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ship").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", not("")))
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }
}
