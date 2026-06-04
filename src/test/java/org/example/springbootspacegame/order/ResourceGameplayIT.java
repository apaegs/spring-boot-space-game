package org.example.springbootspacegame.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.example.springbootspacegame.auth.UserRepository;
import org.example.springbootspacegame.body.BodyResourceRepository;
import org.example.springbootspacegame.resource.ResourceKind;
import org.example.springbootspacegame.ship.ShipCargoRepository;
import org.example.springbootspacegame.ship.ShipRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.springbootspacegame.MockMvcHelper.registerAndLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the resource-loop handlers under the orbit-only
 * model (issue #87): EXTRACT (all three duration modes) and SELL, both gated
 * on {@code ORBITING} (Chebyshev-adjacent to a celestial body). Drives ticks
 * directly via {@link TickService#advanceTick()} so a single test
 * deterministically walks through the order processing.
 *
 * <p>Coordinates referenced in this file map to the V9 seed: spawn (51,50)
 * adjacent to Earth (50,50, ROCKY, IRON+WATER, buys IRON+WATER); Jupiter
 * (30,70, GAS_GIANT, HYDROGEN+HELIUM).
 */
@IntegrationTest
class ResourceGameplayIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private TickService tickService;
    @Autowired private ShipOrderRepository orderRepository;
    @Autowired private ShipCargoRepository shipCargoRepository;
    @Autowired private BodyResourceRepository bodyResourceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ShipRepository shipRepository;

    /** Earth's deterministic seed UUID — see V9 INSERT row 1. */
    private static final UUID EARTH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // ===== Status derivation ================================================

    @Test
    void spawnAdjacentToEarthDerivesOrbiting() throws Exception {
        // Spawn is (51,50); Earth is seeded at (50,50). Chebyshev distance = 1
        // → status ORBITING with no orders ever queued.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "kira", "kira@example.com", "deep-space-99");
        UUID shipId = firstShipIdFor(session);

        assertStatusEquals(session, shipId, "ORBITING");
    }

    @Test
    void movingNextToGasGiantDerivesOrbiting() throws Exception {
        // Jupiter is at (30,70). Move to (31,70) → adjacent → ORBITING.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "janeway", "janeway@example.com", "coffee-black-1");
        UUID shipId = firstShipIdFor(session);

        // Spawn is (51,50); Chebyshev distance to (31,70) is 20.
        postOrder(session, shipId, "MOVE", Map.of("x", 31, "y", 70));
        for (int i = 0; i < 20; i++) tickService.advanceTick();

        assertStatusEquals(session, shipId, "ORBITING");
    }

    @Test
    void shipFarFromAnyBodyIsIdle() throws Exception {
        // (10,30) has no body within Chebyshev distance 1 — nearest are Wolf 359
        // (8,15), Hebe (35,30), Pluto (25,25), Haumea (20,40). Move there and
        // confirm IDLE.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "spock", "spock@example.com", "live-long-prosper");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "MOVE", Map.of("x", 10, "y", 30));
        // Chebyshev (51,50) -> (10,30) = max(41, 20) = 41 ticks.
        for (int i = 0; i < 41; i++) tickService.advanceTick();

        assertStatusEquals(session, shipId, "IDLE");
    }

    // ===== EXTRACT ==========================================================

    @Test
    void extractIronOrbitingEarthFillsCargoAndDecrementsReserve() throws Exception {
        // Spawn is already ORBITING Earth. Three ticks of EXTRACT → 30 IRON
        // in cargo and the same delta off Earth's reserve. Body reserves
        // persist across tests, so the assertion is on the DELTA.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "miles", "miles@example.com", "transporter-1");
        UUID shipId = firstShipIdFor(session);

        int reserveBefore = bodyResourceRepository
                .findByBodyIdAndResourceKind(EARTH_ID, ResourceKind.IRON)
                .orElseThrow().getReserve();

        postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "IRON", "mode", Map.of("ticks", 3)));
        tickService.advanceTick(); // extract 10
        tickService.advanceTick(); // extract 10
        tickService.advanceTick(); // extract 10 → progressTicks=3 → completed

        assertThat(shipCargoRepository.findByShipIdAndResourceKind(shipId, ResourceKind.IRON))
                .map(c -> c.getQty())
                .hasValue(30);
        int reserveAfter = bodyResourceRepository
                .findByBodyIdAndResourceKind(EARTH_ID, ResourceKind.IRON)
                .orElseThrow().getReserve();
        assertThat(reserveBefore - reserveAfter).isEqualTo(30);
    }

    @Test
    void extractHydrogenOrbitingEarthCancels() throws Exception {
        // Earth doesn't have HYDROGEN reserves → the handler cancels the order
        // with "no HYDROGEN" rather than silently extracting nothing.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "scotty", "scotty@example.com", "warp-core-stable");
        UUID shipId = firstShipIdFor(session);

        UUID extractId = postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "HYDROGEN", "mode", "until_cancelled"));
        tickService.advanceTick();

        ShipOrder cancelled = orderRepository.findById(extractId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void extractWhileNotOrbitingCancels() throws Exception {
        // Move to a tile with no adjacent body (10,30) → IDLE → EXTRACT cancels.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "uhura", "uhura@example.com", "channel-open-1");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "MOVE", Map.of("x", 10, "y", 30));
        for (int i = 0; i < 41; i++) tickService.advanceTick();
        // Status now IDLE (no body in Chebyshev range of (10,30)).

        UUID extractId = postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "IRON", "mode", "until_cancelled"));
        tickService.advanceTick();

        assertThat(orderRepository.findById(extractId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void extractUntilFullStopsAtCargoCap() throws Exception {
        // MOTHERSHIP cargo cap = 500. Earth has IRON in the thousands.
        // Extracting at 10/tick should reach cap after 50 ticks and complete.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "geordi", "geordi@example.com", "visor-1701-d");
        UUID shipId = firstShipIdFor(session);

        UUID extractId = postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "IRON", "mode", Map.of("until_full", true)));
        for (int i = 0; i < 50; i++) tickService.advanceTick();

        assertThat(orderRepository.findById(extractId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(shipCargoRepository.findByShipIdAndResourceKind(shipId, ResourceKind.IRON))
                .map(c -> c.getQty())
                .hasValue(500);
    }

    // ===== SELL =============================================================

    @Test
    void sellIronOrbitingEarthAddsCreditsAndRemovesCargo() throws Exception {
        // Earth buys IRON at 9 cr/unit (V9 seed). Extract 50 IRON, sell, expect
        // credits = 450, cargo row gone.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "tasha", "tasha@example.com", "yar-of-ngd-1");
        UUID shipId = firstShipIdFor(session);
        UUID userId = userIdForShip(shipId);

        postOrder(session, shipId, "EXTRACT", Map.of("resourceKind", "IRON", "mode", Map.of("ticks", 5)));
        for (int i = 0; i < 5; i++) tickService.advanceTick();
        // 50 IRON in cargo.

        postOrder(session, shipId, "SELL", Map.of("resourceKind", "IRON"));
        tickService.advanceTick();

        assertThat(userRepository.findById(userId).orElseThrow().getCredits()).isEqualTo(50L * 9L);
        assertThat(shipCargoRepository.findByShipIdAndResourceKind(shipId, ResourceKind.IRON))
                .isEmpty();
    }

    @Test
    void sellAtNonBuyerCancels() throws Exception {
        // Earth buys IRON + WATER; queue SELL HELIUM at Earth — cancel with
        // "does not buy HELIUM".
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "wesley", "wesley@example.com", "ensign-crusher-1");
        UUID shipId = firstShipIdFor(session);

        UUID sellId = postOrder(session, shipId, "SELL", Map.of("resourceKind", "HELIUM"));
        tickService.advanceTick();

        assertThat(orderRepository.findById(sellId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void sellWithNoCargoCancels() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "data", "data@example.com", "positronic-1");
        UUID shipId = firstShipIdFor(session);

        UUID sellId = postOrder(session, shipId, "SELL", Map.of("resourceKind", "IRON"));
        tickService.advanceTick();

        // Earth buys IRON, but player has no IRON in cargo → cancel "nothing to sell".
        assertThat(orderRepository.findById(sellId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void sellWhileNotOrbitingCancels() throws Exception {
        // MOVE far from any body, queue SELL — cancels with "must be orbiting".
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "rolaren", "rolaren@example.com", "ro-laren-bajor-1");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "MOVE", Map.of("x", 10, "y", 30));
        for (int i = 0; i < 41; i++) tickService.advanceTick();

        UUID sellId = postOrder(session, shipId, "SELL", Map.of("resourceKind", "IRON"));
        tickService.advanceTick();

        assertThat(orderRepository.findById(sellId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    // ===== Param validation ================================================

    @Test
    void extractWithoutResourceKindReturns400() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "leeta", "leeta@example.com", "dabo-girl-1");
        UUID shipId = firstShipIdFor(session);

        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "EXTRACT", "params": { "mode": "until_cancelled" } }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extractWithUnknownResourceKindReturns400() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "morn", "morn@example.com", "silent-quark-1");
        UUID shipId = firstShipIdFor(session);

        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "EXTRACT", "params": { "resourceKind": "UNOBTANIUM", "mode": "until_cancelled" } }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ===== helpers ==========================================================

    private UUID firstShipIdFor(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get(0).get("id").asText());
    }

    private UUID userIdForShip(UUID shipId) {
        return shipRepository.findById(shipId).orElseThrow().getUserId();
    }

    private UUID postOrder(MockHttpSession session, UUID shipId, String kind, Map<String, Object> params)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("kind", kind, "params", params));
        MvcResult result = mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }

    private void assertStatusEquals(MockHttpSession session, UUID shipId, String expected) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ships = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode ship : ships) {
            if (shipId.toString().equals(ship.get("id").asText())) {
                assertThat(ship.get("status").asText()).isEqualTo(expected);
                return;
            }
        }
        throw new AssertionError("Ship " + shipId + " not in response");
    }
}
