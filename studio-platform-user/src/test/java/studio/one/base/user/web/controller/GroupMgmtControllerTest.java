package studio.one.base.user.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.web.dto.AddMembersRequest;
import studio.one.base.user.web.dto.GroupMemberSummaryDto;
import studio.one.base.user.web.mapper.ApplicationGroupMapper;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.platform.identity.IdentityService;

@ExtendWith(MockitoExtension.class)
class GroupMgmtControllerTest {

    @Mock
    private ApplicationGroupService<Group, Role> groupService;

    @Mock
    private ApplicationGroupMapper groupMapper;

    @Mock
    private ApplicationRoleMapper roleMapper;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private Group group;

    @Test
    void memberSummariesDelegatesNormalizedQueryAndMapsSummaryFields() {
        GroupMgmtController controller = new GroupMgmtController(groupService, groupMapper, roleMapper, identityServiceProvider);
        Pageable pageable = Pageable.unpaged();

        when(groupService.getById(10L)).thenReturn(group);
        when(group.getGroupId()).thenReturn(10L);
        when(groupService.getMemberSummaryDtos(eq(10L), eq("alice"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(
                        GroupMemberSummaryDto.builder().userId(1L).username("alice").name("Alice Kim").enabled(true).build(),
                        GroupMemberSummaryDto.builder().userId(2L).username("bob").name("Bob Lee").enabled(false).build()),
                        pageable,
                        2));

        var response = controller.memberSummaries(10L, Optional.of("  alice  "), pageable);
        var data = response.getBody().getData();

        assertEquals(2, data.getTotalElements());

        GroupMemberSummaryDto first = data.getContent().get(0);
        assertEquals(1L, first.getUserId());
        assertEquals("alice", first.getUsername());
        assertEquals("Alice Kim", first.getName());
        assertEquals(true, first.isEnabled());

        verify(groupService).getMemberSummaryDtos(10L, "alice", pageable);
    }

    @Test
    void removeMembershipsCallsServiceWithUserIds() {
        GroupMgmtController controller = new GroupMgmtController(groupService, groupMapper, roleMapper, identityServiceProvider);
        when(groupService.removeMembers(eq(10L), eq(List.of(1L, 2L)))).thenReturn(2);

        AddMembersRequest req = new AddMembersRequest();
        req.setUserIds(List.of(1L, 2L));

        var response = controller.removeMemberships(10L, req);

        assertEquals(2, response.getBody().getData());
        verify(groupService).removeMembers(10L, List.of(1L, 2L));
    }

    @Test
    void memberSummariesTreatsBlankQueryAsNull() {
        GroupMgmtController controller = new GroupMgmtController(groupService, groupMapper, roleMapper, identityServiceProvider);
        Pageable pageable = Pageable.unpaged();

        when(groupService.getById(10L)).thenReturn(group);
        when(group.getGroupId()).thenReturn(10L);
        when(groupService.getMemberSummaryDtos(eq(10L), eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        controller.memberSummaries(10L, Optional.of("   "), pageable);

        verify(groupService).getMemberSummaryDtos(10L, null, pageable);
    }
}
