package com.shortsb.recommendation.service;

import com.shortsb.common.exception.ApiException;
import com.shortsb.recommendation.dto.PreviewState;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
// 현재 서버 세션에 최대 10개 발췌 참조 보관. 본문·벡터 복사 없음
public class PreviewStore {

    private static final String ATTRIBUTE = PreviewStore.class.getName() + ".history";
    private static final int LIMIT = 10;

    public void save(HttpSession session, PreviewState state) {
        if (session == null) {
            throw loginRequired();
        }
        try {
            synchronized (session) {
                History history = (History) session.getAttribute(ATTRIBUTE);
                if (history == null) {
                    history = new History();
                    session.setAttribute(ATTRIBUTE, history);
                }
                history.items.put(state.previewId(), state);
                while (history.items.size() > LIMIT) {
                    history.items.remove(history.items.keySet().iterator().next());
                }
            }
        } catch (IllegalStateException exception) {
            throw loginRequired();
        }
    }

    public PreviewState get(HttpSession session, long userId, UUID previewId) {
        if (session == null) {
            throw missing();
        }
        try {
            synchronized (session) {
                History history = (History) session.getAttribute(ATTRIBUTE);
                PreviewState state = history == null ? null : history.items.get(previewId);
                if (state == null || state.userId() != userId) {
                    throw missing();
                }
                return state;
            }
        } catch (IllegalStateException exception) {
            throw missing();
        }
    }

    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "PREVIEW_NOT_FOUND", "발췌가 만료됐거나 없습니다. 새 추천을 요청해주세요.");
    }

    private ApiException loginRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private static class History implements Serializable {
        private final Map<UUID, PreviewState> items = new LinkedHashMap<>();
    }
}
