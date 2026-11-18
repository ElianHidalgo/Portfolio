package dev.elianhidalgo.portfolio.projects.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByPublishedTrueOrderByDisplayOrderAscSlugAsc();

    Optional<Project> findBySlugAndPublishedTrue(String slug);

    boolean existsBySlug(String slug);

    Optional<Project> findFirstByOrderByDisplayOrderDesc();

    List<Project> findAllByOrderByDisplayOrderAscSlugAsc();

    /** Locks the published rows, so two admins cannot each hide "the other" last project. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Project> findAllByPublishedTrue();
}
