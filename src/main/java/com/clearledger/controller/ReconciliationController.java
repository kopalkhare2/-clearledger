package com.clearledger.controller;

import com.clearledger.domain.ReconciliationMatch;
import com.clearledger.domain.SettlementBatch;
import com.clearledger.dto.IngestSettlementBatchRequest;
import com.clearledger.dto.ReconciliationMatchDto;
import com.clearledger.dto.ReconciliationSummaryResponse;
import com.clearledger.dto.SettlementBatchResponse;
import com.clearledger.exception.SettlementBatchNotFoundException;
import com.clearledger.mapper.ReconciliationMapper;
import com.clearledger.repository.SettlementBatchRepository;
import com.clearledger.service.ReconciliationEngineService;
import com.clearledger.service.SettlementIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Settlement batch ingestion and reconciliation endpoints")
public class ReconciliationController {

    private final SettlementIngestionService settlementIngestionService;
    private final ReconciliationEngineService reconciliationEngineService;
    private final SettlementBatchRepository settlementBatchRepository;
    private final ReconciliationMapper reconciliationMapper;

    public ReconciliationController(SettlementIngestionService settlementIngestionService,
                                  ReconciliationEngineService reconciliationEngineService,
                                  SettlementBatchRepository settlementBatchRepository,
                                  ReconciliationMapper reconciliationMapper) {
        this.settlementIngestionService = settlementIngestionService;
        this.reconciliationEngineService = reconciliationEngineService;
        this.settlementBatchRepository = settlementBatchRepository;
        this.reconciliationMapper = reconciliationMapper;
    }

    @PostMapping("/batches")
    @Operation(summary = "Ingest settlement batch", description = "Ingests an external settlement batch file with idempotency")
    public ResponseEntity<SettlementBatchResponse> ingestBatch(@Valid @RequestBody IngestSettlementBatchRequest request) {
        SettlementBatch batch = settlementIngestionService.ingestBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reconciliationMapper.toBatchResponse(batch));
    }

    @PostMapping("/batches/{id}/reconcile")
    @Operation(summary = "Reconcile batch", description = "Triggers deterministic 2-way matching for the specified batch")
    public ResponseEntity<ReconciliationSummaryResponse> reconcileBatch(@PathVariable("id") Long batchId) {
        List<ReconciliationMatch> matches = reconciliationEngineService.reconcileBatch(batchId);
        SettlementBatch batch = settlementBatchRepository.findById(batchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(batchId));
        return ResponseEntity.ok(reconciliationMapper.toSummaryResponse(batch, matches));
    }

    @GetMapping("/batches/{id}/summary")
    @Operation(summary = "Get batch reconciliation summary", description = "Retrieves match breakdown and discrepancy details for a batch")
    public ResponseEntity<ReconciliationSummaryResponse> getBatchSummary(@PathVariable("id") Long batchId) {
        SettlementBatch batch = settlementBatchRepository.findById(batchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(batchId));
        List<ReconciliationMatch> matches = reconciliationEngineService.getBatchMatches(batchId);
        return ResponseEntity.ok(reconciliationMapper.toSummaryResponse(batch, matches));
    }

    @GetMapping("/discrepancies")
    @Operation(summary = "List reconciliation discrepancies", description = "Retrieves paginated list of all flagged discrepancies across batches")
    public ResponseEntity<Page<ReconciliationMatchDto>> getDiscrepancies(Pageable pageable) {
        Page<ReconciliationMatch> discrepancies = reconciliationEngineService.getDiscrepancies(pageable);
        return ResponseEntity.ok(discrepancies.map(reconciliationMapper::toMatchDto));
    }
}
