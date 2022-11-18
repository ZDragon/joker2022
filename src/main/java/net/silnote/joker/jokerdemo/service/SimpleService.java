package net.silnote.joker.jokerdemo.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.services.AdminInterface;
import io.grpc.xds.CsdsService;
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

@Component
@Slf4j
public class SimpleService {

    @Autowired
    private CollectorRegistry collectorRegistry;
    @Autowired
    private Tracer jaegerTracer;
    @Value("${xds.server.port}")
    private String sPort;

    @Value("${xds.server.hostname}")
    private String hostname;

    private Server server;

    /** Start serving requests. */
    public void start() throws IOException {
        int port = Integer.parseInt(sPort);

        TracingServerInterceptor tracingInterceptor = TracingServerInterceptor
                .newBuilder()
                .withTracer(jaegerTracer)
                .build();
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        MonitoringServerInterceptor monitoringInterceptor = MonitoringServerInterceptor.create(Configuration.allMetrics()
                .withCollectorRegistry(collectorRegistry));

        server = ServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(
                        new HostnameGreeter(hostname), monitoringInterceptor, loggingInterceptor, tracingInterceptor))
                .addService(ProtoReflectionService.newInstance()) // convenient for command line tools
                .addServices(AdminInterface.getStandardServices())
                .build();
        server.start();

        log.info("Server started, listening on " + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                log.error("*** shutting down gRPC server since JVM is shutting down");
                try {
                    SimpleService.this.stop();
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                    e.printStackTrace(System.err);
                }
                log.error("*** server shut down");
            }
        });
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public void runServer() throws IOException, InterruptedException {
        start();
        blockUntilShutdown();
    }
}
