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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the PR 2 gameplay handlers: LAND (with gas-giant
 * ORBITING resolution), TAKE_OFF, EXTRACT (all three duration modes), SELL,
 * and the queue-time auto-prerequisite middleware. Drives ticks directly via
 * {@link TickService#advanceTick()} so a single test deterministically walks
 * through the order processing.
 *
 * <p>Coordinates referenced in this file map to the V9 seed:
 * Earth (50,50, ROCKY, IRON+WATER, buys IRON+WATER), Mars (60,55, ROCKY),
 * Jupiter (30,70, GAS_GIANT, HYDROGEN+HELIUM), Sirius (90,90, STAR).
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

    // ===== LAND / TAKE_OFF status derivation ===============================

    @Test
    void landOnRockyBodyDerivesLandedStatus() throws Exception {
        // Spawn is Earth (50,50, rocky). LAND completes → status LANDED.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "kira", "kira@example.com", "deep-space-99");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();

        assertStatusEquals(session, shipId, "LANDED");
    }

    @Test
    void landOnGasGiantDerivesOrbitingStatus() throws Exception {
        // Jupiter is GAS_GIANT — LAND there resolves to ORBITING per the status
        // derivation rule (body.kind == GAS_GIANT).
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "janeway", "janeway@example.com", "coffee-black-1");
        UUID shipId = firstShipIdFor(session);

        // MOVE to Jupiter (30,70). Spawn is (50,50); Chebyshev distance = 20.
        postOrder(session, shipId, "MOVE", Map.of("x", 30, "y", 70));
        for (int i = 0; i < 20; i++) tickService.advanceTick();
        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();

        assertStatusEquals(session, shipId, "ORBITING");
    }

    @Test
    void takeOffAfterLandDerivesIdleStatus() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "spock", "spock@example.com", "live-long-prosper");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();
        assertStatusEquals(session, shipId, "LANDED");

        postOrder(session, shipId, "TAKE_OFF", Map.of());
        tickService.advanceTick();
        assertStatusEquals(session, shipId, "IDLE");
    }

    @Test
    void takeOffWhileIdleCancels() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "uhura", "uhura@example.com", "channel-open-1");
        UUID shipId = firstShipIdFor(session);

        // Player is IDLE at Earth (no LAND issued). TAKE_OFF should cancel.
        UUID orderId = postOrder(session, shipId, "TAKE_OFF", Map.of());
        tickService.advanceTick();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    // ===== EXTRACT ==========================================================

    @Test
    void extractIronOnEarthFillsCargoAndDecrementsReserve() throws Exception {
        // 3 ticks of extraction on Earth → 30 IRON in cargo, reserve decreases
        // by 30. Note: body_resources persists across tests in this class so we
        // assert the DELTA on Earth's reserve rather than its absolute value
        // (the IT runner doesn't re-seed bodies between tests).
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "miles", "miles@example.com", "transporter-1");
        UUID shipId = firstShipIdFor(session);

        int reserveBefore = bodyResourceRepository
                .findByBodyIdAndResourceKind(EARTH_ID, ResourceKind.IRON)
                .orElseThrow().getReserve();

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick(); // LAND completes → LANDED

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
    void extractHydrogenWhileLandedCancels() throws Exception {
        // HYDROGEN requires ORBITING but we're LANDED on Earth → cancel.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "scotty", "scotty@example.com", "warp-core-stable");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();

        UUID extractId = postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "HYDROGEN", "mode", "until_cancelled"));
        tickService.advanceTick();

        ShipOrder cancelled = orderRepository.findById(extractId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void extractUntilFullStopsAtCargoCap() throws Exception {
        // MOTHERSHIP cargo cap = 500. Earth has 2000 IRON. Extracting at 10/tick
        // should reach cap after 50 ticks and complete.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "geordi", "geordi@example.com", "visor-1701-d");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();

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
    void sellIronOnEarthAddsCreditsAndRemovesCargo() throws Exception {
        // Earth buys IRON at 9 cr/unit (V9 seed). Fill 50 IRON, sell, expect
        // credits = 450, cargo row gone.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "tasha", "tasha@example.com", "yar-of-ngd-1");
        UUID shipId = firstShipIdFor(session);
        UUID userId = userIdForShip(shipId);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();
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
        // Mars (60,55) is rocky and does NOT buy WATER (V9 seed has Mars buying
        // only IRON). Player extracts IRON on Earth (which Mars buys), flies to
        // Mars, queues SELL for WATER — should cancel since Mars doesn't buy water.
        // Simpler version: just try to SELL on Earth a resource Earth doesn't
        // buy (Earth buys IRON + WATER; pick HELIUM).
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "wesley", "wesley@example.com", "ensign-crusher-1");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();

        UUID sellId = postOrder(session, shipId, "SELL", Map.of("resourceKind", "HELIUM"));
        tickService.advanceTick();

        assertThat(orderRepository.findById(sellId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void sellWithNoCargoCancels() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "data", "data@example.com", "positronic-1");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();

        UUID sellId = postOrder(session, shipId, "SELL", Map.of("resourceKind", "IRON"));
        tickService.advanceTick();

        // Earth buys IRON, but player has no IRON in cargo → cancel "nothing to sell".
        assertThat(orderRepository.findById(sellId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    // ===== Auto-prerequisite middleware =====================================

    @Test
    void postExtractWhileIdleAutoPrependsLand() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "barclay", "barclay@example.com", "reginald-1701-d");
        UUID shipId = firstShipIdFor(session);

        // Player IDLE at Earth; queue EXTRACT.
        postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "IRON", "mode", Map.of("ticks", 1)));

        mockMvc.perform(get("/api/ships/{shipId}/orders", shipId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("LAND"))
                .andExpect(jsonPath("$[0].autoInserted").value(true))
                .andExpect(jsonPath("$[1].kind").value("EXTRACT"))
                .andExpect(jsonPath("$[1].autoInserted").value(false));
    }

    @Test
    void postMoveWhileLandedAutoPrependsTakeOff() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "guinan", "guinan@example.com", "tenforward-1");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();
        // Status is now LANDED.

        postOrder(session, shipId, "MOVE", Map.of("x", 60, "y", 55));

        mockMvc.perform(get("/api/ships/{shipId}/orders", shipId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("TAKE_OFF"))
                .andExpect(jsonPath("$[0].autoInserted").value(true))
                .andExpect(jsonPath("$[1].kind").value("MOVE"))
                .andExpect(jsonPath("$[1].autoInserted").value(false));
    }

    @Test
    void postExtractWhileAlreadyLandedNoAutoPrerequisite() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "rolaren", "rolaren@example.com", "ro-laren-bajor-1");
        UUID shipId = firstShipIdFor(session);

        postOrder(session, shipId, "LAND", Map.of());
        tickService.advanceTick();
        // Now LANDED — no auto-LAND should be inserted.

        postOrder(session, shipId, "EXTRACT",
                Map.of("resourceKind", "IRON", "mode", Map.of("ticks", 1)));

        mockMvc.perform(get("/api/ships/{shipId}/orders", shipId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("EXTRACT"))
                .andExpect(jsonPath("$[0].autoInserted").value(false));
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
