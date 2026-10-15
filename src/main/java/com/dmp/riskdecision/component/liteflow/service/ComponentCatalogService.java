package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/** 对业务模块提供可编排组件的查找能力。 */
@Service
public class ComponentCatalogService {
    @Resource
    private ComponentRegistry registry;

    public ComponentCatalogService() {
    }

    public ComponentCatalogService(ComponentRegistry registry) {
        this.registry = registry;
    }

    public ComponentDefinition require(String componentKey) {
        return registry.require(componentKey);
    }

    /** 返回当前平台可选择的组件及其配置字段契约。 */
    public java.util.List<ComponentDefinition> list() {
        return registry.list();
    }

    /**
     * 对当前已注册的固定组件目录按名称、标识和节点类型筛选并分页。
     * 目录目前由代码注册，故分页从内存目录计算，不访问业务数据库。
     *
     * @param keyword 组件标识或展示名，空值表示不筛选
     * @param nodeKind COMPONENT、CONDITION 或空值
     * @param category DATA、CONDITION、RULE 或空值
     * @param page 从 1 开始的页码
     * @param pageSize 每页 1–100 条
     * @return 当前页与总数
     */
    public ComponentCatalogPage search(String keyword, String nodeKind, String category, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid component pagination");
        }
        String kind = nodeKind == null ? "" : nodeKind.trim();
        if (!kind.isEmpty() && !"COMPONENT".equals(kind) && !"CONDITION".equals(kind)) {
            throw new IllegalArgumentException("Invalid component nodeKind");
        }
        String group = category == null ? "" : category.trim();
        if (!group.isEmpty() && !java.util.Set.of("DATA", "CONDITION", "RULE").contains(group)) {
            throw new IllegalArgumentException("Invalid component category");
        }
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        List<ComponentDefinition> matched = registry.list().stream()
                .filter(item -> kind.isEmpty() || item.isCondition() == "CONDITION".equals(kind))
                .filter(item -> group.isEmpty() || group.equals(item.getCategory()))
                .filter(item -> item.getKey().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || item.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .toList();
        long from = (long) (page - 1) * pageSize;
        int start = (int) Math.min(from, matched.size());
        int end = Math.min(start + pageSize, matched.size());
        return new ComponentCatalogPage(matched.subList(start, end), matched.size(), page, pageSize);
    }
}
