package dev.elianhidalgo.portfolio.profile.web;

import dev.elianhidalgo.portfolio.profile.domain.ProfileChanges;
import dev.elianhidalgo.portfolio.shared.SafeLink;
import dev.elianhidalgo.portfolio.shared.TextRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The profile form as sent by the admin. Values are trimmed before validation, so the limits
 * apply to what is stored; optional fields left empty become null. Control characters are
 * refused (PostgreSQL rejects NUL); the about text may contain tabs and line breaks.
 */
public record ProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = TextRules.NO_CONTROL) String fullName,
        @NotNull @Valid Headline headline,
        @NotNull @Valid About about,
        @Size(max = 100) @Pattern(regexp = TextRules.NO_CONTROL) String location,
        @Email @Size(max = 254) String email,
        @Size(max = 500) @SafeLink String githubUrl,
        @Size(max = 500) @SafeLink String linkedinUrl,
        @Size(max = 500) @SafeLink(allowPath = true) String avatarUrl,
        @Valid CvUrl cvUrl) {

    public record Headline(
            @NotBlank @Size(max = 160) @Pattern(regexp = TextRules.NO_CONTROL) String es,
            @NotBlank @Size(max = 160) @Pattern(regexp = TextRules.NO_CONTROL) String en) {

        public Headline {
            es = strip(es);
            en = strip(en);
        }
    }

    public record About(
            @NotBlank @Size(max = 2500) @Pattern(regexp = TextRules.NO_CONTROL_EXCEPT_WHITESPACE) String es,
            @NotBlank @Size(max = 2500) @Pattern(regexp = TextRules.NO_CONTROL_EXCEPT_WHITESPACE) String en) {

        public About {
            es = strip(es);
            en = strip(en);
        }
    }

    public record CvUrl(
            @Size(max = 500) @SafeLink(allowPath = true) String es,
            @Size(max = 500) @SafeLink(allowPath = true) String en) {

        public CvUrl {
            es = blankToNull(es);
            en = blankToNull(en);
        }
    }

    public ProfileRequest {
        fullName = strip(fullName);
        location = blankToNull(location);
        email = blankToNull(email);
        githubUrl = blankToNull(githubUrl);
        linkedinUrl = blankToNull(linkedinUrl);
        avatarUrl = blankToNull(avatarUrl);
    }

    ProfileChanges toChanges() {
        var cv = cvUrl == null ? new CvUrl(null, null) : cvUrl;
        return new ProfileChanges(fullName, headline.es(), headline.en(), about.es(), about.en(), location, email,
                githubUrl, linkedinUrl, avatarUrl, cv.es(), cv.en());
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
