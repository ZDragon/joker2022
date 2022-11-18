FROM ubuntu

RUN apt-get update  \
    && apt-get install -y golang jq ca-certificates openjdk-11-jdk curl \
    && update-ca-certificates 2>/dev/null \
    && mkdir -p $HOME/GoProjects/src -p $HOME/GoProjects/pkg -p $HOME/GoProjects/bin \
    && export PATH=$PATH:/usr/local/go/bin:$HOME/GoProjects/bin \
    && export GOPATH=$HOME/GoProjects \
    && go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest


WORKDIR /example-xds
ADD build/libs/joker-demo-0.0.1-SNAPSHOT.jar /example-xds
RUN chmod +rx joker-demo-0.0.1-SNAPSHOT.jar

ENTRYPOINT ["java", "-jar", "joker-demo-0.0.1-SNAPSHOT.jar"]