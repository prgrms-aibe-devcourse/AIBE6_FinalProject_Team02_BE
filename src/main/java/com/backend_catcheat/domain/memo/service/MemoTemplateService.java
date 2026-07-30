package com.backend_catcheat.domain.memo.service;

import com.backend_catcheat.domain.memo.dto.MemoTemplateResponse;
import com.backend_catcheat.domain.memo.entity.MemoTemplate;
import com.backend_catcheat.domain.memo.repository.MemoTemplateRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoTemplateService {

    private final MemoTemplateRepository memoTemplateRepository;

    public List<MemoTemplateResponse> findMine(Long userId) {
        return memoTemplateRepository.findByUserIdOrderByLastUsedAtDesc(userId).stream()
                .map(MemoTemplateResponse::from)
                .toList();
    }

    /** 같은 문구가 이미 있으면 새로 만들지 않고 사용 시각만 올린다 */
    @Transactional
    public MemoTemplateResponse save(Long userId, String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (!StringUtils.hasText(content)) {
            throw new CustomException(ErrorCode.MEMO_TEMPLATE_CONTENT_REQUIRED);
        }
        if (content.length() > MemoTemplate.CONTENT_MAX) {
            throw new CustomException(ErrorCode.MEMO_TEMPLATE_TOO_LONG);
        }

        LocalDateTime now = LocalDateTime.now();
        MemoTemplate existing = memoTemplateRepository
                .findByUserIdAndContent(userId, content)
                .orElse(null);
        if (existing != null) {
            existing.markUsed(now);
            return MemoTemplateResponse.from(existing);
        }

        // 개수 제한은 새로 만들 때만 본다 — 이미 가진 문구를 다시 저장하는 건 개수를 늘리지 않는다
        if (memoTemplateRepository.countByUserId(userId) >= MemoTemplate.MAX_COUNT) {
            throw new CustomException(ErrorCode.MEMO_TEMPLATE_LIMIT_EXCEEDED);
        }
        return MemoTemplateResponse.from(
                memoTemplateRepository.save(MemoTemplate.of(userId, content, now)));
    }

    /** 불러 쓴 순간 호출한다. 다음에 열면 맨 위로 올라온다 */
    @Transactional
    public void markUsed(Long userId, Long templateId) {
        findOwned(userId, templateId).markUsed(LocalDateTime.now());
    }

    @Transactional
    public void delete(Long userId, Long templateId) {
        memoTemplateRepository.delete(findOwned(userId, templateId));
    }

    /** 없는 것과 남의 것을 구분하지 않는다 — 남의 템플릿 존재 여부를 알려 줄 이유가 없다 */
    private MemoTemplate findOwned(Long userId, Long templateId) {
        return memoTemplateRepository.findById(templateId)
                .filter(template -> template.ownedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.MEMO_TEMPLATE_NOT_FOUND));
    }
}
