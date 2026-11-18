package dev.elianhidalgo.portfolio.profile.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * A link the site renders as an href or src: an absolute https URL or, when {@code allowPath}
 * is set, a plain relative path such as {@code cv/cv-es.pdf}. Everything else (other schemes,
 * protocol-relative {@code //host}, absolute or {@code ..} paths) is refused. Null is valid.
 */
@Documented
@Constraint(validatedBy = SafeLink.Validator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@interface SafeLink {

    String message() default "must be an https link or a relative path";

    boolean allowPath() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SafeLink, String> {

        private static final Pattern PATH = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~/-]*");

        private boolean allowPath;

        @Override
        public void initialize(SafeLink constraint) {
            this.allowPath = constraint.allowPath();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                return true;
            }
            if (PATH.matcher(value).matches()) {
                return allowPath && !value.contains("..");
            }
            return isHttps(value);
        }

        private static boolean isHttps(String value) {
            try {
                URI uri = new URI(value);
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && uri.getUserInfo() == null;
            } catch (URISyntaxException e) {
                return false;
            }
        }
    }
}
