package com.backend_catcheat.domain.registration.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재분석 상한 2회는 §5.2의 확정 사항이다.
 * 클라이언트가 세면 새로고침으로 우회되므로 이 규칙은 서버에만 있어야 한다.
 */
@DisplayName("등록 건 — 재분석 상한")
class RegistrationTest {

    private static final Long USER_ID = 7L;

    @Test
    @DisplayName("최초 검증은 재시도가 아니다 — 1회차, 남은 재시도 2회")
    void 최초_검증은_재시도가_아니다() {
        Registration registration = Registration.start(USER_ID, "uploads/a.jpg");

        assertThat(registration.currentAttemptNo()).isEqualTo(1);
        assertThat(registration.retriesLeft()).isEqualTo(2);
        assertThat(registration.canRetry()).isTrue();
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.DRAFT);
    }

    @Test
    @DisplayName("재시도 2회까지만 허용한다")
    void 재시도는_두_번까지() {
        Registration registration = Registration.start(USER_ID, "uploads/a.jpg");

        registration.retryWith("uploads/b.jpg");
        assertThat(registration.currentAttemptNo()).isEqualTo(2);
        assertThat(registration.retriesLeft()).isEqualTo(1);
        assertThat(registration.canRetry()).isTrue();

        registration.retryWith("uploads/c.jpg");
        assertThat(registration.currentAttemptNo()).isEqualTo(3);
        assertThat(registration.retriesLeft()).isZero();
        // 3회차까지 쓰고 나면 더는 못 한다 — 여기서 수동 폴백으로 간다
        assertThat(registration.canRetry()).isFalse();
    }

    @Test
    @DisplayName("남은 횟수는 음수가 되지 않는다")
    void 남은_횟수는_음수가_되지_않는다() {
        Registration registration = Registration.start(USER_ID, "uploads/a.jpg");
        registration.retryWith("uploads/b.jpg");
        registration.retryWith("uploads/c.jpg");
        registration.retryWith("uploads/d.jpg"); // 상한 검사를 건너뛴 경우에도 표시는 0

        assertThat(registration.retriesLeft()).isZero();
    }

    @Test
    @DisplayName("재시도하면 분석 사진이 갱신된다 — 사진을 바꿔 다시 시도하는 경로가 있다")
    void 재시도하면_분석_사진이_갱신된다() {
        Registration registration = Registration.start(USER_ID, "uploads/a.jpg");
        assertThat(registration.getAnalysisPhotoKey()).isEqualTo("uploads/a.jpg");

        registration.retryWith("uploads/b.jpg");

        assertThat(registration.getAnalysisPhotoKey()).isEqualTo("uploads/b.jpg");
    }

    @Test
    @DisplayName("소유자만 이어서 진행할 수 있다")
    void 소유자만_이어서_진행한다() {
        Registration registration = Registration.start(USER_ID, "uploads/a.jpg");

        assertThat(registration.isOwnedBy(USER_ID)).isTrue();
        assertThat(registration.isOwnedBy(999L)).isFalse();
    }
}
