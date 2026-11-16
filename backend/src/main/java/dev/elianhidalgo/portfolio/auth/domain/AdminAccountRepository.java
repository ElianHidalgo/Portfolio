package dev.elianhidalgo.portfolio.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByUsername(String username);

    /** There is a single admin: any account under another username is removed. */
    @Modifying
    @Query("delete from AdminAccount a where a.username <> :username")
    void deleteByUsernameNot(String username);
}
