/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file EntityLogger.java
 *      @date 2025
 *
 */


package studio.one.platform.util.jpa;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Table;
import javax.persistence.metamodel.EntityType;

import org.slf4j.Logger;

import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;

/**
* JPA 엔티티 관련 로깅을 수행하는 유틸리티 클래스.
 * <p>
 * EntityManagerFactory를 통해 JPA 엔티티 정보를 추출하고,
 * 해당 정보를 로깅하여 애플리케이션의 엔티티 구성 상태를 파악하는 데 도움을 줍니다.
 * </p>
 *
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>EntityManagerFactory에 등록된 엔티티 목록을 로그로 출력</li>
 *   <li>엔티티 클래스 이름을 기준으로 패키지별 그룹핑</li>
 *   <li>특정 패키지 프리픽스에 엔티티가 없는 경우 경고 로그 출력</li>
 *   <li>엔티티 클래스에서 테이블 이름 추출 (Hibernate 또는 @Table 어노테이션 사용)</li>
 *   <li>엔티티가 하나도 없는 경우 예외 발생 (fail-fast)</li>
 *   <li>국제화(I18n) 지원을 통해 다양한 언어로 로그 메시지 출력</li>
 * </ul>
 *
 * <p><b>사용 방법:</b></p>
 * <pre>{@code
 * EntityManagerFactory emf = ...; // EntityManagerFactory 인스턴스
 * Logger log = ...; // Logger 인스턴스
 * String tag = "MyApplication"; // 애플리케이션 태그
 * I18n i18n = ...; // I18n 인터페이스 구현체 (선택 사항)
 *
 * EntityLogger.log(emf, log, tag, i18n);
 * }</pre>
 * 
 * @author  donghyuck, son
 * @since 2025-08-14
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-14  donghyuck, son: 최초 생성.
 * </pre>
 */


public final class EntityLogger {

    // ===== Sonar S1192: 중복 문자열 상수화 =====
    private static final String CODE_NONE = "warn.jpa.entities.none";
    private static final String CODE_HEADER = "info.jpa.entities.header";
    private static final String CODE_LINE = "info.jpa.entities.line";

    private static final String DEF_NONE = "[{0}] No JPA entities detected (PU={1}). Check entity scan packages.";
    private static final String DEF_HEADER = "📦 [{0}] JPA Entities registered (PU={1}, count={2}):";
    private static final String DEF_LINE = " • {0}  (table: {1})";

    private static final String[] PU_NAME_KEYS = {
            "hibernate.ejb.persistenceUnitName",
            "hibernate.persistenceUnitName",
            "jakarta.persistence.persistenceUnitName",
            "javax.persistence.persistenceUnitName"
    };

    private EntityLogger() {
        throw new AssertionError("No instances"); // S1118
    }

    /** PU명/개수/엔티티+테이블명(가능 시)을 로그로 출력 */
    public static void log(EntityManagerFactory emf, Logger log, String tag, I18n i18n) {
        final SortedSet<String> names = entityClassNames(emf);
        final String pu = persistenceUnitName(emf);

        if (names.isEmpty()) {
            if (log.isWarnEnabled()) {
                log.warn(format(i18n, CODE_NONE, DEF_NONE, LogUtils.blue(tag), pu));
            }
            return;
        }

        if (log.isInfoEnabled()) {
            log.info(format(i18n, CODE_HEADER, DEF_HEADER, LogUtils.blue(tag), pu, names.size()));
            for (String fqn : names) {
                String table = tableName(fqn, emf).orElse("-");
                log.info(format(i18n, CODE_LINE, DEF_LINE, fqn, table));
            }
        }
    }

    /** 엔티티 FQCN 목록(정렬) */
    public static SortedSet<String> entityClassNames(EntityManagerFactory emf) {
        return emf.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .map(Class::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 패키지별 그룹핑(디버깅용) */
    public static Map<String, List<String>> groupByPackage(EntityManagerFactory emf) {
        return entityClassNames(emf).stream().collect(Collectors.groupingBy(
                n -> {
                    int i = n.lastIndexOf('.');
                    return (i > 0) ? n.substring(0, i) : "";
                },
                TreeMap::new,
                Collectors.toList()));
    }

    /** 특정 패키지 프리픽스에 엔티티가 하나도 없으면 경고 */
    public static void assertHasEntitiesFromPackages(EntityManagerFactory emf, Logger log, String... pkgPrefixes) {
        Set<String> all = entityClassNames(emf);
        for (String p : pkgPrefixes) {
            boolean any = all.stream().anyMatch(n -> n.startsWith(p + "."));
            if (!any && log.isWarnEnabled()) {
                log.warn("⚠ No entities detected under package prefix: {}", p);
            }
        }
    }

    /** (개선) 가능하면 Hibernate로, 아니면 @Table로 테이블명 추출 */
    public static Optional<String> tableName(String entityClassName, EntityManagerFactory emf) {
        // 1) Hibernate(있다면) 리플렉션으로 접근 (S1181 회피: 구체 예외만 처리)
        try {
            Class<?> sfiClass = Class.forName("org.hibernate.engine.spi.SessionFactoryImplementor");
            Object sfi = emf.unwrap(sfiClass);

            Method getMetamodel = sfiClass.getMethod("getMetamodel");
            Object hMeta = getMetamodel.invoke(sfi);

            Method entityPersister = hMeta.getClass().getMethod("entityPersister", String.class);
            Object persister = entityPersister.invoke(hMeta, entityClassName);

            // 대부분 전략에서 동작: AbstractEntityPersister#getTableName()
            Class<?> aep = Class.forName("org.hibernate.persister.entity.AbstractEntityPersister");
            if (aep.isInstance(persister)) {
                Method getTableName = aep.getMethod("getTableName");
                Object name = getTableName.invoke(persister);
                if (name instanceof String) {
                    String s = (String) name;
                    if (!s.isBlank())
                        return Optional.of(s);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException e) {
            // Hibernate 미사용/버전 차이/리플렉션 실패 → JPA 폴백으로 진행
        }

        // 2) JPA @Table 폴백
        try {
            Class<?> clazz = Class.forName(entityClassName);
            Table t = clazz.getAnnotation(Table.class);
            if (t != null) {
                String n = t.name();
                if (n != null && !n.isBlank())
                    return Optional.of(n);
            }
        } catch (ClassNotFoundException e) {
            // 엔티티 클래스를 로드할 수 없는 경우: 빈 Optional
        }
        return Optional.empty();
    }

    /** 엔티티가 하나도 없으면 런타임 예외로 fail-fast (선택) */
    public static void failIfEmpty(EntityManagerFactory emf) {
        if (emf.getMetamodel().getEntities().isEmpty()) {
            throw new IllegalStateException("No JPA entities detected in persistence unit.");
        }
    }

    /** PU 이름 키 호환성 보강 */
    private static String persistenceUnitName(EntityManagerFactory emf) {
        for (String k : PU_NAME_KEYS) {
            Object v = emf.getProperties().get(k);
            if (v instanceof String) {
                String s = (String) v;
                if (!s.isBlank())
                    return s;
            }
        }
        return "default";
    }

    /** I18n 메시지 포맷팅 (없으면 기본 메시지) */
    private static String format(I18n i18n, String code, String def, Object... args) {
        if (i18n != null) {
            try {
                String v = i18n.get(code, args);
                if (v != null && !v.isBlank())
                    return v;
            } catch (RuntimeException ex) {
                // 메시지 미존재/변환 실패 시 폴백
            }
        }
        return MessageFormat.format(def, args);
    }
}