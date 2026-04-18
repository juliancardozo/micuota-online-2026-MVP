package com.micuota.mvp.repository;

import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByTenantIdAndEmail(Long tenantId, String email);
    List<User> findByTenantIdOrderByFullNameAsc(Long tenantId);
    List<User> findByTenantIdAndRoleOrderByFullNameAsc(Long tenantId, UserRole role);
    long countByTenantIdAndRole(Long tenantId, UserRole role);
}
