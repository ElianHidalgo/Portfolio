package dev.elianhidalgo.portfolio.profile;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Captures the seeded profile and skills through the owner connection and puts them back,
 * so tests that edit them leave ProfileApiTests (and each other) undisturbed.
 */
final class ProfileSnapshot {

    private final JdbcTemplate owner;
    private final Map<String, Object> profile;
    private final List<Map<String, Object>> skills;

    ProfileSnapshot(JdbcTemplate owner) {
        this.owner = owner;
        this.profile = owner.queryForMap("select * from profile.profile");
        this.skills = owner.queryForList("select * from profile.skill order by id");
    }

    void restore() {
        owner.update("""
                update profile.profile set full_name = ?, headline_es = ?, headline_en = ?, about_es = ?, about_en = ?,
                    location = ?, email = ?, github_url = ?, linkedin_url = ?, avatar_url = ?, cv_url_es = ?, cv_url_en = ?,
                    updated_at = ?""",
                profile.get("full_name"), profile.get("headline_es"), profile.get("headline_en"),
                profile.get("about_es"), profile.get("about_en"), profile.get("location"), profile.get("email"),
                profile.get("github_url"), profile.get("linkedin_url"), profile.get("avatar_url"),
                profile.get("cv_url_es"), profile.get("cv_url_en"), profile.get("updated_at"));
        owner.update("delete from profile.skill");
        for (var skill : skills) {
            owner.update("insert into profile.skill (id, name, category, display_order) values (?, ?, ?, ?)",
                    skill.get("id"), skill.get("name"), skill.get("category"), skill.get("display_order"));
        }
    }
}
