package net.silnote.joker.jokerdemo.logging;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "grpc")
public class LoggingInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
        String serviceName = MethodDescriptor.extractFullServiceName(methodDescriptor.getFullMethodName());
        String methodName = methodDescriptor.getFullMethodName()
                .substring((serviceName != null ? serviceName.length() : 0) + 1);
        log.info("[gRPC][{}][{}][{}] metadata {}", methodDescriptor.getType(), serviceName, methodName, headers);
        return next.startCall(call, headers);
    }
}
