package dev.elianhidalgo.portfolio.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "profile", name = "skill")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private SkillCategory category;

    private int displayOrder;

    protected Skill() {
        // for JPA
    }

    public Skill(String name, SkillCategory category, int displayOrder) {
        this.name = name;
        this.category = category;
        this.displayOrder = displayOrder;
    }

    public void rename(String name) { this.name = name; }
    public void moveTo(SkillCategory category) { this.category = category; }
    public void placeAt(int displayOrder) { this.displayOrder = displayOrder; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public SkillCategory getCategory() { return category; }
    public int getDisplayOrder() { return displayOrder; }
}
