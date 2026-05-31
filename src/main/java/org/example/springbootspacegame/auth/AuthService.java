package org.example.springbootspacegame.auth;

import lombok.RequiredArgsConstructor;
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

    @Transactional
    public User register(RegisterRequest request) {
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
        try {
            // saveAndFlush (not save) so the INSERT executes inside this try block.
            // Plain save() leaves the actual INSERT to commit time — outside this method —
            // and the unique-constraint violation would escape as a generic 500 instead
            // of being translated to 409 below.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Race: a concurrent request inserted the same username/email between the
            // exists-check above and the flush. Translate Hibernate's 500 into a 409.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered", e);
        }
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
