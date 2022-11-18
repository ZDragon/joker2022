package net.silnote.joker.jokerdemo.service;

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.xds.XdsChannelCredentials;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.core.v3.Node;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusDiscoveryServiceGrpc;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusRequest;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusResponse;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.TracingClientInterceptor;
import io.opentracing.contrib.grpc.TracingServerInterceptor;
import io.opentracing.util.GlobalTracer;
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
public class XdsClient {
    @Autowired
    ApplicationArguments appArgs;
    @Autowired
    private CollectorRegistry collectorRegistry;
    @Autowired
    private Tracer jaegerTracer;

    @Value("${xds.client.credentials.enabled}")
    private String credentialEnabled;

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

        ChannelCredentials credentials = InsecureChannelCredentials.create();
        if (credentialEnabled.equals("true")) {
            // The xDS credentials use the security configured by the xDS server when available. When
            // xDS is not used or when xDS does not provide security configuration, the xDS credentials
            // fall back to other credentials (in this case, InsecureChannelCredentials).
            credentials = XdsChannelCredentials.create(InsecureChannelCredentials.create());
        }
        channel = Grpc.newChannelBuilder(clientTarget, credentials)
                .build();
    }

    public String sendRequest(String name, String target) throws InterruptedException {
        if (appArgs.containsOption("--name")) {
            name = appArgs.getOptionValues("--name").get(0);
        }

        if (appArgs.containsOption("--target")) {
            target = appArgs.getOptionValues("--target").get(0);
        }
        target = "xds:///" + target;

        blockingStub = GreeterGrpc.newBlockingStub(channel).withInterceptors(monitoringInterceptor, tracingInterceptor);

        String response = "";

        try {
            log.warn("Will try to greet {} from {} ...", name, target);
            response = greet(name);
        } catch (Exception ex) {}

        return response;
    }

    public String getDump(String target) throws InterruptedException {
        ChannelCredentials credentials = InsecureChannelCredentials.create();
        if (credentialEnabled.equals("true")) {
            // The xDS credentials use the security configured by the xDS server when available. When
            // xDS is not used or when xDS does not provide security configuration, the xDS credentials
            // fall back to other credentials (in this case, InsecureChannelCredentials).
            credentials = XdsChannelCredentials.create(InsecureChannelCredentials.create());
        }

        if (appArgs.containsOption("--target")) {
            target = appArgs.getOptionValues("--target").get(0);
        }
        target = "xds:///" + target;
        // This uses the new ChannelCredentials API. Grpc.newChannelBuilder() is the same as
        // ManagedChannelBuilder.forTarget(), except that it is passed credentials. When using this API,
        // you don't use methods like `managedChannelBuilder.usePlaintext()`, as that configuration is
        // provided by the ChannelCredentials.
        ManagedChannel channel = Grpc.newChannelBuilder(target, credentials)
                .build();
        ClientStatusDiscoveryServiceGrpc.ClientStatusDiscoveryServiceBlockingStub blockingStub =
                ClientStatusDiscoveryServiceGrpc.newBlockingStub(channel).withInterceptors(monitoringInterceptor, tracingInterceptor);

        String response = "";

        try {
            log.warn("Will try to dump from {} ...", target);
            ClientStatusRequest request = ClientStatusRequest.newBuilder().build();
            ClientStatusResponse reply;
            try {
                reply = blockingStub.fetchClientStatus(request);
                response = reply.toString();
            } catch (StatusRuntimeException e) {
                log.warn("RPC failed: " + e.getStatus());
                response = e.getMessage();
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }

        return response;
    }
}
