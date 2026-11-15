package dev.elianhidalgo.portfolio.projects.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project")
public class Project {

    @Id
    private Long id;

    private String slug;
    private String titleEs;
    private String titleEn;
    private String summaryEs;
    private String summaryEn;
    private String descriptionEs;
    private String descriptionEn;

    /** PostgreSQL varchar[] column, in display order. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> technologies;

    private String repoUrl;
    private String demoUrl;
    private String imageUrl;
    private boolean featured;
    private int displayOrder;
    private boolean published;
    private Instant createdAt;

    protected Project() {
        // for JPA
    }

    public String getSlug() { return slug; }
    public String getTitleEs() { return titleEs; }
    public String getTitleEn() { return titleEn; }
    public String getSummaryEs() { return summaryEs; }
    public String getSummaryEn() { return summaryEn; }
    public String getDescriptionEs() { return descriptionEs; }
    public String getDescriptionEn() { return descriptionEn; }
    public List<String> getTechnologies() { return technologies; }
    public String getRepoUrl() { return repoUrl; }
    public String getDemoUrl() { return demoUrl; }
    public String getImageUrl() { return imageUrl; }
    public boolean isFeatured() { return featured; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isPublished() { return published; }
    public Instant getCreatedAt() { return createdAt; }
}
