package org.example.springbootspacegame.auth;

import lombok.RequiredArgsConstructor;
import org.example.springbootspacegame.ship.ShipService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business logic for the auth flow — counterpart to the thin
 * {@link AuthController} transport layer.
 *
 * <p>{@link #register} guarantees the "every user has at least one ship"
 * invariant by creating the starting mothership in the same transaction
 * as the user row. A failure in ship creation rolls back the user insert,
 * so a half-registered "user without ship" row can never be observed by
 * another request.
 *
 * <p>The conflict-handling in {@code register} is layered on purpose:
 * the pre-checks {@code existsByUsernameIgnoreCase} / {@code existsByEmailIgnoreCase}
 * produce a clean 409 in the common case, and the DB unique index plus
 * the {@code saveAndFlush}/{@link DataIntegrityViolationException} remap
 * catches the concurrent-insert race where two requests both pass the
 * pre-checks. Either path returns the same 409, so the client doesn't
 * have to distinguish.
 *
 * <p>{@link #getCurrentUser} is read-only and resolves the principal
 * out of the {@code SecurityContext} populated by the login flow's
 * {@code SecurityContextRepository.saveContext} call — it does not
 * re-authenticate.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShipService shipService;

    @Transactional
    public MeResponse register(RegisterRequest request) {
        // Pre-checks give a clean 409 in the common case. The DB unique indexes
        // (see V1__create_users.sql) are still the source of truth and protect against
        // the race where two concurrent registrations both pass these checks.
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is registered");
        }

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password())
        );
        User saved;
        try {
            // saveAndFlush (not save) so the INSERT executes inside this try block.
            // Plain save() leaves the actual INSERT to commit time — outside this method —
            // and the unique-constraint violation would escape as a generic 500 instead
            // of being translated to 409 below.
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Race: a concurrent request inserted the same username/email between the
            // exists-check above and the flush. Translate Hibernate's 500 into a 409.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered", e);
        }

        // Auto-create the player's starting mothership in the same transaction.
        // If ship creation fails, the user insert rolls back — so there's never a
        // user-without-a-ship state to observe. Players can create additional
        // ships later via POST /api/ships (issue #32). Pass null for the desired
        // name so ShipService generates the default "<username>'s ship".
        shipService.createForNewUser(saved.getId(), saved.getUsername());
        return MeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return MeResponse.from(user);
    }

    /**
     * Hard-delete the currently authenticated user. Cascades through ships and
     * ship_orders via the FK {@code ON DELETE CASCADE} clauses already in place
     * (V2 for ships → users, V4 for ship_orders → ships) — no follow-up writes
     * needed here.
     *
     * <p>Session invalidation is the caller's job ({@link AuthController#deleteMe})
     * because it has the {@code HttpServletRequest}. This service layer only
     * touches the DB.
     */
    @Transactional
    public void deleteCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // deleteById is a no-op on missing rows by default, but our session
        // says the user exists; if it doesn't, something is badly out of sync
        // and 404 is more honest than a silent 204.
        if (!userRepository.existsById(principal.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        userRepository.deleteById(principal.getUserId());
    }
}
