package org.example.springbootspacegame.order;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the orders queue: API surface, tick processing, and
 * the MOVE/LAND handlers together. Calls {@link TickService#advanceTick()}
 * directly instead of waiting for the scheduler — see CLAUDE.md "Integration
 * test setup" + the test-only {@code game.tick.interval-ms} override.
 */
@IntegrationTest
class ShipOrderFlowIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TickService tickService;

    @Autowired
    private ShipOrderRepository orderRepository;

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
    void moveOrderAdvancesShipChebyshevAndCompletesOnArrival() throws Exception {
        MockHttpSession session = registerAndLogin("sisko", "sisko@enterprise.example", "deep-space-niner");

        // Player spawns at (50, 50); send the ship to (52, 53).
        UUID orderId = postOrder(session, "MOVE", Map.of("x", 52, "y", 53));

        // Tick 1: diagonal step toward target -> (51, 51)
        tickService.advanceTick();
        assertShipAt(session, 51, 51);

        // Tick 2: diagonal again -> (52, 52)
        tickService.advanceTick();
        assertShipAt(session, 52, 52);

        // Tick 3: only y axis left -> (52, 53), arrived, order completes.
        tickService.advanceTick();
        assertShipAt(session, 52, 53);

        // After completion, the order leaves the visible queue.
        mockMvc.perform(get("/api/ship/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void landOnPlanetCompletesInOneTick() throws Exception {
        // Player spawns at (50, 50) — Earth is seeded there in V5, so LAND
        // succeeds immediately without any MOVE first.
        MockHttpSession session = registerAndLogin("janeway", "janeway@enterprise.example", "coffee-black-1");

        UUID orderId = postOrder(session, "LAND", Map.of());

        tickService.advanceTick();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void landOffPlanetCancelsWithReason() throws Exception {
        MockHttpSession session = registerAndLogin("picard", "picard@enterprise.example", "engage-warp-7");

        // MOVE one tile off-spawn (Earth is at 50,50), then LAND on an empty
        // tile — LAND should cancel because there's no planet under the ship.
        postOrder(session, "MOVE", Map.of("x", 51, "y", 50));
        UUID landId = postOrder(session, "LAND", Map.of());

        tickService.advanceTick(); // MOVE completes (51, 50)
        tickService.advanceTick(); // LAND fires, finds no planet, cancels

        ShipOrder land = orderRepository.findById(landId).orElseThrow();
        assertThat(land.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(land.getCompletedAt()).isNotNull();
    }

    @Test
    void invalidMoveParamsReturn400() throws Exception {
        MockHttpSession session = registerAndLogin("worf", "worf@enterprise.example", "klingon-honor1");

        // Missing y
        mockMvc.perform(post("/api/ship/orders").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "MOVE", "params": { "x": 10 } }
                                """))
                .andExpect(status().isBadRequest());

        // Out of range
        mockMvc.perform(post("/api/ship/orders").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "MOVE", "params": { "x": 999, "y": 999 } }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPendingOrderTakesItOutOfTheQueue() throws Exception {
        MockHttpSession session = registerAndLogin("tuvok", "tuvok@enterprise.example", "logical-choice");

        UUID first = postOrder(session, "MOVE", Map.of("x", 60, "y", 60)); // ACTIVE after first tick
        UUID second = postOrder(session, "LAND", Map.of());                // still PENDING

        // Cancel the pending LAND before any tick fires.
        mockMvc.perform(delete("/api/ship/orders/{id}", second).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ship/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.toString()));

        assertThat(orderRepository.findById(second).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void ordersProcessInQueueOrder() throws Exception {
        MockHttpSession session = registerAndLogin("ezri", "ezri@enterprise.example", "joined-trill1");

        // Three-step plan: move to Mars (60,55), land there.
        // Spawn is (50,50); Chebyshev distance to (60,55) is 10 steps.
        postOrder(session, "MOVE", Map.of("x", 60, "y", 55));
        postOrder(session, "LAND", Map.of());

        // 10 ticks for the MOVE.
        for (int i = 0; i < 10; i++) {
            tickService.advanceTick();
        }
        assertShipAt(session, 60, 55);

        // Eleventh tick fires LAND on Mars.
        tickService.advanceTick();

        List<ShipOrder> active = orderRepository
                .findByShipIdAndStatusInOrderByCreatedAtAsc(
                        shipIdFor(session), List.of(OrderStatus.PENDING, OrderStatus.ACTIVE));
        assertThat(active).isEmpty(); // both orders completed
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

    private UUID postOrder(MockHttpSession session, String kind, Map<String, Object> params) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("kind", kind, "params", params));
        MvcResult result = mockMvc.perform(post("/api/ship/orders").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    private void assertShipAt(MockHttpSession session, int x, int y) throws Exception {
        mockMvc.perform(get("/api/ship").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(x))
                .andExpect(jsonPath("$.y").value(y));
    }

    private UUID shipIdFor(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ship").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }
}
