package dev.elianhidalgo.portfolio.shared;

/** Regular expressions for text fields; PostgreSQL rejects NUL, and control characters have no place in a title. */
public final class TextRules {

    /** No control characters at all. */
    public static final String NO_CONTROL = "[^\\p{Cntrl}]*";

    /** Control characters are refused except tab, line feed and carriage return. */
    public static final String NO_CONTROL_EXCEPT_WHITESPACE = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*";

    private TextRules() {
    }
}
