package dev.elianhidalgo.portfolio.projects.web;

import dev.elianhidalgo.portfolio.projects.domain.ProjectChanges;
import dev.elianhidalgo.portfolio.shared.SafeLink;
import dev.elianhidalgo.portfolio.shared.TextRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * The project form as sent by the admin, without the slug (it is fixed once created). Values are
 * trimmed before validation, so the limits apply to what is stored; empty links become null.
 * Control characters are refused (PostgreSQL rejects NUL); descriptions may contain tabs and line breaks.
 */
public record ProjectRequest(
        @NotNull @Valid Title title,
        @NotNull @Valid Summary summary,
        @NotNull @Valid Description description,
        @NotNull @Size(max = 20) List<String> technologies,
        @Size(max = 500) @SafeLink String repoUrl,
        @Size(max = 500) @SafeLink String demoUrl,
        boolean featured,
        boolean published) {

    static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";

    public record Title(
            @NotBlank @Size(max = 120) @Pattern(regexp = TextRules.NO_CONTROL) String es,
            @NotBlank @Size(max = 120) @Pattern(regexp = TextRules.NO_CONTROL) String en) {

        public Title {
            es = strip(es);
            en = strip(en);
        }
    }

    public record Summary(
            @NotBlank @Size(max = 300) @Pattern(regexp = TextRules.NO_CONTROL) String es,
            @NotBlank @Size(max = 300) @Pattern(regexp = TextRules.NO_CONTROL) String en) {

        public Summary {
            es = strip(es);
            en = strip(en);
        }
    }

    public record Description(
            @NotBlank @Size(max = 4000) @Pattern(regexp = TextRules.NO_CONTROL_EXCEPT_WHITESPACE) String es,
            @NotBlank @Size(max = 4000) @Pattern(regexp = TextRules.NO_CONTROL_EXCEPT_WHITESPACE) String en) {

        public Description {
            es = strip(es);
            en = strip(en);
        }
    }

    /** The same form for a new project, which also carries its slug. */
    public record Create(
            @NotBlank @Size(max = 80) @Pattern(regexp = SLUG) String slug,
            @NotNull @Valid Title title,
            @NotNull @Valid Summary summary,
            @NotNull @Valid Description description,
            @NotNull @Size(max = 20) List<String> technologies,
            @Size(max = 500) @SafeLink String repoUrl,
            @Size(max = 500) @SafeLink String demoUrl,
            boolean featured,
            boolean published) {

        public Create {
            technologies = stripAll(technologies);
            repoUrl = blankToNull(repoUrl);
            demoUrl = blankToNull(demoUrl);
        }

        @AssertTrue(message = "must list up to 20 different names of 1 to 40 characters")
        public boolean isTechnologiesValid() {
            return validTechnologies(technologies);
        }

        ProjectRequest form() {
            return new ProjectRequest(title, summary, description, technologies, repoUrl, demoUrl, featured, published);
        }
    }

    public ProjectRequest {
        technologies = stripAll(technologies);
        repoUrl = blankToNull(repoUrl);
        demoUrl = blankToNull(demoUrl);
    }

    @AssertTrue(message = "must list up to 20 different names of 1 to 40 characters")
    public boolean isTechnologiesValid() {
        return validTechnologies(technologies);
    }

    ProjectChanges toChanges() {
        return new ProjectChanges(title.es(), title.en(), summary.es(), summary.en(), description.es(),
                description.en(), technologies, repoUrl, demoUrl, featured, published);
    }

    /** Entries are 1 to 40 characters without control characters; "Java" and "java" count as the same. */
    private static boolean validTechnologies(List<String> technologies) {
        if (technologies == null) {
            return true; // reported by @NotNull
        }
        var seen = new HashSet<String>();
        return technologies.stream().allMatch(t -> t != null && !t.isEmpty() && t.length() <= 40
                && t.matches(TextRules.NO_CONTROL) && seen.add(t.toLowerCase(Locale.ROOT)));
    }

    private static List<String> stripAll(List<String> values) {
        return values == null ? null : values.stream().map(ProjectRequest::strip).toList();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
