package net.silnote.joker.jokerdemo.service;

import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptors;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.services.AdminInterface;
import io.grpc.xds.CsdsService;
import io.grpc.xds.XdsServerBuilder;
import io.grpc.xds.XdsServerCredentials;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.TracingServerInterceptor;
import io.opentracing.util.GlobalTracer;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringServerInterceptor;
import net.silnote.joker.jokerdemo.controller.HostnameGreeter;
import net.silnote.joker.jokerdemo.logging.LoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class XdsServer {
    @Autowired
    private CollectorRegistry collectorRegistry;
    @Autowired
    private Tracer jaegerTracer;
    @Value("${xds.server.credentials.enabled}")
    private String credentialEnabled;

    @Value("${xds.server.port}")
    private String sPort;

    @Value("${xds.server.hostname}")
    private String hostname;

    public void runServer() throws IOException, InterruptedException {
        int port = Integer.parseInt(sPort);
        ServerCredentials credentials = InsecureServerCredentials.create();

        if (credentialEnabled.equals("true")) {
            // The xDS credentials use the security configured by the xDS server when available. When xDS
            // is not used or when xDS does not provide security configuration, the xDS credentials fall
            // back to other credentials (in this case, InsecureServerCredentials).
            credentials = XdsServerCredentials.create(InsecureServerCredentials.create());
        }

        // Since the main server may be using TLS, we start a second server just for plaintext health
        // checks
        int healthPort = port + 1;
        log.info("Port " + port + " Hostname " + hostname);

        TracingServerInterceptor tracingInterceptor = TracingServerInterceptor
                .newBuilder()
                .withTracer(jaegerTracer)
                .build();
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        MonitoringServerInterceptor monitoringInterceptor = MonitoringServerInterceptor.create(Configuration.allMetrics()
                .withCollectorRegistry(collectorRegistry));

        final Server server = XdsServerBuilder.forPort(port, credentials)
                .addService(ServerInterceptors.intercept(
                                new HostnameGreeter(hostname), monitoringInterceptor, loggingInterceptor, tracingInterceptor))
                .addService(ProtoReflectionService.newInstance()) // convenient for command line tools
                .addServices(AdminInterface.getStandardServices())
                .build()
                .start();

        log.info("Listening on port " + port);
        log.info("Plaintext health service listening on port " + healthPort);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Start graceful shutdown
                server.shutdown();
                try {
                    // Wait for RPCs to complete processing
                    if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                        // That was plenty of time. Let's cancel the remaining RPCs
                        server.shutdownNow();
                        // shutdownNow isn't instantaneous, so give a bit of time to clean resources up
                        // gracefully. Normally this will be well under a second.
                        server.awaitTermination(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException ex) {
                    server.shutdownNow();
                }
            }
        });
        // This would normally be tied to the service's dependencies. For example, if HostnameGreeter
        // used a Channel to contact a required service, then when 'channel.getState() ==
        // TRANSIENT_FAILURE' we'd want to set NOT_SERVING. But HostnameGreeter has no dependencies, so
        // hard-coding SERVING is appropriate.
        server.awaitTermination();
    }
}
