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
 *      @file ApplicationCompanyService.java
 *      @date 2025
 *
 */


package studio.one.base.user.service;

import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationCompany;
/**
 *
 * @author  donghyuck, son
 * @since 2025-09-15
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-15  donghyuck, son: 최초 생성.
 * </pre>
 */


public interface ApplicationCompanyService {

    public static final String SERVICE_NAME = "components:application-company-service";

    ApplicationCompany get(Long companyId);

    ApplicationCompany create(ApplicationCompany company);

    ApplicationCompany update(Long companyId, Consumer<ApplicationCompany> mutator);

    void delete(Long companyId);

    // properties (ElementCollection or 별도 테이블)
    void setProperty(Long companyId, String name, String value);

    void removeProperty(Long companyId, String name);

    Page<ApplicationCompany> search(String q, Pageable pageable);
}
