package com.micuota.mvp.repository;

import com.micuota.mvp.domain.StudentBankAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentBankAccountRepository extends JpaRepository<StudentBankAccount, Long> {
    List<StudentBankAccount> findByTenantIdOrderByPreferredDescUpdatedAtDesc(Long tenantId);
    List<StudentBankAccount> findByTenantIdAndStudentIdOrderByPreferredDescUpdatedAtDesc(Long tenantId, Long studentUserId);
}
