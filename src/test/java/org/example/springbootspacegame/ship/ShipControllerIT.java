package org.example.springbootspacegame.ship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;


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

import static org.example.springbootspacegame.MockMvcHelper.registerAndLogin;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ShipControllerIT {

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
    void newlyRegisteredUserHasOneShipAtSpawn() throws Exception {
        String username = "spock";
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, username, "spock@enterprise.example", "live-long-prosper");

        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").value(username + "'s ship"))
                .andExpect(jsonPath("$[0].x").value(50))
                .andExpect(jsonPath("$[0].y").value(50))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void twoUsersGetTwoDifferentShips() throws Exception {
        MockHttpSession alice = registerAndLogin(mockMvc, objectMapper, "alice", "alice@example.com", "password-alice-1");
        MockHttpSession bob = registerAndLogin(mockMvc, objectMapper, "bob", "bob@example.com", "password-bob-12");

        String aliceShipId = readFirstShipId(alice);
        String bobShipId = readFirstShipId(bob);
        assertNotEquals(aliceShipId, bobShipId);
    }

    @Test
    void shipsEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/ships"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        // Acceptance criterion for issue #27: a state-changing request from an
        // authenticated session without the X-XSRF-TOKEN header is rejected.
        // Spring Security returns 403 (not 401) — auth was fine, CSRF wasn't.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper,
                "csrf-victim", "csrf-victim@example.com", "password-victim1");

        mockMvc.perform(post("/api/ships").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shipPositionIsWithinGrid() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "uhura", "uhura@enterprise.example", "frequency-open-1");

        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].x", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$[0].x", lessThan(100)))
                .andExpect(jsonPath("$[0].y", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$[0].y", lessThan(100)));
    }

    @Test
    void canCreateAdditionalShipsAndTheyGetNumberedNames() throws Exception {
        String username = "kira";
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, username, "kira@enterprise.example", "deep-space-niner");

        // The auto-created mothership keeps the bare name; subsequent ships get
        // numbered suffixes. Tests both: the default-named first ship was created
        // at register time; we POST two more and check the numbering.
        mockMvc.perform(post("/api/ships").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(username + "'s ship 2"));

        mockMvc.perform(post("/api/ships").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(username + "'s ship 3"));

        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void canCreateShipWithCustomName() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "worf", "worf@enterprise.example", "klingon-honor1");

        mockMvc.perform(post("/api/ships").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Bird-of-Prey" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bird-of-Prey"));
    }

    @Test
    void createShipWithWhitespaceNameFallsBackToAutoName() throws Exception {
        String username = "tuvok";
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, username, "tuvok@enterprise.example", "logical-choice");

        // Whitespace-only name in the request body trims to empty, which we
        // treat as "no name supplied" → auto-generated.
        mockMvc.perform(post("/api/ships").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "   " }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(username + "'s ship 2"));
    }

    // --- rename ---

    @Test
    void canRenameOwnShip() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "picard", "picard@enterprise.example", "engage-1701D");
        String shipId = readFirstShipId(session);

        mockMvc.perform(patch("/api/ships/" + shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Enterprise" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipId))
                .andExpect(jsonPath("$.name").value("Enterprise"));
    }

    @Test
    void renameToBlankNameReturnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "riker", "riker@enterprise.example", "number-one1");
        String shipId = readFirstShipId(session);

        mockMvc.perform(patch("/api/ships/" + shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "   " }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renameToDuplicateNameReturnsConflict() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "troi", "troi@enterprise.example", "counselor1");

        // Create a second ship with a known name, then try to rename the first to match.
        mockMvc.perform(post("/api/ships").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Betazoid" }
                                """))
                .andExpect(status().isCreated());

        String firstShipId = readFirstShipId(session);
        mockMvc.perform(patch("/api/ships/" + firstShipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Betazoid" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void renameOtherPlayersShipReturnsNotFound() throws Exception {
        MockHttpSession data = registerAndLogin(mockMvc, objectMapper, "data", "data@enterprise.example", "positronic1");
        MockHttpSession laforge = registerAndLogin(mockMvc, objectMapper, "laforge", "laforge@enterprise.example", "visor-on-12");

        String dataShipId = readFirstShipId(data);

        // LaForge tries to rename Data's ship — should 404.
        mockMvc.perform(patch("/api/ships/" + dataShipId).session(laforge).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Stolen" }
                                """))
                .andExpect(status().isNotFound());
    }

    // --- status ---

    @Test
    void newShipWithNoOrdersIsIdle() throws Exception {
        // A freshly spawned ship has no completed orders, so LANDED requires an
        // explicit LAND order — it must be IDLE until one completes.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "status-spawn", "status-spawn@example.com", "password-spawn1");

        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IDLE"));
    }

    @Test
    void shipWithPendingOrderIsMoving() throws Exception {
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "status-move", "status-move@example.com", "password-move1");
        String shipId = readFirstShipId(session);

        // Queue a MOVE order far enough away that it won't complete in one tick.
        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("kind", "MOVE", "params", Map.of("x", 60, "y", 60)))))
                .andExpect(status().isCreated());

        // PENDING branch
        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("MOVING"));

        tickService.advanceTick(); // order transitions PENDING → ACTIVE

        // ACTIVE branch
        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("MOVING"));
    }

    @Test
    void shipOffBodyWithNoOrdersIsIdle() throws Exception {
        // Move the ship one tile off Earth (50,50) → (51,50), then confirm
        // there are no pending orders and status is IDLE (not on a body).
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "status-idle", "status-idle@example.com", "password-idle1");
        String shipId = readFirstShipId(session);

        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("kind", "MOVE", "params", Map.of("x", 51, "y", 50)))))
                .andExpect(status().isCreated());

        tickService.advanceTick(); // MOVE completes — ship at (51,50), no body there

        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IDLE"));
    }

    @Test
    void shipLandedOnBodyIsLanded() throws Exception {
        // Spawn is at (50,50) where Earth is seeded (V9). Queue LAND, fire one
        // tick so the order completes, then check status = LANDED.
        MockHttpSession session = registerAndLogin(mockMvc, objectMapper, "status-land", "status-land@example.com", "password-land1");
        String shipId = readFirstShipId(session);

        mockMvc.perform(post("/api/ships/{shipId}/orders", shipId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("kind", "LAND", "params", Map.of()))))
                .andExpect(status().isCreated());

        tickService.advanceTick(); // LAND order completes

        mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("LANDED"));
    }

    // --- helpers ---

    private String readFirstShipId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ships").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(0).get("id").asText();
    }
}
