package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.basicdex.type.Category;
import com.backend_catcheat.domain.dex.collection.entity.CardPhoto;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CardPhotoRepository;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.registration.config.VisionProperties;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest.CardInput;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmResponse;
import com.backend_catcheat.domain.registration.entity.Photo;
import com.backend_catcheat.domain.registration.entity.Registration;
import com.backend_catcheat.domain.registration.entity.VerificationAttempt;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.domain.registration.repository.VerificationAttemptRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("등록 확정 — 해금")
class RegistrationConfirmServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long REGISTRATION_ID = 100L;
    private static final String ANALYSIS_KEY = "uploads/2026/07/28/analysis.jpg";

    private RegistrationRepository registrationRepository;
    private VerificationAttemptRepository attemptRepository;
    private PhotoRepository photoRepository;
    private BasicDexRepository slotRepository;
    private UserCollectionRepository userCollectionRepository;
    private CollectionCardRepository cardRepository;
    private CardPhotoRepository cardPhotoRepository;
    private FoodRegistrationRequestRepository foodRegistrationRequestRepository;
    private RegistrationPhotoLoader photoLoader;

    private RegistrationConfirmService service;
    private Registration registration;

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        attemptRepository = mock(VerificationAttemptRepository.class);
        photoRepository = mock(PhotoRepository.class);
        slotRepository = mock(BasicDexRepository.class);
        userCollectionRepository = mock(UserCollectionRepository.class);
        cardRepository = mock(CollectionCardRepository.class);
        cardPhotoRepository = mock(CardPhotoRepository.class);
        foodRegistrationRequestRepository = mock(FoodRegistrationRequestRepository.class);
        photoLoader = mock(RegistrationPhotoLoader.class);

        service = new RegistrationConfirmService(
                registrationRepository, attemptRepository, photoRepository, slotRepository,
                userCollectionRepository, cardRepository, cardPhotoRepository, foodRegistrationRequestRepository, photoLoader,
                new VisionProperties(5, 1024, 0.8f, 3_500_000L, 5, true, "none"));

        registration = Registration.start(USER_ID, ANALYSIS_KEY);
        // 저장 전 엔티티는 id가 null이라 조회 스텁이 안 걸린다. 실제 흐름(영속화된 상태)을 재현한다
        ReflectionTestUtils.setField(registration, "id", REGISTRATION_ID);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        when(photoLoader.loadForStorage(anyString())).thenReturn(new byte[]{1, 2, 3});
        when(photoLoader.hash(any())).thenAnswer(invocation -> "hash-" + System.nanoTime());
        when(photoRepository.existsByHash(anyString())).thenReturn(false);
        when(photoRepository.save(any(Photo.class))).thenAnswer(i -> i.getArgument(0));
        when(cardRepository.save(any(CollectionCard.class))).thenAnswer(i -> i.getArgument(0));
        when(userCollectionRepository.save(any(UserCollection.class))).thenAnswer(i -> i.getArgument(0));
        when(userCollectionRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(slotRepository.count()).thenReturn(200L);
    }

    private BasicDexEntity slot(Long id, String name, Category category) {
        BasicDexEntity entity = mock(BasicDexEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getName()).thenReturn(name);
        when(entity.getCategory()).thenReturn(category);
        return entity;
    }

    private void verified(int attemptNo, Long... slotIds) {
        List<VerificationAttempt> attempts = java.util.Arrays.stream(slotIds)
                .map(id -> VerificationAttempt.of(REGISTRATION_ID, attemptNo, id, true, 0.9, ""))
                .toList();
        when(attemptRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(attempts);
    }

    private void slotsExist(BasicDexEntity... slots) {
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(slots));
    }

    private RegistrationConfirmRequest request(CardInput... cards) {
        return new RegistrationConfirmRequest(List.of(cards), null);
    }

    @Test
    @DisplayName("최초 해금이면 별 1개, firstUnlock=true")
    void 최초_해금() {
        verified(1, 1L);
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW));
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, 1L)).thenReturn(Optional.empty());

        RegistrationConfirmResponse response = service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, "맛있었다")));

        assertThat(response.unlocked()).singleElement().satisfies(slot -> {
            assertThat(slot.slotName()).isEqualTo("김치찌개");
            assertThat(slot.rank()).isEqualTo(1);
            assertThat(slot.firstUnlock()).isTrue();
        });
        assertThat(response.totalSlots()).isEqualTo(200L);
        assertThat(registration.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("이미 열린 칸이면 별이 오르고 firstUnlock=false — 수집률은 그대로 (§5.1)")
    void 중복_수집() {
        verified(1, 1L);
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW));
        UserCollection existing = UserCollection.unlock(USER_ID, 1L, LocalDateTime.now());
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, 1L)).thenReturn(Optional.of(existing));

        RegistrationConfirmResponse response = service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null)));

        assertThat(response.unlocked()).singleElement().satisfies(slot -> {
            assertThat(slot.rank()).isEqualTo(2);
            assertThat(slot.firstUnlock()).isFalse();
        });
        // 새 user_collection 행을 만들지 않는다 — 만들면 수집률이 부당하게 오른다
        org.mockito.Mockito.verify(userCollectionRepository, org.mockito.Mockito.never())
                .save(any(UserCollection.class));
    }

    @Test
    @DisplayName("카드 사진을 안 고르면 분석 사진이 자동 첨부된다 (§5.2 등록 이탈 방지)")
    void 사진_미지정시_분석_사진_자동_첨부() {
        verified(1, 1L);
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW));
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, 1L)).thenReturn(Optional.empty());

        service.confirm(USER_ID, REGISTRATION_ID, request(new CardInput(1L, null, null, null)));

        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        org.mockito.Mockito.verify(photoRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo(ANALYSIS_KEY);
    }

    @Test
    @DisplayName("한 상 사진 1장을 여러 카드가 공유해도 photo 행은 하나다")
    void 한_상_사진은_한_행() {
        verified(1, 1L, 2L);
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW), slot(2L, "삼겹살", Category.MEAT_DISH));
        when(userCollectionRepository.findByUserIdAndSlotId(any(), any())).thenReturn(Optional.empty());

        service.confirm(USER_ID, REGISTRATION_ID, request(
                new CardInput(1L, List.of(ANALYSIS_KEY), null, null),
                new CardInput(2L, List.of(ANALYSIS_KEY), null, null)));

        // 사진은 1행, 카드는 2장, 연결은 카드마다 1개씩
        org.mockito.Mockito.verify(photoRepository, org.mockito.Mockito.times(1)).save(any(Photo.class));
        org.mockito.Mockito.verify(cardRepository, org.mockito.Mockito.times(2)).save(any(CollectionCard.class));
        org.mockito.Mockito.verify(cardPhotoRepository, org.mockito.Mockito.times(2)).saveAll(anyList());
    }

    @Test
    @DisplayName("검증을 통과하지 않은 칸은 해금할 수 없다 (§5.2)")
    void 미검증_칸은_거부() {
        verified(1, 1L); // 1번만 통과
        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(2L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLOT_NOT_VERIFIED);
    }

    @Test
    @DisplayName("이전 회차에 통과했어도 최신 회차 기준으로 본다")
    void 최신_회차만_인정한다() {
        // 1회차엔 통과했지만 재시도(2회차)에서 빠진 칸
        when(attemptRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
                VerificationAttempt.of(REGISTRATION_ID, 1, 2L, true, 0.9, ""),
                VerificationAttempt.of(REGISTRATION_ID, 2, 1L, true, 0.9, "")));

        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(2L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLOT_NOT_VERIFIED);
    }

    @Test
    @DisplayName("같은 사진을 다시 등록하면 거부한다 (§5.2 어뷰징 방어)")
    void 중복_사진_거부() {
        verified(1, 1L);
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW));
        when(photoRepository.existsByHash(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_PHOTO);
    }

    @Test
    @DisplayName("이미 확정된 건은 다시 확정할 수 없다 — 별 랭크가 부당하게 오른다")
    void 이미_확정된_건은_거부() {
        registration.complete();

        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("남의 등록 건은 확정할 수 없다 (§7)")
    void 남의_건은_거부() {
        when(registrationRepository.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(Registration.start(999L, ANALYSIS_KEY)));

        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_FORBIDDEN);
    }

    @Test
    @DisplayName("같은 칸을 두 번 등록할 수 없다 — 한 끼에 별이 2칸 오르면 안 된다")
    void 같은_칸_중복_거부() {
        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null), new CardInput(1L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_REQUIRED);
    }

    @Test
    @DisplayName("메모 100자를 넘으면 거부한다 (§5.2)")
    void 메모_길이_초과() {
        verified(1, 1L);
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW));
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, "가".repeat(101)))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMO_TOO_LONG);
    }

    @Test
    @DisplayName("재시도가 남았는데 미통과 칸을 등록하려 하면 거부한다 — 먼저 다시 확인해야 한다")
    void 재시도가_남으면_수동_폴백_불가() {
        when(attemptRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
                VerificationAttempt.of(REGISTRATION_ID, 1, 1L, false, 0.3, "달라 보여요")));

        assertThatThrownBy(() -> service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLOT_NOT_VERIFIED);
    }

    @Test
    @DisplayName("상한을 다 쓰면 미통과 칸도 등록되지만 해금되지 않고 검토 큐로 간다")
    void 상한_소진시_검토_대기() {
        registration.retryWith(ANALYSIS_KEY);
        registration.retryWith(ANALYSIS_KEY);
        when(attemptRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
                VerificationAttempt.of(REGISTRATION_ID, 3, 1L, false, 0.3, "달라 보여요")));
        slotsExist(slot(1L, "김치찌개", Category.SOUP_STEW));

        RegistrationConfirmResponse response = service.confirm(USER_ID, REGISTRATION_ID,
                request(new CardInput(1L, null, null, null)));

        assertThat(response.unlocked()).isEmpty();
        assertThat(response.awaitingReview()).singleElement()
                .satisfies(slot -> assertThat(slot.slotName()).isEqualTo("김치찌개"));

        // 칸이 열리지 않았으므로 user_collection은 생기지 않는다 (수집률 미반영)
        org.mockito.Mockito.verify(userCollectionRepository, org.mockito.Mockito.never())
                .save(any(UserCollection.class));
        // 증빙이 검토 큐에 쌓인다
        org.mockito.Mockito.verify(foodRegistrationRequestRepository)
                .save(any(com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest.class));
    }

    @Test
    @DisplayName("일부만 통과하면 통과분은 해금, 나머지는 검토 대기로 갈린다")
    void 일부_통과시_갈린다() {
        registration.retryWith(ANALYSIS_KEY);
        registration.retryWith(ANALYSIS_KEY);
        when(attemptRepository.findByRegistrationIdOrderByIdAsc(REGISTRATION_ID)).thenReturn(List.of(
                VerificationAttempt.of(REGISTRATION_ID, 3, 1L, true, 0.9, ""),
                VerificationAttempt.of(REGISTRATION_ID, 3, 2L, false, 0.2, "달라 보여요")));
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        BasicDexEntity pork = slot(2L, "삼겹살", Category.MEAT_DISH);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi, pork));
        when(userCollectionRepository.findByUserIdAndSlotId(USER_ID, 1L)).thenReturn(Optional.empty());

        RegistrationConfirmResponse response = service.confirm(USER_ID, REGISTRATION_ID, request(
                new CardInput(1L, null, null, null),
                new CardInput(2L, null, null, null)));

        assertThat(response.unlocked()).singleElement()
                .satisfies(slot -> assertThat(slot.slotName()).isEqualTo("김치찌개"));
        assertThat(response.awaitingReview()).singleElement()
                .satisfies(slot -> assertThat(slot.slotName()).isEqualTo("삼겹살"));
    }
}
