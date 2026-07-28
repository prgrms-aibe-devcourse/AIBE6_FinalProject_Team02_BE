package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.entity.ReviewQueueItem;
import com.backend_catcheat.domain.admin.entity.ReviewStatus;
import com.backend_catcheat.domain.admin.repository.ReviewQueueItemRepository;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.registration.entity.Registration;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수동 폴백의 핵심 규칙: **수락하는 순간이 해금 시점이다.**
 * 그전까지 카드는 칸에 붙지 않고 수집률에도 잡히지 않는다.
 */
@DisplayName("관리자 검토")
class ReviewServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long REGISTRATION_ID = 100L;
    private static final Long CARD_ID = 500L;
    private static final Long REVIEW_ID = 900L;
    private static final Long SLOT_ID = 1L;

    private ReviewQueueItemRepository reviewQueueRepository;
    private CollectionCardRepository cardRepository;
    private UserCollectionRepository userCollectionRepository;
    private RegistrationRepository registrationRepository;

    private ReviewService service;
    private ReviewQueueItem item;
    private CollectionCard card;

    @BeforeEach
    void setUp() {
        reviewQueueRepository = mock(ReviewQueueItemRepository.class);
        cardRepository = mock(CollectionCardRepository.class);
        userCollectionRepository = mock(UserCollectionRepository.class);
        registrationRepository = mock(RegistrationRepository.class);

        service = new ReviewService(
                reviewQueueRepository, cardRepository, userCollectionRepository, registrationRepository,
                mock(BasicDexRepository.class), mock(PhotoRepository.class), mock(S3PresignedUrlService.class));

        card = CollectionCard.awaitingReview(
                REGISTRATION_ID, SLOT_ID, 1L, "메모", null, null, null, LocalDateTime.now());
        ReflectionTestUtils.setField(card, "id", CARD_ID);

        item = ReviewQueueItem.pending(REGISTRATION_ID, CARD_ID, 1L);
        ReflectionTestUtils.setField(item, "id", REVIEW_ID);

        when(reviewQueueRepository.findById(REVIEW_ID)).thenReturn(Optional.of(item));
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(registrationRepository.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(Registration.start(USER_ID, "uploads/a.jpg")));
        when(userCollectionRepository.save(any(UserCollection.class))).thenAnswer(i -> {
            UserCollection saved = i.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
    }

    @Test
    @DisplayName("등록 직후에는 칸에 붙어 있지 않다 — 수집률에 잡히지 않는다")
    void 등록_직후에는_해금되지_않는다() {
        assertThat(card.isAwaitingReview()).isTrue();
        assertThat(card.getUserCollectionId()).isNull();
    }

    @Test
    @DisplayName("수락하면 칸이 열리고 카드가 붙는다 — 최초면 별 1개")
    void 수락하면_해금된다() {
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, SLOT_ID)).thenReturn(Optional.empty());

        service.approve(REVIEW_ID);

        assertThat(card.isAwaitingReview()).isFalse();
        assertThat(card.getUserCollectionId()).isEqualTo(42L);
        assertThat(item.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(item.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("대기 중 같은 칸이 먼저 열려 있었으면 별이 오른다 — 랭크는 수락 시점에 정한다")
    void 이미_열린_칸이면_별이_오른다() {
        UserCollection existing = UserCollection.unlock(USER_ID, SLOT_ID, LocalDateTime.now());
        ReflectionTestUtils.setField(existing, "id", 42L);
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, SLOT_ID)).thenReturn(Optional.of(existing));

        service.approve(REVIEW_ID);

        assertThat(existing.getRank()).isEqualTo(2);
        assertThat(card.getUserCollectionId()).isEqualTo(42L);
        verify(userCollectionRepository, never()).save(any(UserCollection.class));
    }

    @Test
    @DisplayName("반려하면 칸은 끝내 열리지 않는다 — 카드는 남는다")
    void 반려하면_열리지_않는다() {
        service.reject(REVIEW_ID);

        assertThat(item.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(card.isAwaitingReview()).isTrue();
        verify(userCollectionRepository, never()).save(any(UserCollection.class));
    }

    @Test
    @DisplayName("이미 처리한 건은 다시 처리할 수 없다 — 두 번 수락하면 별이 두 번 오른다")
    void 이중_처리_거부() {
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, SLOT_ID)).thenReturn(Optional.empty());
        service.approve(REVIEW_ID);

        assertThatThrownBy(() -> service.approve(REVIEW_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_ALREADY_HANDLED);
    }

    @Test
    @DisplayName("없는 검토 항목은 404")
    void 없는_항목() {
        when(reviewQueueRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(404L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_ITEM_NOT_FOUND);
    }
}
