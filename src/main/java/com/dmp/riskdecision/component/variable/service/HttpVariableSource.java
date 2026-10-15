package com.dmp.riskdecision.component.variable.service;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 从已登记的只读 HTTP 来源提取一个声明类型的变量值。 */
@Service
public class HttpVariableSource {
    private static final int MAX_RESPONSE_BYTES = 65536;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    /** 仅发送 GET；任何来源错误、空值或类型不符都使本次取值失败。 */
    public Object fetch(VariableVersion version, Map<String, Object> parameters) {
        if (!parameters.keySet().equals(new HashSet<>(version.getParameterNames()))) {
            throw new IllegalArgumentException("Variable parameters do not match the definition");
        }
        StringJoiner query = new StringJoiner("&");
        for (String name : version.getParameterNames()) {
            Object value = parameters.get(name);
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("Variable parameter must be scalar: " + name);
            }
            query.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
        }
        URI uri = URI.create(version.getUrl() + (query.length() == 0 ? "" : "?" + query));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(version.getTimeoutMs()))
                .GET().build();
        byte[] bytes = readWithinDeadline(request, version.getTimeoutMs());
        JsonNode value;
        try { value = json.readTree(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException error) { throw new VariableResolutionException("Variable response is not valid JSON", error); }
        if (value == null || !value.isObject()) {
            throw new VariableResolutionException("Variable response must be a JSON object");
        }
        for (String part : version.getResultPath().split("\\.")) {
            if (!value.isObject()) throw new VariableResolutionException("Variable result path is missing");
            value = value.get(part);
            if (value == null) throw new VariableResolutionException("Variable result path is missing");
        }
        if (value.isNull()) throw new VariableResolutionException("Variable result is missing");
        return typedValue(version.getValueType(), value);
    }

    /** 将响应头、响应体读取共同限制在一次完整请求的超时内。 */
    private byte[] readWithinDeadline(HttpRequest request, int timeoutMs) {
        AtomicReference<InputStream> activeBody = new AtomicReference<>();
        ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
        Future<byte[]> pending = readers.submit(() -> {
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    activeBody.set(body);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new VariableResolutionException("Variable HTTP source returned " + response.statusCode());
                    }
                    byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) {
                        throw new VariableResolutionException("Variable response is too large");
                    }
                    return bytes;
                } finally { activeBody.set(null); }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new VariableResolutionException("Variable HTTP source interrupted", error);
            } catch (java.io.IOException error) {
                throw new VariableResolutionException("Variable HTTP source failed", error);
            }
        });
        try {
            return pending.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            pending.cancel(true);
            closeQuietly(activeBody.get());
            throw new VariableResolutionException("Variable HTTP source timed out", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            closeQuietly(activeBody.get());
            throw new VariableResolutionException("Variable HTTP source interrupted", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof VariableResolutionException) throw (VariableResolutionException) cause;
            throw new VariableResolutionException("Variable HTTP source failed", cause);
        } finally { readers.shutdownNow(); }
    }

    private static void closeQuietly(InputStream body) {
        if (body == null) return;
        try { body.close(); } catch (java.io.IOException ignored) { /* 请求已失败。 */ }
    }

    private static Object typedValue(VariableValueType type, JsonNode value) {
        if (type == VariableValueType.STRING && value.isTextual()) return value.asText();
        if (type == VariableValueType.BOOLEAN && value.isBoolean()) return value.booleanValue();
        if (type == VariableValueType.DECIMAL && value.isNumber()) {
            BigDecimal decimal = value.decimalValue();
            return decimal;
        }
        throw new VariableResolutionException("Variable result type does not match definition");
    }
}
