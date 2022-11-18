package net.silnote.joker.jokerdemo.http;

import net.silnote.joker.jokerdemo.service.BaseClient;
import net.silnote.joker.jokerdemo.service.XdsClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ApiController {

    private final XdsClient xdsClient;
    private final BaseClient baseClient;

    public ApiController(XdsClient xdsClient, BaseClient baseClient) {
        this.xdsClient = xdsClient;
        this.baseClient = baseClient;
    }

    @GetMapping("/xds/{server}/{name}")
    @ResponseBody
    private String sendGRPCRequest(@PathVariable String name, @PathVariable String server) throws InterruptedException {
        return xdsClient.sendRequest(name, server);
    }

    @GetMapping("/base/{server}/{name}")
    @ResponseBody
    private String baseGRPCRequest(@PathVariable String name, @PathVariable String server) throws InterruptedException {
        return baseClient.sendRequest(name, server);
    }

    @GetMapping("/dump/{server}")
    @ResponseBody
    private String getDump(@PathVariable String server) throws InterruptedException {
        return xdsClient.getDump(server);
    }
}
