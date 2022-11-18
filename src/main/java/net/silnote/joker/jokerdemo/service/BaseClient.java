package net.silnote.joker.jokerdemo.service;

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.xds.XdsChannelCredentials;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusDiscoveryServiceGrpc;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusRequest;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusResponse;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.TracingClientInterceptor;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringClientInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class BaseClient {
    @Autowired
    ApplicationArguments appArgs;
    @Autowired
    private CollectorRegistry collectorRegistry;
    @Autowired
    private Tracer jaegerTracer;

    @Value("${xds.client.target}")
    private String clientTarget;

    private GreeterGrpc.GreeterBlockingStub blockingStub;
    private MonitoringClientInterceptor monitoringInterceptor;
    private TracingClientInterceptor tracingInterceptor;
    private ManagedChannel channel;

    /** Say hello to server. */
    public String greet(String name) {
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: " + e.getStatus());
            return e.getMessage();
        }
        log.warn("Greeting: " + response.getMessage());
        return response.getMessage();
    }

    public void startup() {
        monitoringInterceptor =
                MonitoringClientInterceptor.create(Configuration.cheapMetricsOnly().withCollectorRegistry(collectorRegistry));
        tracingInterceptor = TracingClientInterceptor
                .newBuilder()
                .withTracer(jaegerTracer)
                .build();

        var str = clientTarget.split(":");
        var host = str[0];
        var port = str[1];

        channel = ManagedChannelBuilder.forAddress(host, Integer.parseInt(port)).usePlaintext().build();
    }

    public String sendRequest(String name, String target) throws InterruptedException {
        if (appArgs.containsOption("--name")) {
            name = appArgs.getOptionValues("--name").get(0);
        }

        blockingStub = GreeterGrpc.newBlockingStub(channel).withInterceptors(monitoringInterceptor, tracingInterceptor);

        String response = "";

        try {
            log.warn("Will try to greet {} from {} ...", name, target);
            response = greet(name);
        } catch (Exception ex) {}

        return response;
    }
}
