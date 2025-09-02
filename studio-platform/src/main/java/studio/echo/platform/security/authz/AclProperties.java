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
 *      @file AclProperties.java
 *      @date 2025
 *
 */


package studio.echo.platform.security.authz;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
/**
* ACL(도메인/컴포넌트별) 웹 권한 정책을 구성하는 프로퍼티.
 *
 * 예시 YAML:
 * echo:
 *   security:
 *     acl:
 *       domains:
 *         group:
 *           roles:
 *             read:  [ADMIN, MANAGER]
 *             write: [ADMIN]
 *           components:
 *             member:
 *               roles:
 *                 read:  [ADMIN, MANAGER]
 *                 write: [ADMIN]
 * 
 * 
 * @author  donghyuck, son
 * @since 2025-09-01
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-01  donghyuck, son: 최초 생성.
 * </pre>
 */


@Getter
@Setter
public class AclProperties {  
    
    private Map<String, DomainPolicy> domains = new HashMap<>();

    @Getter
    @Setter
    public static class DomainPolicy { 
        private Roles roles = new Roles();
        private Map<String, ComponentPolicy> components = new HashMap<>();
    }

    @Getter
    @Setter
    public static class ComponentPolicy { 
        private Roles roles;  
    }

    @Getter
    @Setter
    public static class Roles {
        private List<String> read = Collections.emptyList();
        private List<String> write = Collections.emptyList();
        private List<String> admin = Collections.emptyList();
    }
}
