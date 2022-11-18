Demo
================

Cluster Kubernetes
Namespaces: joker-demo-proxyless, joker-demo-proxy

### Build the example

Just run:
```
$ ./gradlew build
```

Then:
```
docker build . -t joker-demo:VERSION
docker tag joker-demo:VERSION your-repo/joker-demo:VERSION
docker push your-repo/joker-demo:VERSION
```

Then:
Fix yaml files set new image version

### Deploy to Andronus

#### Service 1
Created NS, SA, PullSecret, Deployment and service for namespace joker-demo-proxyless
```
kubectl --kubeconfig path/to/kubeconfig apply -f deploy/srv1/all.yaml
```

#### Service 2
Created Deployment V2, DestinationRule and VirtualService for namespace joker-demo-proxyless
Traffic distribution weight 20/80 (v1/v2)
```
kubectl --kubeconfig path/to/kubeconfig apply -f deploy/srv2/all.yaml
```

#### Service 3
Created NS, SA, PullSecret, Deployment and service for namespace joker-demo-proxy
Just simple service mesh item. It's NOT a XDS player.
```
kubectl --kubeconfig path/to/kubeconfig apply -f deploy/srv3/all.yaml
```

### Check
attach to pod in NS joker-demo-proxyless or joker-demo-proxy
```
kubectl exec -ti deploy/xds-client -n joker-demo-proxyless --kubeconfig path/to/kubeconfig -- bash
curl http://localhost:8081/xds/myxds.joker-demo-proxyless.svc.cluster.local:7070/JOKER
curl http://localhost:8081/xds/myxds.joker-demo-proxy.svc.cluster.local:7070/JOKER
```
Next you get two util for request:
#### GRPCurl
Request to V1/V2
```
/root/GoProjects/bin/grpcurl -plaintext -d '{"name": "JOKER"}' myxds:7070 helloworld.Greeter/SayHello
```
Request to V3
```
/root/GoProjects/bin/grpcurl -plaintext -d '{"name": "JOKER"}' myxds.joker-demo-proxy:7070 helloworld.Greeter/SayHello
```

Get Dump
```
/root/GoProjects/bin/grpcurl -plaintext -d '' myxds.joker-demo-proxyless:7070 envoy.service.status.v3.ClientStatusDiscoveryService/FetchClientStatus
/root/GoProjects/bin/grpcurl -plaintext -d '' myxds.joker-demo-proxy:7070 envoy.service.status.v3.ClientStatusDiscoveryService/FetchClientStatus
```

Get ChannelZ
```
/root/GoProjects/bin/grpcurl -plaintext -d '' myxds.joker-demo-proxyless:7070 grpc.channelz.v1.Channelz/GetServers
/root/GoProjects/bin/grpcurl -plaintext -d '' myxds.joker-demo-proxy:7070 grpc.channelz.v1.Channelz/GetTopChannels
```

### Undeploy

#### Service 1
DELETE NS, SA, PullSecret, Deployment and service for namespace joker-demo-one
```
kubectl --kubeconfig path/to/kubeconfig delete -f deploy/srv1/all.yaml
```

#### Service 2
DELETE Deployment V2, DestinationRule and VirtualService for namespace joker-demo-one
Traffic distribution weight 20/80 (v1/v2)
```
kubectl --kubeconfig path/to/kubeconfig delete -f deploy/srv2/all.yaml
```

#### Service 3
DELETE NS, SA, PullSecret, Deployment and service for namespace joker-demo-two
Just simple service mesh item. It's NOT a XDS player.
```
kubectl --kubeconfig path/to/kubeconfig delete -f deploy/srv3/all.yaml
```
