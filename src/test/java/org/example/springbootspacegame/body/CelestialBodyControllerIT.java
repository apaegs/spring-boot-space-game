package org.example.springbootspacegame.body;

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
class CelestialBodyControllerIT {

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
    void listReturnsSeededBodies() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "kira", "kira@enterprise.example", "deep-space-nine");

        // V9 seeds 40 bodies across the kind taxonomy; we don't pin the exact
        // count (future seeds may grow this) but Earth at the spawn tile is the
        // load-bearing one, and the taxonomy needs to be represented.
        mockMvc.perform(get("/api/bodies").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(40)))
                .andExpect(jsonPath("$[*].name", hasItem("Earth")))
                .andExpect(jsonPath("$[*].name", hasItem("Jupiter")))
                .andExpect(jsonPath("$[*].kind", hasItem("ROCKY_PLANET")))
                .andExpect(jsonPath("$[*].kind", hasItem("GAS_GIANT")))
                .andExpect(jsonPath("$[*].kind", hasItem("ASTEROID")))
                .andExpect(jsonPath("$[*].kind", hasItem("STAR")));
    }

    @Test
    void earthIncludesReservesAndBuyPrices() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "sisko", "sisko@enterprise.example", "captain-of-the-defiant");

        // Earth is seeded as an industrial buyer with reserves of IRON and WATER.
        // Pinning specific entries to verify the nested-list shape works.
        mockMvc.perform(get("/api/bodies").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Earth')].reserves[*].kind", hasItem("IRON")))
                .andExpect(jsonPath("$[?(@.name=='Earth')].buyPrices[*].kind", hasItem("IRON")));
    }

    @Test
    void listRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/bodies"))
                .andExpect(status().isUnauthorized());
    }
}
