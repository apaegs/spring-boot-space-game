package org.example.springbootspacegame.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springbootspacegame.IntegrationTest;
import org.example.springbootspacegame.order.CreateOrderRequest;
import org.example.springbootspacegame.order.OrderKind;
import org.example.springbootspacegame.order.ShipOrderRepository;
import org.example.springbootspacegame.ship.ShipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private ShipOrderRepository shipOrderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registerLoginMeFlow() throws Exception {
        String username = "captain_kirk";
        String email = "kirk@enterprise.example";
        String password = "beam-me-up-1701";

        // register
        String registerBody = objectMapper.writeValueAsString(
                new RegisterRequest(username, email, password));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // /me without session → 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // login → captures the session
        String loginBody = objectMapper.writeValueAsString(new LoginRequest(username, password));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // /me with session → 200
        mockMvc.perform(get("/api/auth/me").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // logout — state-changing, needs CSRF (not exempted like /login & /register)
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginWithWrongPasswordIs401() throws Exception {
        String username = "scotty";
        String email = "scotty@enterprise.example";
        String password = "warp-core-stable";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMeRemovesUserShipsAndOrdersAndInvalidatesSession() throws Exception {
        String username = "spock";
        String email = "spock@enterprise.example";
        String password = "live-long-and-prosper";

        // Register + log in.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Sanity: registration created the user + the auto mothership.
        var user = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        var ships = shipRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
        assertThat(ships).hasSize(1);
        var shipId = ships.getFirst().getId();

        // Queue an order so the cascade has something to delete in ship_orders too.
        mockMvc.perform(post("/api/ships/" + shipId + "/orders")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(OrderKind.MOVE, Map.of("x", 10, "y", 10)))))
                .andExpect(status().isCreated());
        assertThat(shipOrderRepository.findAll()).hasSize(1);

        // DELETE /api/auth/me → 204
        mockMvc.perform(delete("/api/auth/me").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // Cascade verified at the data layer: user, their ship, and the order are gone.
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(shipRepository.findByUserIdOrderByCreatedAtAsc(user.getId())).isEmpty();
        assertThat(shipOrderRepository.findAll()).isEmpty();

        // Session invalidated: /me on the same session now returns 401.
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMeWithoutSessionIs401() throws Exception {
        // No session → no principal → handled by JsonSecurityErrorHandlers, not the
        // service. CSRF isn't even reached.
        mockMvc.perform(delete("/api/auth/me").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("uhura", "uhura@enterprise.example", "frequency-open"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // same username, different email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("UHURA", "other@enterprise.example", "different-pw-1"))))
                .andExpect(status().isConflict());
    }
}
