package dev.elianhidalgo.portfolio.shared.web;

import org.apache.catalina.Host;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/** Installs {@link ProblemJsonErrorReportValve} in place of Tomcat's HTML error report. */
@Component
class TomcatErrorReportCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // Tomcat only adds its default ErrorReportValve when the host has none of that class.
        factory.addContextCustomizers(context -> {
            Host host = (Host) context.getParent();
            host.getPipeline().addValve(new ProblemJsonErrorReportValve());
            if (host instanceof org.apache.catalina.core.StandardHost standardHost) {
                standardHost.setErrorReportValveClass(ProblemJsonErrorReportValve.class.getName());
            }
        });
    }
}
