package dev.elianhidalgo.portfolio.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The single administrator. Only a BCrypt hash of the password is ever stored. */
@Entity
@Table(schema = "auth", name = "admin_user")
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AdminAccount() {}

    public AdminAccount(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getUpdatedAt() { return updatedAt; }
}
