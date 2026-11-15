package dev.elianhidalgo.portfolio.shared.web;

import java.io.IOException;
import java.io.Writer;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.HttpStatus;

/**
 * Tomcat answers some malformed requests (e.g. an encoded slash in the path) before Spring
 * sees them, with an HTML page that reveals the server. This valve writes the same minimal
 * problem JSON as the rest of the API instead — no server name, no stack trace.
 */
class ProblemJsonErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int status = response.getStatus();
        if (status < 400 || response.getContentWritten() > 0) {
            return;
        }
        HttpStatus httpStatus = HttpStatus.resolve(status);
        String title = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";
        try {
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException ignored) {
            // The client is gone or the response was committed; nothing more to report.
        }
    }
}
