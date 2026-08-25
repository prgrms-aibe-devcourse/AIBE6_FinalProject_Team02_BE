package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.made.dto.MadeDexFeedCardDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedSlotDTO;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.service.MadeDexFeedLoader.FeedData;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 하루치 식탁을 화면 모양으로 조립한다.
 *
 * ## 이 클래스에는 @Transactional이 없다 — 의도된 것이다
 *
 * DB 읽기는 전부 {@link MadeDexFeedLoader}가 한 트랜잭션 안에서 끝내고, 여기서는
 * **커넥션을 놓은 뒤에** DTO를 만든다.
 *
 * 조립 과정에는 이미지마다 presigned URL을 만드는 서명 작업이 들어간다. 이건 DB를 쓰지 않는
 * 순수 CPU 작업인데, 예전에는 트랜잭션 안에 있어서 그동안 커넥션을 붙잡고 있었다.
 * 멤버 12명 × 슬롯 4개면 서명이 60회 돌고 약 117ms가 걸린다 —
 * 요청 하나가 커넥션을 137ms 점유하는데 그중 85%가 DB와 무관한 시간이었다.
 *
 * 부하테스트(VU 150)에서 커넥션 10개가 전부 차고 112개가 대기하며 p95가 6.85초까지 갔다.
 * CPU는 35%로 놀고 있었다. 자세한 수치는 docs/로그잇-피드-부하테스트-측정기록.md 참고.
 *
 * **@Transactional을 다시 붙이면 그 상태로 돌아간다.**
 */
@Service
@RequiredArgsConstructor
public class MadeDexFeedService {

    private final MadeDexFeedLoader madeDexFeedLoader;
    private final S3PresignedUrlService s3PresignedUrlService;

    /**
     * 하루치 식탁. 슬롯마다 멤버 카드가 놓이고, 기록이 없는 멤버도 빈 카드로 자리를 남긴다.
     * 쿼리는 로더가 일곱 번으로 고정하고, 이 아래로는 DB 접근이 없다.
     */
    public MadeDexFeedDTO findFeed(Long userId, Long madeDexId, LocalDate date) {
        FeedData data = madeDexFeedLoader.load(userId, madeDexId, date);
        // ── 여기부터 커넥션 없음. 남은 일은 서명과 조립뿐이다 ──

        List<MadeDexFeedCardDTO> emptyCards = emptyCards(data, userId);
        Map<Long, Map<Long, List<MadeDexRecord>>> bySlotAndAuthor = groupBySlotAndAuthor(data.records());
        Photos photos = new Photos(data.photosByRecord());

        return new MadeDexFeedDTO(data.loggedOn(), data.today(), visibleSlots(data).stream()
                .map(slot -> new MadeDexFeedSlotDTO(
                        slot.getId(),
                        slot.getName(),
                        slot.isHidden(),
                        cardsOf(emptyCards, bySlotAndAuthor.getOrDefault(slot.getId(), Map.of()), photos)))
                .toList());
    }

    /**
     * 숨긴 슬롯은 하루 화면에서 빠지지만, 그날 기록이 남아 있으면 보여 준다.
     * 숨겼다고 과거의 기록이 사라지면 안 된다.
     */
    private List<MadeDexSlot> visibleSlots(FeedData data) {
        Set<Long> usedSlotIds = data.records().stream()
                .map(MadeDexRecord::getSlotId)
                .collect(Collectors.toSet());
        return data.slots().stream()
                .filter(slot -> !slot.isHidden() || usedSlotIds.contains(slot.getId()))
                .toList();
    }

    /**
     * 멤버 카드 뼈대. 내가 항상 첫 장이고 나머지는 가입 순서다.
     *
     * 프로필 서명은 여기서 **멤버당 한 번만** 돈다. 아래 cardsOf가 슬롯마다 이 목록을 재사용하므로
     * 서명 횟수는 슬롯 수만큼 늘지 않는다 (멤버 12명·슬롯 4개면 프로필 서명은 48회가 아니라 12회).
     */
    private List<MadeDexFeedCardDTO> emptyCards(FeedData data, Long userId) {
        return data.members().stream()
                .sorted(Comparator.comparing((MadeDexMember member) ->
                        member.getUserId().equals(userId)).reversed())
                .map(member -> {
                    User user = data.userById().get(member.getUserId());
                    return new MadeDexFeedCardDTO(
                            member.getUserId(),
                            user == null ? null : user.getNickname(),
                            user == null ? null : s3PresignedUrlService.createDownloadUrl(user.getProfileImageKey()),
                            member.getUserId().equals(userId),
                            0, null, 50, 50, List.of(), null);
                })
                .toList();
    }

    private List<MadeDexFeedCardDTO> cardsOf(List<MadeDexFeedCardDTO> emptyCards,
                                             Map<Long, List<MadeDexRecord>> byAuthor,
                                             Photos photos) {
        return emptyCards.stream()
                .map(card -> {
                    List<MadeDexRecord> mine = byAuthor.get(card.userId());
                    return mine == null ? card : toCard(card, mine, photos);
                })
                .toList();
    }

    /**
     * 한 사람이 한 슬롯에 남긴 것을 카드 한 장으로 접는다.
     * 대표 사진은 가장 먼저 남긴 기록의 첫 장이다.
     */
    private MadeDexFeedCardDTO toCard(MadeDexFeedCardDTO card, List<MadeDexRecord> records, Photos photos) {
        List<Long> recordIds = records.stream().map(MadeDexRecord::getId).toList();
        MadeDexRecord cover = records.stream()
                .filter(record -> photos.firstKeyOf(record.getId()) != null)
                .findFirst()
                .orElse(records.getFirst());
        MadeDexRecordPhoto coverPhoto = photos.firstOf(cover.getId());
        String thumbnailKey = coverPhoto == null ? null : coverPhoto.getImageKey();
        double cropX = coverPhoto == null ? 50 : coverPhoto.getCropX();
        double cropY = coverPhoto == null ? 50 : coverPhoto.getCropY();

        return new MadeDexFeedCardDTO(
                card.userId(),
                card.nickname(),
                card.profileImageUrl(),
                card.me(),
                records.size(),
                s3PresignedUrlService.createDownloadUrl(thumbnailKey),
                cropX,
                cropY,
                recordIds,
                cover.getLoggedAt());
    }

    private Map<Long, Map<Long, List<MadeDexRecord>>> groupBySlotAndAuthor(List<MadeDexRecord> records) {
        return records.stream().collect(Collectors.groupingBy(
                MadeDexRecord::getSlotId,
                Collectors.groupingBy(MadeDexRecord::getAuthorId)));
    }

    private record Photos(Map<Long, List<MadeDexRecordPhoto>> byRecord) {

        MadeDexRecordPhoto firstOf(Long recordId) {
            List<MadeDexRecordPhoto> photos = byRecord.get(recordId);
            return photos == null || photos.isEmpty() ? null : photos.getFirst();
        }

        String firstKeyOf(Long recordId) {
            MadeDexRecordPhoto first = firstOf(recordId);
            return first == null ? null : first.getImageKey();
        }
    }
}
