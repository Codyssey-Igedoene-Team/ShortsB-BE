package com.shortsb.book.service.impl;

import com.shortsb.book.dto.CatalogImportData;
import com.shortsb.book.dto.CatalogImportData.Book;
import com.shortsb.book.dto.CatalogImportData.Card;
import com.shortsb.book.dto.CatalogImportData.Page;
import com.shortsb.book.dto.CatalogImportData.Vector;
import com.shortsb.book.repository.CatalogImportRepository;
import com.shortsb.book.service.CatalogImportReader;
import com.shortsb.book.service.CatalogImportService;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 원본 전체 검증 후 등록. 한 항목 실패 시 전체 트랜잭션 롤백
public class CatalogImportServiceImpl implements CatalogImportService {

    private final CatalogImportReader reader;
    private final CatalogImportRepository repository;

    public CatalogImportServiceImpl(CatalogImportReader reader, CatalogImportRepository repository) {
        this.reader = reader;
        this.repository = repository;
    }

    @Override
    @Transactional
    public CatalogImportData importCatalog(Path catalogDir, Path documentsDir, Path cardsInput, Path cardsDir) {
        CatalogImportData data = reader.read(catalogDir, documentsDir, cardsInput, cardsDir);
        repository.lockImport();
        Map<String, Long> segmentIds = new HashMap<>();
        for (Book book : data.books()) {
            long bookId = repository.saveBook(book, data.version());
            for (Page page : book.pages()) {
                segmentIds.put(page.segmentId(), repository.savePage(bookId, page));
            }
            repository.verifyBookCounts(bookId, book.pages().size());
        }
        if (data.model() != null) {
            repository.saveModel(data.model());
            for (Vector vector : data.documents()) {
                repository.saveDocument(segmentIds.get(vector.inputId()), data.model(), vector);
            }
            for (Card card : data.cards()) {
                repository.saveCard(data.model(), card);
            }
            repository.activate(data);
        }
        return data;
    }
}
