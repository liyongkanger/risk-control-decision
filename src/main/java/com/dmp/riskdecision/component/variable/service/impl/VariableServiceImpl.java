package com.dmp.riskdecision.component.variable.service.impl;

import com.dmp.riskdecision.component.variable.mapper.VariableVersionMapper;
import com.dmp.riskdecision.component.variable.mapper.VariableVersionRow;
import com.dmp.riskdecision.component.variable.model.VariablePage;
import com.dmp.riskdecision.component.variable.model.VariableCategory;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableSourceRegistry;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.component.variable.service.VariableResolutionException;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import jakarta.annotation.Resource;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import tools.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 在当前项目内保存变量版本，固定来源配置并对外提供强类型取值。 */
@Service
public class VariableServiceImpl implements VariableService {
    @Resource private VariableVersionMapper mapper;
    @Resource private VariableSourceRegistry sources;
    @Value("${variable.allowed-hosts:localhost,127.0.0.1}") private String allowedHosts;
    private final ObjectMapper json = new ObjectMapper();

    /** {@inheritDoc} 创建首个不可变版本，单实例内串行避免重复码。 */
    @Override
    @Transactional
    public synchronized VariableVersion create(String code, String name, VariableValueType valueType,
                                               String sourceType, String url, int timeoutMs,
                                               List<String> parameters, String resultPath) {
        return create(code, name, valueType, sourceType, url, timeoutMs, parameters, resultPath, Map.of());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public synchronized VariableVersion create(String code, String name, VariableValueType valueType,
                                               String sourceType, String url, int timeoutMs,
                                               List<String> parameters, String resultPath,
                                               Map<String, Long> dependencyVersionIds) {
        return create(code, name, valueType, sourceType, url, timeoutMs, parameters, resultPath,
                dependencyVersionIds, null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public synchronized VariableVersion create(String code, String name, VariableValueType valueType,
                                               String sourceType, String url, int timeoutMs,
                                               List<String> parameters, String resultPath,
                                               Map<String, Long> dependencyVersionIds, String categoryCode) {
        VariableCategory category = VariableCategory.parse(categoryCode);
        validate(code, name, valueType, sourceType, url, timeoutMs, parameters, resultPath,
                dependencyVersionIds);
        if (mapper.current(code) != null) throw new IllegalStateException("Variable already exists");
        VariableVersion created = insert(code, name, 1, valueType, sourceType, url, timeoutMs, parameters,
                resultPath, dependencyVersionIds);
        mapper.insertCategory(code, category.name());
        created.setCategoryCode(category.name());
        return created;
    }

    /** {@inheritDoc} 仅预期当前版本匹配时创建下一个不可变版本。 */
    @Override
    public synchronized VariableVersion update(String code, long expectedVersion, String name,
                                               VariableValueType valueType, String sourceType, String url,
                                               int timeoutMs, List<String> parameters, String resultPath) {
        return update(code, expectedVersion, name, valueType, sourceType, url, timeoutMs,
                parameters, resultPath, Map.of());
    }

    /** {@inheritDoc} */
    @Override
    public synchronized VariableVersion update(String code, long expectedVersion, String name,
                                               VariableValueType valueType, String sourceType, String url,
                                               int timeoutMs, List<String> parameters, String resultPath,
                                               Map<String, Long> dependencyVersionIds) {
        validate(code, name, valueType, sourceType, url, timeoutMs, parameters, resultPath,
                dependencyVersionIds);
        VariableVersion current = current(code);
        if (current.getVersionNumber() != expectedVersion) throw new IllegalStateException("Variable version conflict");
        insert(code, name, expectedVersion + 1, valueType, sourceType, url, timeoutMs,
                parameters, resultPath, dependencyVersionIds);
        return current(code);
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
        return search(keyword, null, null, null, page, pageSize);
    }

    /** {@inheritDoc} */
    @Override
    public VariablePage search(String keyword, String categoryCode, String sourceType, String valueType,
                               int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        String category = categoryCode == null || categoryCode.isBlank()
                ? null : VariableCategory.parse(categoryCode).name();
        String source = sourceType == null || sourceType.isBlank() ? null : sourceType;
        String type = valueType == null || valueType.isBlank() ? null : valueType;
        String pattern = "%" + (keyword == null ? "" : keyword.trim().replace("%", "\\%")) + "%";
        return new VariablePage(mapper.search(pattern, category, source, type,
                (long) (page - 1) * pageSize, pageSize).stream().map(this::convert).toList(),
                mapper.count(pattern, category, source, type), page, pageSize);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public VariableVersion updateCategory(String code, String categoryCode) {
        if (categoryCode == null) throw new IllegalArgumentException("Invalid variable category: null");
        VariableCategory category = VariableCategory.parse(categoryCode);
        current(code);
        if (mapper.updateCategory(code, category.name()) != 1) {
            throw new IllegalStateException("Variable category is missing: " + code);
        }
        return current(code);
    }

    /** {@inheritDoc} */
    @Override
    public VariableVersion requireVersion(long id) {
        VariableVersionRow row = mapper.version(id);
        if (row == null) throw new NoSuchElementException("Variable version not found: " + id);
        return convert(row);
    }

    /** {@inheritDoc} 旧流程继续取发布时选中的不可变版本。 */
    @Override
    public Object resolve(long versionId, String code, Map<String, Object> parameters) {
        VariableVersion version = requireVersion(versionId);
        if (!version.getVariableCode().equals(code)) throw new IllegalArgumentException("Variable version mismatch");
        if ("REQUEST_INPUT".equals(version.getSourceType()) || "FLOW_CONTEXT".equals(version.getSourceType())) {
            return resolveForTest(versionId, code, parameters, parameters);
        }
        return sources.require(version.getSourceType()).fetch(version, parameters);
    }

    /** {@inheritDoc} */
    @Override
    public Object resolveInFlow(long versionId, String code, Map<String, Object> requestInput,
                                Map<String, Object> nodeResults, Map<String, FlowContextBinding> bindings) {
        return resolveInFlow(versionId, code, requestInput, nodeResults, bindings, new HashMap<>());
    }

    /** {@inheritDoc} */
    @Override
    public Object resolveInFlow(long versionId, String code, Map<String, Object> requestInput,
                                Map<String, Object> nodeResults, Map<String, FlowContextBinding> bindings,
                                Map<Long, Object> cache) {
        return resolveRecursive(versionId, code, requestInput == null ? Map.of() : requestInput,
                nodeResults == null ? Map.of() : nodeResults, bindings == null ? Map.of() : bindings,
                Map.of(), new HashSet<>(), cache);
    }

    /** {@inheritDoc} */
    @Override
    public Object resolveForTest(long versionId, String code, Map<String, Object> requestInput,
                                 Map<String, Object> contextSamples) {
        return resolveForTest(versionId, code, requestInput, contextSamples, new HashMap<>());
    }

    /** {@inheritDoc} */
    @Override
    public Object resolveForTest(long versionId, String code, Map<String, Object> requestInput,
                                 Map<String, Object> contextSamples, Map<Long, Object> cache) {
        return resolveRecursive(versionId, code, requestInput == null ? Map.of() : requestInput,
                Map.of(), Map.of(), contextSamples == null ? Map.of() : contextSamples,
                new HashSet<>(), cache);
    }

    /** 沿不可变版本依赖图取值，活动集合检测环，同次调用缓存避免重复远程取值。 */
    private Object resolveRecursive(long versionId, String code, Map<String, Object> requestInput,
                                    Map<String, Object> nodeResults, Map<String, FlowContextBinding> bindings,
                                    Map<String, Object> contextSamples,
                                    Set<Long> active, Map<Long, Object> cache) {
        VariableVersion version = requireVersion(versionId);
        if (!version.getVariableCode().equals(code)) {
            throw new VariableResolutionException("Variable version mismatch: " + code);
        }
        if (cache.containsKey(versionId)) return cache.get(versionId);
        if (!active.add(versionId)) throw new VariableResolutionException("Variable dependency cycle: " + code);
        try {
            Object value;
            if ("REQUEST_INPUT".equals(version.getSourceType())) {
                value = requestInput.get(code);
            } else if ("FLOW_CONTEXT".equals(version.getSourceType())) {
                value = contextValue(version, nodeResults, bindings, contextSamples);
            } else {
                Map<String, Object> parameters = new HashMap<>();
                for (String name : version.getParameterNames()) {
                    Long dependencyId = version.getDependencyVersionIds().get(name);
                    if (dependencyId == null) {
                        throw new VariableResolutionException("Variable dependency version is missing: " + name);
                    }
                    parameters.put(name, resolveRecursive(dependencyId, name, requestInput,
                            nodeResults, bindings, contextSamples, active, cache));
                }
                value = sources.require(version.getSourceType()).fetch(version, parameters);
            }
            Object typed = typedValue(version, value);
            cache.put(versionId, typed);
            return typed;
        } finally { active.remove(versionId); }
    }

    /** 上下文绑定由流程图提供；独立试算允许直接以变量码提供临时样例。 */
    private Object contextValue(VariableVersion version, Map<String, Object> nodeResults,
                                Map<String, FlowContextBinding> bindings,
                                Map<String, Object> contextSamples) {
        String code = version.getVariableCode();
        FlowContextBinding binding = bindings.get(code);
        if (binding == null) {
            if (contextSamples.containsKey(code)) return contextSamples.get(code);
            throw new VariableResolutionException("Flow context variable has no binding: " + code);
        }
        if (!code.equals(binding.getVariableCode()) || version.getId() != binding.getVariableVersionId()) {
            throw new VariableResolutionException("Flow context binding version mismatch: " + code);
        }
        Object value = nodeResults.get(binding.getNodeId());
        String path = binding.getResultPath();
        if (path == null) throw new VariableResolutionException("Flow context path is missing: " + code);
        if (!path.isEmpty()) {
            for (String part : path.split("\\.")) {
                value = readProperty(value, part, code);
            }
        }
        return value;
    }

    /** 允许流程节点结果为 Map 或普通 Java 对象，缺少字段时保留变量码供定位。 */
    private Object readProperty(Object object, String property, String code) {
        if (object instanceof Map<?, ?>) return ((Map<?, ?>) object).get(property);
        if (object == null) throw new VariableResolutionException("Flow context path is missing: " + code);
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(object.getClass()).getPropertyDescriptors()) {
                if (descriptor.getName().equals(property) && descriptor.getReadMethod() != null) {
                    return descriptor.getReadMethod().invoke(object);
                }
            }
        } catch (IntrospectionException | ReflectiveOperationException error) {
            throw new VariableResolutionException("Flow context path is unreadable: " + code, error);
        }
        throw new VariableResolutionException("Flow context path is missing: " + code);
    }

    /** 统一校验本地与外部来源的强类型单值。 */
    private Object typedValue(VariableVersion version, Object value) {
        if (value == null) throw new VariableResolutionException("Variable value is missing: " + version.getVariableCode());
        if (version.getValueType() == VariableValueType.STRING && value instanceof String) return value;
        if (version.getValueType() == VariableValueType.BOOLEAN && value instanceof Boolean) return value;
        if (version.getValueType() == VariableValueType.DECIMAL && value instanceof Number) {
            return value instanceof BigDecimal ? value : new BigDecimal(value.toString());
        }
        if (version.getValueType() == VariableValueType.DECIMAL && value instanceof String
                && ((String) value).matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
            return new BigDecimal((String) value);
        }
        throw new VariableResolutionException("Variable value type mismatch: " + version.getVariableCode());
    }

    private VariableVersion insert(String code, String name, long version, VariableValueType valueType,
                                   String sourceType, String url, int timeoutMs, List<String> parameters,
                                   String resultPath, Map<String, Long> dependencyVersionIds) {
        VariableVersionRow row = new VariableVersionRow();
        row.setVariableCode(code);
        row.setVariableName(name.trim());
        row.setVersionNumber(version);
        row.setValueType(valueType.name());
        row.setSourceType(sourceType);
        row.setUrl(url);
        row.setTimeoutMs(timeoutMs);
        row.setParameterNamesJson(json.writeValueAsString(parameters == null ? List.of() : parameters));
        row.setDependencyVersionIdsJson(json.writeValueAsString(
                dependencyVersionIds == null ? Map.of() : dependencyVersionIds));
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
        Map<String, Long> dependencies = row.getDependencyVersionIdsJson() == null ? Map.of()
                : json.readValue(row.getDependencyVersionIdsJson(), new TypeReference<Map<String, Long>>() { });
        VariableVersion version = new VariableVersion(row.getId(), row.getVariableCode(), row.getVariableName(),
                row.getVersionNumber(), VariableValueType.valueOf(row.getValueType()), row.getSourceType(),
                row.getUrl(), row.getTimeoutMs(), List.copyOf(parameters), row.getResultPath(), Map.copyOf(dependencies));
        version.setCategoryCode(row.getCategoryCode() == null ? VariableCategory.OTHER.name() : row.getCategoryCode());
        return version;
    }

    /** 受控 HTTP 来源检查允许主机；演示来源交由已注册适配器核对固定定义。 */
    private void validate(String code, String name, VariableValueType valueType, String sourceType,
                          String url, int timeoutMs, List<String> parameters, String resultPath,
                          Map<String, Long> dependencyVersionIds) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                || name == null || name.isBlank() || name.length() > 128
                || valueType == null || sourceType == null
                || !Set.of("HTTP_GET", "DEMO_MARKETING", "REQUEST_INPUT", "FLOW_CONTEXT").contains(sourceType)) {
            throw new IllegalArgumentException("Invalid variable definition");
        }
        if (Set.of("REQUEST_INPUT", "FLOW_CONTEXT").contains(sourceType)) {
            if (parameters != null && !parameters.isEmpty() || dependencyVersionIds != null && !dependencyVersionIds.isEmpty()
                    || url != null && !url.isBlank() || resultPath != null && !resultPath.isBlank()) {
                throw new IllegalArgumentException("Local variable cannot define source parameters");
            }
            return;
        }
        if (timeoutMs < 100 || timeoutMs > 10000 || parameters == null || parameters.size() > 20
                || url == null || url.length() > 512 || resultPath == null || resultPath.length() > 128
                || !resultPath.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,10}")) {
            throw new IllegalArgumentException("Invalid variable definition");
        }
        Set<String> unique = new HashSet<>();
        for (String parameterName : parameters) {
            if (parameterName == null || !parameterName.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                    || !unique.add(parameterName)) {
                throw new IllegalArgumentException("Invalid variable parameter name");
            }
        }
        if (dependencyVersionIds == null || !dependencyVersionIds.keySet().equals(unique)) {
            throw new IllegalArgumentException("Variable dependency versions must match parameters");
        }
        for (String dependencyCode : parameters) {
            Long dependencyId = dependencyVersionIds.get(dependencyCode);
            if (dependencyCode.equals(code) || dependencyId == null || dependencyId < 1) {
                throw new IllegalArgumentException("Invalid variable dependency: " + dependencyCode);
            }
            VariableVersion dependency;
            try { dependency = requireVersion(dependencyId); }
            catch (NoSuchElementException missing) {
                throw new IllegalArgumentException("Invalid variable dependency: " + dependencyCode, missing);
            }
            if (!dependency.getVariableCode().equals(dependencyCode)) {
                throw new IllegalArgumentException("Invalid variable dependency: " + dependencyCode);
            }
        }
        if ("DEMO_MARKETING".equals(sourceType)) {
            sources.require(sourceType).validateDefinition(code, valueType, url, parameters, resultPath);
            return;
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
    }
}
