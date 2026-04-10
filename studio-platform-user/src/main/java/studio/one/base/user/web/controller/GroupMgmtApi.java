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
 *      @file GroupMgmtApi.java
 *      @date 2026
 *
 */

package studio.one.base.user.web.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import studio.one.base.user.web.dto.AddMembersRequest;
import studio.one.base.user.web.dto.GroupDto;
import studio.one.base.user.web.dto.GroupMemberSummaryDto;
import studio.one.base.user.web.dto.PropertyDto;
import studio.one.base.user.web.dto.RoleDto;
import studio.one.base.user.web.dto.UpdateRolesRequest;
import studio.one.platform.web.dto.ApiResponse;

/**
 * API contract for group management endpoints.
 *
 * @author donghyuck, son
 * @since 2026-04-10
 * @version 1.0
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-04-10  donghyuck, son: 최초 생성.
 * </pre>
 */
public interface GroupMgmtApi {

    // ── CRUD ──────────────────────────────────────────────────────────────────
    ResponseEntity<ApiResponse<Page<GroupDto>>> list(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "fields", required = false) Optional<String> fields,
            Pageable pageable);

    ResponseEntity<ApiResponse<GroupDto>> get(Long id);

    ResponseEntity<ApiResponse<GroupDto>> create(@Valid @RequestBody GroupDto req);

    ResponseEntity<ApiResponse<GroupDto>> update(Long id, @Valid @RequestBody GroupDto dto);

    ResponseEntity<ApiResponse<Void>> delete(Long id);

    // ── Roles ─────────────────────────────────────────────────────────────────
    ResponseEntity<ApiResponse<List<RoleDto>>> roles(Long id);

    ResponseEntity<ApiResponse<Void>> updateGroupRoles(Long id,
            @Valid @RequestBody UpdateRolesRequest req,
            UserDetails actor);

    // ── Membership ────────────────────────────────────────────────────────────
    ResponseEntity<ApiResponse<Page<studio.one.platform.identity.UserDto>>> members(Long id, Pageable pageable);

    ResponseEntity<ApiResponse<Page<GroupMemberSummaryDto>>> memberSummaries(Long id,
            @RequestParam(value = "q", required = false) Optional<String> q,
            Pageable pageable);

    ResponseEntity<ApiResponse<Integer>> addMemberships(Long id,
            @Valid @RequestBody AddMembersRequest req,
            UserDetails principal);

    ResponseEntity<ApiResponse<Integer>> removeMemberships(Long id,
            @Valid @RequestBody AddMembersRequest req);

    // ── Properties ────────────────────────────────────────────────────────────
    ResponseEntity<ApiResponse<Map<String, String>>> getProperties(Long id);

    ResponseEntity<ApiResponse<Map<String, String>>> replaceProperties(Long id,
            @RequestBody Map<String, String> properties);

    ResponseEntity<ApiResponse<PropertyDto>> getProperty(Long id, String key);

    ResponseEntity<ApiResponse<PropertyDto>> setProperty(Long id, String key,
            @Valid @RequestBody PropertyDto dto);

    ResponseEntity<Void> deleteProperty(Long id, String key);
}
