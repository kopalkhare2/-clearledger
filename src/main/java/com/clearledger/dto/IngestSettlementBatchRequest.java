package com.clearledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Request to ingest an external settlement batch")
public record IngestSettlementBatchRequest(

    @Schema(description = "Unique batch reference", example = "BATCH-20260818-001")
    @NotBlank(message = "batchReference is required")
    @Size(max = 64)
    String batchReference,

    @Schema(description = "External source provider (e.g. STRIPE, VISA, JPMORGAN)", example = "STRIPE")
    @NotBlank(message = "sourceProvider is required")
    @Size(max = 64)
    String sourceProvider,

    @Schema(description = "List of settlement records")
    @NotEmpty(message = "records list cannot be empty")
    @Valid
    List<SettlementRecordItemDto> records
) {}
