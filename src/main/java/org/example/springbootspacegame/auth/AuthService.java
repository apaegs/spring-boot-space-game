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
}
