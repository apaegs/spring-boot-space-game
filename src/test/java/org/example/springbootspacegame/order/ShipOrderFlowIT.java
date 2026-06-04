package org.example.springbootspacegame.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.example.springbootspacegame.tick.TickService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.example.springbootspacegame.MockMvcHelper.registerAndLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the orders queue: API surface, tick processing, and
 * the MOVE handler. Calls {@link TickService#advanceTick()} directly instead
 * of waiting for the scheduler — see CLAUDE.md "Integration test setup" + the
 * test-only {@code game.tick.interval-ms} override.
 *
 * <p>Resource-handler coverage (EXTRACT / SELL, status derivation) lives in
 * {@link ResourceGameplayIT}.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
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
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "sisko", "sisko@enterprise.example", "deep-space-niner");
        UUID shipId = firstShipIdFor(session);

        // Player spawns at (51, 50); send the ship to (53, 53).
        UUID orderId = postOrder(session, shipId, "MOVE", Map.of("x", 53, "y", 53));

        // Tick 1: diagonal step toward target -> (52, 51)
        tickService.advanceTick();
        assertShipAt(session, shipId, 52, 51);

        // Tick 2: diagonal again -> (53, 52)
        tickService.advanceTick();
        assertShipAt(session, shipId, 53, 52);

        // Tick 3: only y axis left -> (53, 53), arrived, order completes.
        tickService.advanceTick();
        assertShipAt(session, shipId, 53, 53);

        // After completion, the order leaves the visible queue.
        mockMvc.perform(get("/api/ships/{shipId}/orders", shipId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void invalidMoveParamsReturn400() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "worf", "worf@enterprise.example", "klingon-honor1");
        UUID shipId = firstShipIdFor(session);

        // Missing y
        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "MOVE", "params": { "x": 10 } }
                                """))
                .andExpect(status().isBadRequest());

        // Out of range
        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "MOVE", "params": { "x": 999, "y": 999 } }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPendingOrderTakesItOutOfTheQueue() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "tuvok", "tuvok@enterprise.example", "logical-choice");
        UUID shipId = firstShipIdFor(session);

        UUID first = postOrder(session, shipId, "MOVE", Map.of("x", 60, "y", 60));  // ACTIVE after first tick
        UUID second = postOrder(session, shipId, "MOVE", Map.of("x", 70, "y", 70)); // still PENDING

        // Cancel the pending second MOVE before any tick fires.
        mockMvc.perform(delete("/api/ships/{shipId}/orders/{orderId}", shipId, second).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ships/{shipId}/orders", shipId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.toString()));

        assertThat(orderRepository.findById(second).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void ordersProcessInQueueOrder() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "ezri", "ezri@enterprise.example", "joined-trill1");
        UUID shipId = firstShipIdFor(session);

        // Two-step plan. Both paths are body-free so the per-tile collision
        // check (#88) doesn't cancel them mid-flight:
        //   spawn (51,50) -> (51,53): 3 ticks straight south.
        //   (51,53)       -> (51,56): 3 ticks further south.
        postOrder(session, shipId, "MOVE", Map.of("x", 51, "y", 53));
        postOrder(session, shipId, "MOVE", Map.of("x", 51, "y", 56));

        for (int i = 0; i < 6; i++) {
            tickService.advanceTick();
        }

        assertShipAt(session, shipId, 51, 56);

        List<ShipOrder> active = orderRepository
                .findByShipIdAndStatusInOrderByCreatedAtAsc(
                        shipId, List.of(OrderStatus.PENDING, OrderStatus.ACTIVE));
        assertThat(active).isEmpty();
    }

    @Test
    void moveToOccupiedTileReturns400() throws Exception {
        // Spawn-spiral puts alice at (51,50) and bob at (50,49). Bob tries to
        // MOVE to (51,50) — alice's tile — and the queue-time check rejects.
        MockHttpSession alice = registerAndLogin(mockMvc, objectMapper, "alice88", "alice88@example.com", "alice-pass-188");
        MockHttpSession bob = registerAndLogin(mockMvc, objectMapper, "bob88", "bob88@example.com", "bob-pass-1888");
        UUID bobShipId = firstShipIdFor(bob);

        // sanity: alice really is at (51,50)
        firstShipIdFor(alice);

        mockMvc.perform(post("/api/ships/{shipId}/orders", bobShipId).session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("kind", "MOVE", "params", Map.of("x", 51, "y", 50)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveToBodyTileReturns400() throws Exception {
        // Earth is seeded at (50,50). A ship can never land on a body's tile;
        // the queue-time check rejects MOVE to (50,50).
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "obrien", "obrien@example.com", "transporter-188");
        UUID shipId = firstShipIdFor(session);

        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("kind", "MOVE", "params", Map.of("x", 50, "y", 50)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveBlockedMidPathCancels(CapturedOutput output) throws Exception {
        // Alice spawns at (51,50). Bob spawns at (50,49) via spawn-spiral.
        // Bob queues a long MOVE southeast to (60,60). The Chebyshev path
        // from (50,49) heads dx=+1, dy=+1 — the very first step would land
        // on (51,50), which alice occupies. The handler cancels the order
        // and surfaces the blocking coordinates in the reason.
        MockHttpSession alice = registerAndLogin(mockMvc, objectMapper, "alice88b", "alice88b@example.com", "alice-pass-188b");
        MockHttpSession bob = registerAndLogin(mockMvc, objectMapper, "bob88b", "bob88b@example.com", "bob-pass-1888b");
        UUID bobShipId = firstShipIdFor(bob);

        UUID moveId = postOrder(bob, bobShipId, "MOVE", Map.of("x", 60, "y", 60));
        tickService.advanceTick();

        ShipOrder cancelled = orderRepository.findById(moveId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // Bob hasn't moved.
        assertShipAt(bob, bobShipId, 50, 49);

        // The cancellation reason names the blocking tile so the player can
        // see *why* their MOVE failed. The reason isn't persisted on the order
        // row in v1 — it's only logged — so assert the contract via the log.
        // (Persisting the reason would belong with a UI surface for it; track
        // separately if/when that's wanted.)
        assertThat(output.getOut()).contains("blocked at (51, 50)");

        // Sanity: alice didn't move either.
        firstShipIdFor(alice);
    }

    @Test
    void cannotPokeOtherPlayersShipsOrOrders() throws Exception {
        // Two players. Bob shouldn't be able to read, queue, or cancel against
        // Alice's ship — the ownership check should 404 (not 403, to avoid
        // confirming Alice's ship exists).
        MockHttpSession alice = registerAndLogin(mockMvc, objectMapper, "alice", "alice@example.com", "password-alice-1");
        MockHttpSession bob = registerAndLogin(mockMvc, objectMapper, "bob", "bob@example.com", "password-bob-12");
        UUID aliceShipId = firstShipIdFor(alice);
        UUID aliceOrderId = postOrder(alice, aliceShipId, "MOVE", Map.of("x", 70, "y", 70));

        mockMvc.perform(get("/api/ships/{shipId}/orders", aliceShipId).session(bob).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/ships/{shipId}/orders", aliceShipId).session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "MOVE", "params": { "x": 70, "y": 70 } }
                                """))
                .andExpect(status().isNotFound());

        // Cancel path too — ownership-404 should be uniform across verbs so
        // Bob can't probe the existence of Alice's specific order id.
        mockMvc.perform(delete("/api/ships/{shipId}/orders/{orderId}", aliceShipId, aliceOrderId)
                        .session(bob).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private UUID firstShipIdFor(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get(0).get("id").asText());
    }

    private UUID postOrder(MockHttpSession session, UUID shipId, String kind, Map<String, Object> params)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("kind", kind, "params", params));
        MvcResult result = mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    private void assertShipAt(MockHttpSession session, UUID shipId, int x, int y) throws Exception {
        // GET /api/ships returns a list; we look up by ID so multi-ship tests
        // remain robust to other ships moving in parallel.
        MvcResult result = mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode ship = findShipById(body, shipId);
        assertThat(ship.get("x").asInt()).isEqualTo(x);
        assertThat(ship.get("y").asInt()).isEqualTo(y);
    }

    private static JsonNode findShipById(JsonNode shipsArray, UUID shipId) {
        for (JsonNode ship : shipsArray) {
            if (shipId.toString().equals(ship.get("id").asText())) {
                return ship;
            }
        }
        throw new AssertionError("Ship " + shipId + " not in response");
    }
}
