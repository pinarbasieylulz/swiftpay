package com.swiftpay.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * When TLS is enabled, opens a second, plaintext Tomcat connector that does nothing but
 * redirect every request to the HTTPS port — there is no route to the API itself over HTTP.
 */
@Configuration
public class TlsConfig {

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${swiftpay.security.http-redirect-port:8080}")
    private int httpRedirectPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpToHttpsRedirect() {
        return factory -> {
            if (!sslEnabled) {
                return;
            }
            Connector redirectConnector = new Connector(org.apache.coyote.http11.Http11NioProtocol.class.getName());
            redirectConnector.setScheme("http");
            redirectConnector.setPort(httpRedirectPort);
            redirectConnector.setSecure(false);
            redirectConnector.setRedirectPort(factory.getPort());
            factory.addAdditionalTomcatConnectors(redirectConnector);

            factory.addContextCustomizers(context -> {
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                constraint.addCollection(collection);
                Context ctx = context;
                ctx.addConstraint(constraint);
            });
        };
    }
}
