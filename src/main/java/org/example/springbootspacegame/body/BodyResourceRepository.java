package org.example.springbootspacegame.body;

import org.example.springbootspacegame.resource.ResourceKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BodyResourceRepository extends JpaRepository<BodyResource, BodyResourceId> {

    /**
     * All resource rows for a body. Used when rendering body details to the UI.
     */
    List<BodyResource> findByBodyId(UUID bodyId);

    /**
     * Reserve row for a specific (body, resource) — used by the EXTRACT handler
     * (PR 2) to decrement the reserve atomically within the tick transaction.
     */
    Optional<BodyResource> findByBodyIdAndResourceKind(UUID bodyId, ResourceKind resourceKind);
}
