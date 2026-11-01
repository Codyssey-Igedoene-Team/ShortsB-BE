package com.shortsb.book.command;

import com.shortsb.book.dto.CatalogImportData;
import com.shortsb.book.repository.CatalogImportRepository;
import com.shortsb.book.service.CatalogImportReader;
import com.shortsb.book.service.CatalogImportService;
import com.shortsb.book.service.impl.CatalogImportServiceImpl;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// 웹 서버·Google 로그인·GPU 호출 없이 실행하는 명시적 등록 명령
public final class CatalogImportCommand {

    private CatalogImportCommand() {}

    public static void main(String[] args) {
        try {
            Map<String, String> options = options(args);
            Path catalog = Path.of(options.get("--catalog-dir"));
            Path documents = path(options, "--documents-dir");
            Path cardsInput = path(options, "--cards-input");
            Path cards = path(options, "--cards-dir");
            CatalogImportReader reader = new CatalogImportReader();
            CatalogImportData data;
            if (options.containsKey("--validate-only")) {
                data = reader.read(catalog, documents, cardsInput, cards);
            } else {
                String url = environment("SPRING_DATASOURCE_URL");
                if (!url.startsWith("jdbc:postgresql://")) {
                    throw new IllegalArgumentException();
                }
                var source = new DriverManagerDataSource(url, environment("SPRING_DATASOURCE_USERNAME"),
                        environment("SPRING_DATASOURCE_PASSWORD"));
                var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
                transactions.setTimeout(120);
                CatalogImportService service = new CatalogImportServiceImpl(reader,
                        new CatalogImportRepository(new JdbcTemplate(source)));
                // 직접 생성한 서비스이므로 명령에서는 TransactionTemplate로 경계 지정
                data = transactions.execute(status -> service.importCatalog(catalog, documents, cardsInput, cards));
            }
            if (data == null) {
                throw new IllegalStateException();
            }
            System.out.printf("%s: 작품 %d개, 페이지 %d개, 본문 벡터 %d개, 카드 %d개%n",
                    options.containsKey("--validate-only") ? "파일 검증 완료·DB 변경 없음" : "DB 등록 완료",
                    data.books().size(), data.pageCount(), data.documents().size(), data.cards().size());
        } catch (Exception exception) {
            // SQL 오류에도 본문·바인딩 값·접속 정보·원래 예외 메시지 출력 금지
            System.err.println("도서 등록 실패: 옵션·입력 파일·기존 데이터·DB 설정 확인 필요 ("
                    + exception.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }

    // 적용 여부를 반드시 직접 선택. 오타·중복·알 수 없는 인수 거절
    private static Map<String, String> options(String[] args) {
        Set<String> paths = Set.of("--catalog-dir", "--documents-dir", "--cards-input", "--cards-dir");
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (values.containsKey(key)) {
                throw new IllegalArgumentException();
            }
            if (key.equals("--validate-only") || key.equals("--apply")) {
                values.put(key, "true");
            } else if (paths.contains(key) && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                values.put(key, args[++i]);
            } else {
                throw new IllegalArgumentException();
            }
        }
        if (!values.containsKey("--catalog-dir")
                || values.containsKey("--validate-only") == values.containsKey("--apply")) {
            throw new IllegalArgumentException();
        }
        return values;
    }

    private static Path path(Map<String, String> options, String key) {
        return options.containsKey(key) ? Path.of(options.get(key)) : null;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException();
        }
        return value;
    }
}
