package dev.elianhidalgo.portfolio.contact.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The contact form as sent by the browser. Values are trimmed before validation, so the
 * limits apply to what is stored. Control characters are refused (PostgreSQL rejects NUL);
 * the message may contain tabs and line breaks.
 *
 * @param website honeypot: hidden from people, so only bots fill it in
 */
public record ContactRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]*") String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 2000) @Pattern(regexp = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*") String message,
        @NotNull @Pattern(regexp = "es|en") String locale,
        @Size(max = 200) String website) {

    public ContactRequest {
        name = strip(name);
        email = strip(email);
        message = strip(message);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    boolean isFromBot() {
        return website != null && !website.isBlank();
    }
}
