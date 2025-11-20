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
 *      @file RoleAclSidSyncListener.java
 *      @date 2025
 *
 */

package studio.one.base.security.acl.domain.event.listener;

import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.domain.entity.AclSidEntity;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclSidRepository;
import studio.one.platform.security.event.RoleUpdatedEvent;
/**
 * Synchronizes ACL SIDs when application roles change.
 */ 
@RequiredArgsConstructor
@Slf4j
public class RoleAclSidSyncListener {

    private final AclSidRepository sidRepository;
    private final AclEntryRepository entryRepository;

    @EventListener 
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationRoleEvent(RoleUpdatedEvent event) {
        
        if (event == null || event.getRoleName() == null || event.getRoleName().isBlank())
            return;
        String roleName = event.getRoleName().trim();
        switch (event.getAction()) {
            case CREATED -> ensureSid(roleName);
            case UPDATED -> {
                ensureSid(roleName);
                ensureRemovedIfRenamed(event.getPreviousRoleName(), roleName);
            }
            case DELETED -> deleteSidAndEntries(roleName);
        }
    }

    private void ensureSid(String roleName) {
        sidRepository.findBySidAndPrincipal(roleName, false)
                .ifPresentOrElse(
                        sid -> log.debug("ACL sid already exists for {}", roleName),
                        () -> {
                            AclSidEntity sid = new AclSidEntity();
                            sid.setSid(roleName);
                            sid.setPrincipal(false);
                            sidRepository.save(sid);
                            log.debug("created ACL sid for {}", roleName);
                        });
    }

    private void ensureRemovedIfRenamed(String previousRoleName, String currentRoleName) {
        if (previousRoleName == null || previousRoleName.isBlank())
            return;
        String trimmed = previousRoleName.trim();
        if (!trimmed.equals(currentRoleName)) {
            deleteSidAndEntries(trimmed);
        }
    }

    private void deleteSidAndEntries(String roleName) {
        sidRepository.findBySidAndPrincipal(roleName, false).ifPresent(sid -> {
            entryRepository.deleteBySid(sid);
            sidRepository.delete(sid);
            log.debug("deleted ACL sid and entries for {}", roleName);
        });
    }
}
