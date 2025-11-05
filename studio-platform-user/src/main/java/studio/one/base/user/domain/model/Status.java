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
 *      @file Status.java
 *      @date 2025
 *
 */
package studio.one.base.user.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 사용자 상태 객체
 * 
 * @author donghyuck, son
 * @since 2025-08-05
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-05  donghyuck, son: 최초 생성.
 *          </pre>
 */

public enum Status {

    NONE(0),

    APPROVED(1),

    REJECTED(2),

    VALIDATED(3),

    REGISTERED(4);

    private final int id;

    Status(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    private static final Map<Integer, Status> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(Status::getId, Function.identity()));

    private static final Map<String, Status> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(s -> s.name().toLowerCase(Locale.ROOT), Function.identity()));

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Status fromJson(Object raw) {
        if (raw == null)
            return NONE;

        if (raw instanceof Number) {
            return BY_ID.getOrDefault(((Number) raw).intValue(), NONE);
        }

        String s = raw.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s))
            return NONE;

        boolean numeric = s.chars().allMatch(Character::isDigit)
                || (s.startsWith("-") && s.length() > 1 && s.substring(1).chars().allMatch(Character::isDigit));
        if (numeric) {
            try {
                return BY_ID.getOrDefault(Integer.parseInt(s), NONE); // 없으면 throw로 바꿔도 됨
            } catch (NumberFormatException ignored) {
                /* fallthrough to name match */ }
        }

        Status byName = BY_NAME.get(s.toLowerCase(Locale.ROOT));
        if (byName != null)
            return byName;

        // 알 수 없는 값
        throw new IllegalArgumentException("Unknown Status: " + raw);
    }

    public static Status getById(int i) {
        return BY_ID.getOrDefault(i, NONE); // 정책에 따라 NONE 대신 null/예외 가능
    }

}
