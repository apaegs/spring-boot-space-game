package org.example.springbootspacegame.planet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlanetService {

    private final PlanetRepository planetRepository;

    @Transactional(readOnly = true)
    public List<PlanetDto> getAll() {
        return planetRepository.findAll().stream()
                .map(PlanetDto::from)
                .toList();
    }

    /**
     * Returns the planet on the given tile, if any. Used by the LAND order
     * handler — entity (not DTO) because the caller is another service, not the
     * REST layer.
     */
    @Transactional(readOnly = true)
    public Optional<Planet> findAt(int x, int y) {
        return planetRepository.findByXAndY(x, y);
    }
}
