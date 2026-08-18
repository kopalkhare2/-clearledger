package com.clearledger.repository;

import com.clearledger.domain.SettlementRecord;
import com.clearledger.domain.SettlementRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, Long> {
    List<SettlementRecord> findByBatchId(Long batchId);
    List<SettlementRecord> findByBatchIdAndStatus(Long batchId, SettlementRecordStatus status);
    Optional<SettlementRecord> findByBatchIdAndExternalTxId(Long batchId, String externalTxId);
}
