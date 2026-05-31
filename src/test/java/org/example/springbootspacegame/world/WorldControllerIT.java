package org.example.springbootspacegame.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.example.springbootspacegame.auth.LoginRequest;
import org.example.springbootspacegame.auth.RegisterRequest;
import org.example.springbootspacegame.tick.TickService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class WorldControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TickService tickService;

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
    void worldEndpointReturnsCurrentTickAndGrid() throws Exception {
        MockHttpSession session = registerAndLogin("sulu", "sulu@enterprise.example", "warp-factor-nine");

        // Fire a tick so the response shows current_tick > 0.
        tickService.advanceTick();

        mockMvc.perform(get("/api/world").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTick").value(1))
                .andExpect(jsonPath("$.gridWidth").value(100))
                .andExpect(jsonPath("$.gridHeight").value(100))
                .andExpect(jsonPath("$.lastTickAt").isNotEmpty());
    }

    @Test
    void worldEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/world"))
                .andExpect(status().isUnauthorized());
    }

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
}
