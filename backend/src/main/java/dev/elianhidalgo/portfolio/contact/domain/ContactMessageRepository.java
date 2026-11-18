package dev.elianhidalgo.portfolio.contact.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    /** Messages stored in the last 24 hours, measured by the database clock. */
    @Query(value = "select count(*) from contact.message where created_at > now() - interval '24 hours'", nativeQuery = true)
    long countLast24Hours();

    /** The inbox: newest first (id breaks ties between messages stored in the same instant). */
    Page<ContactMessage> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByReadFalse();
}
