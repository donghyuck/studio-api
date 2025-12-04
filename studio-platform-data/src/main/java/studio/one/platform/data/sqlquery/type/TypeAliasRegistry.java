/*
 * Copyright 2016 donghyuck, son
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package studio.one.platform.data.sqlquery.type;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.*;
import org.springframework.util.ClassUtils;

/**
 * IBatis org.apache.ibatis.type.TypeAliasRegistry 기반
 * 
 * @author donghyuck, son
 */
public class TypeAliasRegistry {

    private final Map<String, Class<?>> typeAlias = new HashMap<>();

    public TypeAliasRegistry() {
        // 기본 타입 등록
        registerPrimitives();
        registerWrapperTypes();
        registerArrayTypes();
        registerCollections();
        registerMiscTypes();
    }

    private void registerPrimitives() {
        registerAlias("_byte", byte.class);
        registerAlias("_short", short.class);
        registerAlias("_int", int.class);
        registerAlias("_integer", int.class);
        registerAlias("_long", long.class);
        registerAlias("_float", float.class);
        registerAlias("_double", double.class);
        registerAlias("_boolean", boolean.class);
    }

    private void registerWrapperTypes() {
        registerAlias("string", String.class);
        registerAlias("byte", Byte.class);
        registerAlias("short", Short.class);
        registerAlias("int", Integer.class);
        registerAlias("integer", Integer.class);
        registerAlias("long", Long.class);
        registerAlias("float", Float.class);
        registerAlias("double", Double.class);
        registerAlias("boolean", Boolean.class);
    }

    private void registerArrayTypes() {
        registerAlias("byte[]", Byte[].class);
        registerAlias("short[]", Short[].class);
        registerAlias("int[]", Integer[].class);
        registerAlias("integer[]", Integer[].class);
        registerAlias("long[]", Long[].class);
        registerAlias("float[]", Float[].class);
        registerAlias("double[]", Double[].class);
        registerAlias("boolean[]", Boolean[].class);

        registerAlias("_byte[]", byte[].class);
        registerAlias("_short[]", short[].class);
        registerAlias("_int[]", int[].class);
        registerAlias("_integer[]", int[].class);
        registerAlias("_long[]", long[].class);
        registerAlias("_float[]", float[].class);
        registerAlias("_double[]", double[].class);
        registerAlias("_boolean[]", boolean[].class);

        registerAlias("date[]", Date[].class);
        registerAlias("decimal[]", BigDecimal[].class);
        registerAlias("bigdecimal[]", BigDecimal[].class);
        registerAlias("object[]", Object[].class);
    }

    private void registerCollections() {
        registerAlias("map", Map.class);
        registerAlias("hashmap", HashMap.class);
        registerAlias("list", List.class);
        registerAlias("arraylist", ArrayList.class);
        registerAlias("collection", Collection.class);
        registerAlias("iterator", Iterator.class);
    }

    private void registerMiscTypes() {
        registerAlias("date", Date.class);
        registerAlias("decimal", BigDecimal.class);
        registerAlias("bigdecimal", BigDecimal.class);
        registerAlias("object", Object.class);
        registerAlias("locale", Locale.class);
        registerAlias("resultset", ResultSet.class); // 'ResultSet' → 'resultset' (통일된 lowercase 키)
    }

    public void registerAlias(String alias, Class<?> value) {
        if (alias == null || value == null)
            throw new IllegalArgumentException("Alias and value must not be null");

        String key = alias.toLowerCase(Locale.ENGLISH);

        if (typeAlias.containsKey(key) && !typeAlias.get(key).equals(value)) {
            throw new IllegalArgumentException(
                "The alias '" + alias + "' is already mapped to '" + typeAlias.get(key).getName() + "'");
        }
        typeAlias.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> resolveAlias(String className) {
        if (className == null) return null;

        String key = className.toLowerCase(Locale.ENGLISH);
        try {
            if (typeAlias.containsKey(key)) {
                return (Class<T>) typeAlias.get(key);
            }
            return (Class<T>) ClassUtils.forName(className, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Type alias or class not found: " + className, e);
        }
    }
}