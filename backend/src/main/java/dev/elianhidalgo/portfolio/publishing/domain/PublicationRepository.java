package dev.elianhidalgo.portfolio.publishing.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicationRepository extends JpaRepository<Publication, Long> {

    Optional<Publication> findFirstByOrderByRequestedAtDesc();
}
