package com.clearledger.repository;

import com.clearledger.domain.ReconciliationMatch;
import com.clearledger.domain.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, Long> {
    List<ReconciliationMatch> findByBatchId(Long batchId);
    Optional<ReconciliationMatch> findByBatchIdAndInternalTransactionId(Long batchId, Long internalTransactionId);
    Optional<ReconciliationMatch> findByBatchIdAndSettlementRecordId(Long batchId, Long settlementRecordId);
    long countByBatchIdAndStatus(Long batchId, ReconciliationStatus status);

    @Query("""
        SELECT r FROM ReconciliationMatch r
        WHERE r.status IN :statuses
        ORDER BY r.reconciledAt DESC
    """)
    Page<ReconciliationMatch> findByStatuses(@Param("statuses") List<ReconciliationStatus> statuses, Pageable pageable);
}
