package dev.elianhidalgo.portfolio.profile.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered by Spring MVC as a 404 ProblemDetail. */
public class ProfileNotFoundException extends ErrorResponseException {

    public ProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Profile has not been created yet"),
                null);
    }
}
