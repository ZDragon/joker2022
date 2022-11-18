package net.silnote.joker.jokerdemo.service;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class Initializer {

    final
    Environment env;

    public Initializer(Environment env,
                       SimpleService simpleService, XdsClient xdsClient, BaseClient baseClient, XdsServer xdsServer) {
        this.env = env;

        if(env.acceptsProfiles(Profiles.of("server"))) {
            new Thread(() -> {
                try {
                    xdsServer.runServer();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        } else if (env.acceptsProfiles(Profiles.of("xdsclient"))) {
            xdsClient.startup();
        } else if (env.acceptsProfiles(Profiles.of("baseclient"))) {
            baseClient.startup();
        } else if (env.acceptsProfiles(Profiles.of("simple"))) {
            new Thread(() -> {
                try {
                    simpleService.runServer();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

}
