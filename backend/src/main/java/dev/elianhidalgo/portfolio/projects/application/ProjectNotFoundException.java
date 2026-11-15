package dev.elianhidalgo.portfolio.projects.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered by Spring MVC as a 404 ProblemDetail; unpublished projects count as missing. */
public class ProjectNotFoundException extends ErrorResponseException {

    public ProjectNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Project not found"),
                null);
    }
}
