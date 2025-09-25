package studio.echo.base.user.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.repository.ApplicationRoleRepository;
import studio.echo.base.user.exception.RoleNotFoundException;
import studio.echo.base.user.service.ApplicationRoleService;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ApplicationRoleServiceImpl implements ApplicationRoleService<ApplicationRole> {

    private final ApplicationRoleRepository roleRepo;

    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> findAll(Pageable pageable) {
        return roleRepo.findAll(pageable);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public ApplicationRole get(Long roleId) {
        return roleRepo.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.byId(roleId));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
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
    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> getRolesByUser(Long userId, Pageable pageable) {
        return roleRepo.findRolesByUserId(userId, pageable);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ApplicationRole> getRolesByUser(Long userId) {
        return roleRepo.findRolesByUserId(userId);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> getRolesByGroup(Long groupId, Pageable pageable) {
        return roleRepo.findRolesByGroupId(groupId, pageable);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ApplicationRole> getRolesByGroup(Long groupId) {
        return roleRepo.findRolesByGroupIds(List.of(groupId)); // 혹은 groupRoleRepo로 List 버전 호출
    }
}