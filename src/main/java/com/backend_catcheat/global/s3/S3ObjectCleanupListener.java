package com.backend_catcheat.global.s3;

import com.backend_catcheat.domain.upload.service.UploadObjectService;
import com.backend_catcheat.global.event.S3ObjectUnusedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쓰이지 않게 된 S3 객체 정리
 */
@Component
@RequiredArgsConstructor
public class S3ObjectCleanupListener {

    private final S3PresignedUrlService s3PresignedUrlService;
    private final UploadObjectService uploadObjectService;

    // 트랜잭션 안에서 지우면 뒤이어 커밋이 실패했을 때 DB는 옛 key를 가리키는데 객체가 없다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnused(S3ObjectUnusedEvent event) {
        // 객체가 남았는데 발급 기록만 지우면, 주인이 없는 key가 되어 아무나 자기 기록에 붙일 수 있다
        if (s3PresignedUrlService.deleteObject(event.objectKey())) {
            uploadObjectService.forget(event.objectKey());
        }
    }
}
