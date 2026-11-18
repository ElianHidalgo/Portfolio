package dev.elianhidalgo.portfolio.publishing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An accepted request to rebuild the public site. */
@Entity
@Table(schema = "publishing", name = "publication")
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "build_id")
    private String buildId;

    protected Publication() {}

    public Publication(Instant requestedAt, String buildId) {
        this.requestedAt = requestedAt;
        this.buildId = buildId;
    }

    public Instant getRequestedAt() { return requestedAt; }
    public String getBuildId() { return buildId; }
}
