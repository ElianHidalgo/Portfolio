package dev.elianhidalgo.portfolio.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill")
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

    public String getName() { return name; }
    public SkillCategory getCategory() { return category; }
    public int getDisplayOrder() { return displayOrder; }
}
