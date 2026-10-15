package com.dmp.riskdecision.component.variable.service.impl;

import com.dmp.riskdecision.component.variable.mapper.VariableVersionMapper;
import com.dmp.riskdecision.component.variable.mapper.VariableVersionRow;
import com.dmp.riskdecision.component.variable.model.VariablePage;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.HttpVariableSource;
import com.dmp.riskdecision.component.variable.service.VariableService;
import jakarta.annotation.Resource;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 在当前项目内保存变量版本，固定来源配置并对外提供强类型取值。 */
@Service
public class VariableServiceImpl implements VariableService {
    @Resource private VariableVersionMapper mapper;
    @Resource private HttpVariableSource httpSource;
    @Value("${variable.allowed-hosts:localhost,127.0.0.1}") private String allowedHosts;
    private final ObjectMapper json = new ObjectMapper();

    /** {@inheritDoc} 创建首个不可变版本，单实例内串行避免重复码。 */
    @Override
    public synchronized VariableVersion create(String code, String name, VariableValueType valueType,
                                               String sourceType, String url, int timeoutMs,
                                               List<String> parameters, String resultPath) {
        validate(code, name, valueType, sourceType, url, timeoutMs, parameters, resultPath);
        if (mapper.current(code) != null) throw new IllegalStateException("Variable already exists");
        return insert(code, name, 1, valueType, sourceType, url, timeoutMs, parameters, resultPath);
    }

    /** {@inheritDoc} 仅预期当前版本匹配时创建下一个不可变版本。 */
    @Override
    public synchronized VariableVersion update(String code, long expectedVersion, String name,
                                               VariableValueType valueType, String sourceType, String url,
                                               int timeoutMs, List<String> parameters, String resultPath) {
        validate(code, name, valueType, sourceType, url, timeoutMs, parameters, resultPath);
        VariableVersion current = current(code);
        if (current.getVersionNumber() != expectedVersion) throw new IllegalStateException("Variable version conflict");
        return insert(code, name, expectedVersion + 1, valueType, sourceType, url, timeoutMs,
                parameters, resultPath);
    }

    /** {@inheritDoc} */
    @Override
    public VariableVersion current(String code) {
        VariableVersionRow row = mapper.current(code);
        if (row == null) throw new NoSuchElementException("Variable not found: " + code);
        return convert(row);
    }

    /** {@inheritDoc} */
    @Override
    public VariablePage search(String keyword, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        String pattern = "%" + (keyword == null ? "" : keyword.trim().replace("%", "\\%")) + "%";
        return new VariablePage(mapper.search(pattern, (long) (page - 1) * pageSize, pageSize)
                .stream().map(this::convert).toList(), mapper.count(pattern), page, pageSize);
    }

    /** {@inheritDoc} */
    @Override
    public VariableVersion requireVersion(long id) {
        VariableVersionRow row = mapper.selectById(id);
        if (row == null) throw new NoSuchElementException("Variable version not found: " + id);
        return convert(row);
    }

    /** {@inheritDoc} 旧流程继续取发布时选中的不可变版本。 */
    @Override
    public Object resolve(long versionId, String code, Map<String, Object> parameters) {
        VariableVersion version = requireVersion(versionId);
        if (!version.getVariableCode().equals(code)) throw new IllegalArgumentException("Variable version mismatch");
        return httpSource.fetch(version, parameters);
    }

    private VariableVersion insert(String code, String name, long version, VariableValueType valueType,
                                   String sourceType, String url, int timeoutMs, List<String> parameters,
                                   String resultPath) {
        VariableVersionRow row = new VariableVersionRow();
        row.setVariableCode(code);
        row.setVariableName(name.trim());
        row.setVersionNumber(version);
        row.setValueType(valueType.name());
        row.setSourceType(sourceType);
        row.setUrl(url);
        row.setTimeoutMs(timeoutMs);
        row.setParameterNamesJson(json.writeValueAsString(parameters));
        row.setResultPath(resultPath);
        row.setIsactive(1);
        try { mapper.insert(row); }
        catch (DuplicateKeyException conflict) {
            throw new IllegalStateException("Variable version conflict", conflict);
        }
        return convert(row);
    }

    private VariableVersion convert(VariableVersionRow row) {
        @SuppressWarnings("unchecked") List<String> parameters = json.readValue(row.getParameterNamesJson(), List.class);
        return new VariableVersion(row.getId(), row.getVariableCode(), row.getVariableName(),
                row.getVersionNumber(), VariableValueType.valueOf(row.getValueType()), row.getSourceType(),
                row.getUrl(), row.getTimeoutMs(), List.copyOf(parameters), row.getResultPath());
    }

    /** 第一版只接受允许主机的固定 GET 地址和明确的 JSON 字段路径。 */
    private void validate(String code, String name, VariableValueType valueType, String sourceType,
                          String url, int timeoutMs, List<String> parameters, String resultPath) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                || name == null || name.isBlank() || name.length() > 128
                || valueType == null || !"HTTP_GET".equals(sourceType)
                || timeoutMs < 100 || timeoutMs > 10000 || parameters == null || parameters.size() > 20
                || url == null || url.length() > 512
                || resultPath == null || resultPath.length() > 128
                || !resultPath.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,10}")) {
            throw new IllegalArgumentException("Invalid variable definition");
        }
        URI uri;
        try { uri = URI.create(url); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Invalid variable source URL", error); }
        Set<String> hosts = Set.of(allowedHosts.split(","));
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getHost() == null || !hosts.contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Variable source host is not allowed");
        }
        Set<String> unique = new HashSet<>();
        for (String namePart : parameters) {
            if (namePart == null || !namePart.matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                    || !unique.add(namePart)) throw new IllegalArgumentException("Invalid variable parameter name");
        }
    }
}
