package com.shortsb.recommendation.command;

import com.shortsb.book.repository.BookRepository;
import com.shortsb.book.service.impl.BookServiceImpl;
import com.shortsb.recommendation.repository.ContrastRepository;
import com.shortsb.recommendation.service.ContrastEvidenceValidator;
import com.shortsb.recommendation.service.ContrastReviewReader;
import com.shortsb.recommendation.service.impl.ContrastImportServiceImpl;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 서버 자동 실행과 분리한 검수 JSONL의 수동 DB 대조·등록 명령
public final class ContrastImportCommand {

    private ContrastImportCommand() {}

    public static void main(String[] args) {
        try {
            Map<String, String> options = options(args);
            boolean apply = options.containsKey("--apply");
            String url = environment("SPRING_DATASOURCE_URL");
            if (!url.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }
            var source = new DriverManagerDataSource(url, environment("SPRING_DATASOURCE_USERNAME"),
                    environment("SPRING_DATASOURCE_PASSWORD"));
            var jdbc = new JdbcTemplate(source);
            var evidence = new ContrastEvidenceValidator(new BookServiceImpl(new BookRepository(jdbc)));
            var service = new ContrastImportServiceImpl(new ContrastReviewReader(), new ContrastRepository(jdbc), evidence);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            transaction.setReadOnly(!apply);
            transaction.setIsolationLevel(apply ? TransactionDefinition.ISOLATION_READ_COMMITTED : TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setTimeout(120);
            Integer count = transaction.execute(status -> service.importReviews(Path.of(options.get("--input")), apply));
            System.out.printf("대비 검수 자료 %s: %d개%n", apply ? "반영 완료" : "DB 대조 완료·변경 없음", count);
        } catch (Exception exception) {
            // 근거 구절·SQL 바인딩·DB 비밀값과 원래 예외 메시지 출력 차단
            System.err.println("대비 자료 처리 실패: 옵션·파일·도서/검수 정보·DB 설정 확인 필요 ("
                    + exception.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            String key = args[index];
            if (result.containsKey(key)) {
                throw new IllegalArgumentException();
            }
            if (key.equals("--apply") || key.equals("--validate-only")) {
                result.put(key, "true");
            } else if (key.equals("--input") && index + 1 < args.length && !args[index + 1].startsWith("--")) {
                result.put(key, args[++index]);
            } else {
                throw new IllegalArgumentException();
            }
        }
        if (!result.containsKey("--input") || result.containsKey("--apply") == result.containsKey("--validate-only")) {
            throw new IllegalArgumentException();
        }
        return result;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException();
        }
        return value;
    }
}
