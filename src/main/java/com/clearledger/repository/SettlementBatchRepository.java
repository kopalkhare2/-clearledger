package com.clearledger.repository;

import com.clearledger.domain.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {
    Optional<SettlementBatch> findByBatchReference(String batchReference);
    boolean existsByBatchReference(String batchReference);
}
