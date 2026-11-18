package dev.elianhidalgo.portfolio.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "profile", name = "profile")
public class Profile {

    @Id
    private Short id;

    private String fullName;
    private String headlineEs;
    private String headlineEn;
    private String aboutEs;
    private String aboutEn;
    private String location;
    private String email;
    private String githubUrl;
    private String linkedinUrl;
    private String avatarUrl;
    private String cvUrlEs;
    private String cvUrlEn;
    private Instant updatedAt;

    protected Profile() {
        // for JPA
    }

    public void update(ProfileChanges changes, Instant now) {
        this.fullName = changes.fullName();
        this.headlineEs = changes.headlineEs();
        this.headlineEn = changes.headlineEn();
        this.aboutEs = changes.aboutEs();
        this.aboutEn = changes.aboutEn();
        this.location = changes.location();
        this.email = changes.email();
        this.githubUrl = changes.githubUrl();
        this.linkedinUrl = changes.linkedinUrl();
        this.avatarUrl = changes.avatarUrl();
        this.cvUrlEs = changes.cvUrlEs();
        this.cvUrlEn = changes.cvUrlEn();
        this.updatedAt = now;
    }

    public String getFullName() { return fullName; }
    public String getHeadlineEs() { return headlineEs; }
    public String getHeadlineEn() { return headlineEn; }
    public String getAboutEs() { return aboutEs; }
    public String getAboutEn() { return aboutEn; }
    public String getLocation() { return location; }
    public String getEmail() { return email; }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getCvUrlEs() { return cvUrlEs; }
    public String getCvUrlEn() { return cvUrlEn; }
    public Instant getUpdatedAt() { return updatedAt; }
}
