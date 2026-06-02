package org.example.springbootspacegame.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Spring Security's {@link UserDetailsService} SPI to our {@link User}
 * entity. The {@code AuthenticationManager} configured in {@link SecurityConfig}
 * resolves this bean automatically; consumers don't call it directly.
 *
 * <p>Two intentional design points worth knowing about:
 *
 * <ul>
 *   <li><b>Case-insensitive lookup.</b> Registration enforces case-insensitive
 *       uniqueness on usernames ({@code existsByUsernameIgnoreCase}), so login
 *       must match the same semantics — otherwise a user who registered as
 *       {@code "Picard"} could fail to log in by typing {@code "picard"} into
 *       the form despite the duplicate check having claimed the name. The
 *       repository method matching this is {@code findByUsernameIgnoreCase}.</li>
 *   <li><b>{@code @Transactional(readOnly = true)}.</b> Wraps the lookup in a
 *       JPA session so the lazy collections on {@link User} (none today, but
 *       future ones like roles or sessions) wouldn't blow up with
 *       LazyInitializationException the moment Spring Security touches them
 *       on a credential check. The {@code readOnly} flag is a hint to
 *       Hibernate to skip dirty-tracking — this method never mutates.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
