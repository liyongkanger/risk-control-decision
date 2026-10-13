package com.dmp.riskdecision.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证持久层使用 MyBatis-Plus，并把自定义 SQL 留在 XML 中。 */
class MapperConventionTest {
    private static final Set<String> SQL_ANNOTATIONS = Set.of(
            "Select", "Insert", "Update", "Delete", "SelectProvider", "InsertProvider",
            "UpdateProvider", "DeleteProvider", "Results", "ResultMap");

    @Test
    void sceneMapperProvidesStandardCrudThroughMyBatisPlus() {
        assertThat(BaseMapper.class.isAssignableFrom(SceneMapper.class)).isTrue();
        assertThat(BaseMapper.class.isAssignableFrom(FlowMapper.class)).isTrue();
    }

    @Test
    void mapperInterfacesDoNotEmbedSqlOrResultMappings() {
        for (Class<?> mapper : Set.of(SceneMapper.class, FlowMapper.class,
                FlowDraftMapper.class, FlowVersionMapper.class)) {
            for (Method method : mapper.getDeclaredMethods()) {
                for (Annotation annotation : method.getDeclaredAnnotations()) {
                    assertThat(SQL_ANNOTATIONS).as(mapper.getSimpleName() + "." + method.getName())
                            .doesNotContain(annotation.annotationType().getSimpleName());
                }
            }
        }
    }
}
