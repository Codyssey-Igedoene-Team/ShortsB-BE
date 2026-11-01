package com.shortsb.book.service;

import com.shortsb.book.dto.CatalogImportData;
import java.nio.file.Path;

// 검증된 도서·페이지·사전 벡터의 수동 등록 계약
public interface CatalogImportService {

    CatalogImportData importCatalog(Path catalogDir, Path documentsDir, Path cardsInput, Path cardsDir);
}
