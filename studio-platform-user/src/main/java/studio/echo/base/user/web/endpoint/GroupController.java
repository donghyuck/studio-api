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
 *      @file GroupEndpoint.java
 *      @date 2025
 *
 */


package studio.echo.base.user.web.endpoint;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URI;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.service.ApplicationGroupService;
import studio.echo.base.user.web.dto.ApplicationGroupDto;
import studio.echo.base.user.web.dto.ApplicationUserDto;
import studio.echo.base.user.web.mapper.ApplicationGroupMapper;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.web.dto.ApiResponse;
/**
 *
 * @author  donghyuck, son
 * @since 2025-08-27
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-27  donghyuck, son: 최초 생성.
 * </pre>
 */


@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/groups")
@RequiredArgsConstructor
public class GroupController {

    private final ApplicationGroupService groupService; 
    private final ApplicationGroupMapper mapper; 

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('group','read')")
    public ResponseEntity<ApiResponse<Page<ApplicationGroupDto>>> list(@PageableDefault(size = 15, sort = "groupId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ApplicationGroup> page = groupService.findAll(pageable); 
        Page<ApplicationGroupDto> dtoPage = page.map(mapper::toDto);
        return ok(ApiResponse.ok(dtoPage));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','read')")
    public ResponseEntity<ApiResponse<ApplicationGroupDto>> get(@PathVariable Long id) {
        ApplicationGroup group = groupService.get(id);
        return ok(ApiResponse.ok(mapper.toDto(group)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<ApplicationGroupDto>> create(@Valid @RequestBody ApplicationGroupDto req)  {
        ApplicationGroup group = mapper.toEntity(req); 
        ApplicationGroup saved = groupService.create(group);
        // Location 헤더 (선택)
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(String.format("/groups/%s", saved.getGroupId())));
        return new ResponseEntity<>(ApiResponse.ok(mapper.toDto(saved)), headers, HttpStatus.CREATED);
    }
 
    @PutMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<ApplicationGroupDto>> update(@PathVariable Long id, @Valid @RequestBody ApplicationGroupDto dto)  {
        ApplicationGroup updated = groupService.update(id, g-> mapper.updateEntityFromDto(dto, g) );
        return ok(ApiResponse.ok(mapper.toDto(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        groupService.delete(id);
        return ok(ApiResponse.ok());
    }
 
 
}