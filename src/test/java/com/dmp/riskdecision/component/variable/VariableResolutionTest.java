package com.dmp.riskdecision.component.variable;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.component.variable.service.VariableResolutionException;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Resource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/** 缺字段、类型不符和来源失败不能降为默认值。 */
@SpringBootTest
class VariableResolutionTest {
    @Resource private VariableService variables;

    @Test
    void rejectsMissingPathWrongTypeAndRemoteError() throws Exception {
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/missing", exchange -> respond(exchange, 200, "{\"other\":12}"));
        remote.createContext("/wrong", exchange -> respond(exchange, 200, "{\"value\":\"12\"}"));
        remote.createContext("/failure", exchange -> respond(exchange, 503, "{}"));
        remote.start();
        try {
            for (String path : List.of("missing", "wrong", "failure")) {
                String code = path + System.nanoTime();
                VariableVersion version = variables.create(code, "错误样例", VariableValueType.DECIMAL,
                        "HTTP_GET", "http://localhost:" + remote.getAddress().getPort() + "/" + path,
                        1000, List.of("userId"), "value");
                assertThatThrownBy(() -> variables.resolve(version.getId(), code, Map.of("userId", "u1")))
                        .isInstanceOf(VariableResolutionException.class);
            }
        } finally { remote.stop(0); }
    }

    @Test
    void timesOutWhenRemoteStallsAfterResponseHeaders() throws Exception {
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/stalled", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            try { Thread.sleep(900); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        remote.start();
        try {
            String code = "stalled" + System.nanoTime();
            VariableVersion version = variables.create(code, "慢响应", VariableValueType.STRING,
                    "HTTP_GET", "http://localhost:" + remote.getAddress().getPort() + "/stalled",
                    100, List.of(), "value");
            long started = System.nanoTime();
            assertThatThrownBy(() -> variables.resolve(version.getId(), code, Map.of()))
                    .isInstanceOf(VariableResolutionException.class);
            assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    .isLessThan(600);
        } finally { remote.stop(0); }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
