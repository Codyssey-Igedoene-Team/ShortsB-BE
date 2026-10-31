package com.shortsb.recommendation.service;

import java.nio.file.Path;

// 검수 자료의 DB 대조와 명시적 반영 계약
public interface ContrastImportService {

    int importReviews(Path input, boolean apply);
}
