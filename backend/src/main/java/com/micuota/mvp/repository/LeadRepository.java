package com.micuota.mvp.repository;

import com.micuota.mvp.domain.Lead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByEmailIgnoreCase(String email);

    List<Lead> findAllByOrderByCreatedAtDesc();
}
