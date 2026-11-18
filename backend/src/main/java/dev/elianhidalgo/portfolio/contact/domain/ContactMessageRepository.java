package dev.elianhidalgo.portfolio.contact.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    /** Messages stored in the last 24 hours, measured by the database clock. */
    @Query(value = "select count(*) from contact.message where created_at > now() - interval '24 hours'", nativeQuery = true)
    long countLast24Hours();

    /** The inbox, newest first: ids grow with every stored message. */
    List<ContactMessage> findAllByOrderByIdDesc(Pageable pageable);

    /** The inbox after a cursor: messages older than {@code id}, newest first. */
    List<ContactMessage> findByIdLessThanOrderByIdDesc(long id, Pageable pageable);

    long countByReadFalse();
}
