package studio.echo.base.user.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.repository.ApplicationRoleRepository;
import studio.echo.base.user.service.ApplicationRoleService;
import studio.echo.platform.exception.NotFoundException;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationRoleServiceImpl implements ApplicationRoleService {

    private final ApplicationRoleRepository roleRepo; 

    @Override @Transactional(readOnly = true)
    public ApplicationRole get(Long roleId) {
        return roleRepo.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
    }

    @Override @Transactional(readOnly = true)
    public Optional<ApplicationRole> findByName(String name) {
        return roleRepo.findByName(name);
    }

    @Override
    public ApplicationRole create(ApplicationRole role) { 
        return roleRepo.save(role);
    }

    @Override
    public ApplicationRole update(Long roleId, Consumer<ApplicationRole> mutator) {
        ApplicationRole r = get(roleId);
        mutator.accept(r);
        return roleRepo.save(r);
    }

    @Override
    public void delete(Long roleId) {
        roleRepo.deleteById(roleId);
    }

    // --- 사용자/그룹 기준 롤 조회 ---
    @Override @Transactional(readOnly = true)
    public Page<ApplicationRole> getRolesByUser(Long userId, Pageable pageable) {
        // 존재 체크를 원하면 아래 한 줄 추가
        // userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        return roleRepo.findRolesByUserId(userId, pageable);
    }

    @Override @Transactional(readOnly = true)
    public List<ApplicationRole> getRolesByUser(Long userId) {
        return roleRepo.findRolesByUserId(userId);
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationRole> getRolesByGroup(Long groupId, Pageable pageable) {
        return roleRepo.findRolesByGroupId(groupId, pageable);
    }

    @Override @Transactional(readOnly = true)
    public List<ApplicationRole> getRolesByGroup(Long groupId) {
        return roleRepo.findRolesByGroupIds( List.of(groupId) ); // 혹은 groupRoleRepo로 List 버전 호출
    }
}