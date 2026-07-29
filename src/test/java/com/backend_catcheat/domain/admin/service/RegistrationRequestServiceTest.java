package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RegistrationRequestService 단위 테스트 — 등록 요청 완료(칸 해금)/반려.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationRequestServiceTest {

    @Mock
    FoodRegistrationRequestRepository requestRepository;
    @Mock
    CollectionCardRepository cardRepository;
    @Mock
    UserCollectionRepository userCollectionRepository;
    @Mock
    RegistrationRepository registrationRepository;
    @Mock
    PhotoRepository photoRepository;
    @Mock
    S3PresignedUrlService presignedUrlService;

    @InjectMocks
    RegistrationRequestService service;

    @Test
    @DisplayName("등록 완료 시 카드가 칸에 붙고(해금) 상태가 COMPLETED로 바뀐다")
    void completeRequest_unlocksAndCompletes() {
        CollectionCard card = CollectionCard.awaitingReview(
                5L, 7L, 1L, null, null, null, null, LocalDateTime.now());
        FoodRegistrationRequest request = FoodRegistrationRequest.builder()
                .description("김치찌개")
                .collectionCardId(100L)
                .build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(cardRepository.findById(100L)).thenReturn(Optional.of(card));

        Registration registration = mock(Registration.class);
        when(registration.getUserId()).thenReturn(10L);
        when(registrationRepository.findById(5L)).thenReturn(Optional.of(registration));

        UserCollection unlocked = mock(UserCollection.class);
        when(unlocked.getId()).thenReturn(500L);
        when(userCollectionRepository.findByUserIdAndSlotId(10L, 7L)).thenReturn(Optional.empty());
        when(userCollectionRepository.save(any())).thenReturn(unlocked);

        service.completeRequest(1L);

        assertThat(request.getStatus()).isEqualTo(RegistrationRequestStatus.COMPLETED);
        assertThat(card.isAwaitingReview()).isFalse();   // 칸에 붙음 = 해금
        verify(userCollectionRepository).save(any());
    }

    @Test
    @DisplayName("반려 시 rejectReason만 채워지고 failureReason은 유지된다")
    void rejectRequest_setsRejectReasonKeepsFailureReason() {
        FoodRegistrationRequest request = FoodRegistrationRequest.builder()
                .description("김치찌개")
                .failureReason("AI 판별 실패")
                .collectionCardId(100L)
                .build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        service.rejectRequest(1L, "등록 불가");

        assertThat(request.getStatus()).isEqualTo(RegistrationRequestStatus.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo("등록 불가");
        assertThat(request.getFailureReason()).isEqualTo("AI 판별 실패");
    }

    @Test
    @DisplayName("없는 등록 요청을 처리하면 REGISTRATION_REQUEST_NOT_FOUND")
    void completeRequest_notFound() {
        when(requestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeRequest(99L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REGISTRATION_REQUEST_NOT_FOUND));
    }
}
