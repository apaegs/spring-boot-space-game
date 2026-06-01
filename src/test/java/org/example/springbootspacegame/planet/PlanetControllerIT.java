package org.example.springbootspacegame.planet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.example.springbootspacegame.MockMvcHelper.registerAndLogin;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PlanetControllerIT {

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
    void listReturnsSeededPlanets() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "kira", "kira@enterprise.example", "deep-space-nine");

        // V5 seeds 6 planets; we don't pin the exact count (future seeds will
        // grow this list) but Earth at the spawn tile is the load-bearing one.
        mockMvc.perform(get("/api/planets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$[*].name", hasItem("Earth")))
                .andExpect(jsonPath("$[*].name", hasItem("Mars")));
    }

    @Test
    void listRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/planets"))
                .andExpect(status().isUnauthorized());
    }
}
