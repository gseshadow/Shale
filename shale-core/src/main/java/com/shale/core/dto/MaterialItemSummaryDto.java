package com.shale.core.dto;

import java.time.LocalDateTime;

public record MaterialItemSummaryDto(long id, int shaleClientId, long caseId, Long materialRequestId, int materialTypeId, String materialTypeName, String materialTypeSystemKey, String format, String name, Integer receivedByUserId, String receivedByDisplayName, LocalDateTime receivedAt, String completeness, Integer quantityCount, Integer pageCount, Integer fileCount, String custodyStatus, LocalDateTime updatedAt, byte[] rowVer) {}
