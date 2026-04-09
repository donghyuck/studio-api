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

import studio.one.base.user.domain.entity.ApplicationGroupMemberSummary;
import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.service.ApplicationGroupService;
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
        when(groupService.getMemberSummaries(eq(10L), eq("alice"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(
                        new Summary(1L, "alice", "Alice Kim", true),
                        new Summary(2L, "bob", "Bob Lee", false)),
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

        verify(groupService).getMemberSummaries(10L, "alice", pageable);
    }

    @Test
    void memberSummariesTreatsBlankQueryAsNull() {
        GroupMgmtController controller = new GroupMgmtController(groupService, groupMapper, roleMapper, identityServiceProvider);
        Pageable pageable = Pageable.unpaged();

        when(groupService.getById(10L)).thenReturn(group);
        when(group.getGroupId()).thenReturn(10L);
        when(groupService.getMemberSummaries(eq(10L), eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        controller.memberSummaries(10L, Optional.of("   "), pageable);

        verify(groupService).getMemberSummaries(10L, null, pageable);
    }

    private record Summary(Long userId, String username, String name, boolean enabled)
            implements ApplicationGroupMemberSummary {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
