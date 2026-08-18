package com.clearledger.repository;

import com.clearledger.domain.ReconciliationResolutionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationResolutionAuditRepository extends JpaRepository<ReconciliationResolutionAudit, Long> {

    List<ReconciliationResolutionAudit> findByMatchIdOrderByResolvedAtDesc(Long matchId);

    Optional<ReconciliationResolutionAudit> findTopByMatchIdOrderByResolvedAtDesc(Long matchId);

    long countByMatchId(Long matchId);
}
