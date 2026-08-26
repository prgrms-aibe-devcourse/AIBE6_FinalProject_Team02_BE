package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.config.TimeConfig;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 피드가 필요한 것을 **한 트랜잭션 안에서 전부 읽어 온다.** DTO 조립은 하지 않는다.
 *
 * ## 왜 서비스에서 떼어 냈나
 *
 * 예전에는 조회와 조립이 한 메서드(`MadeDexFeedService.findFeed`)에 있었고, 그 메서드에
 * `@Transactional(readOnly = true)`가 걸려 있었다. 그래서 **DTO를 만드는 동안에도 DB 커넥션이
 * 반납되지 않았다.**
 *
 * 문제는 조립 비용이 작지 않다는 것이다. 응답에 실리는 이미지마다 presigned URL을 새로 서명하는데,
 * 이 작업은 **DB를 한 번도 쓰지 않는 순수 CPU 작업**이다. 멤버 12명 × 슬롯 4개 기준으로 서명이
 * 60회 돌고 약 117ms가 걸린다. 그동안 커넥션은 아무 일도 시키지 않으면서 붙잡혀 있었다.
 *
 * 부하테스트(VU 150)에서 이렇게 나타났다:
 *
 *   커넥션 사용 10/10 · 대기 112 · 획득 대기 6.03초 · p95 6.85초 · CPU는 35%
 *
 * CPU가 노는데 느렸다. 커넥션풀(기본 10)이 요청당 137ms씩 점유되어 말라붙은 것이다.
 * 측정 기록은 docs/로그잇-피드-부하테스트-측정기록.md 에 있다.
 *
 * ## 왜 별도 빈인가
 *
 * 같은 클래스 안에서 `this.load()`를 부르면 스프링 프록시를 타지 않아 `@Transactional`이
 * **조용히 무시된다.** 트랜잭션 경계를 실제로 만들려면 다른 빈이어야 한다.
 *
 * ## 엔티티를 그대로 돌려줘도 되는 이유
 *
 * 트랜잭션이 끝나면 여기서 돌려준 엔티티는 준영속(detached)이 된다. 그래도 안전하다 —
 * 이 도메인 엔티티에는 연관관계 매핑이 하나도 없고(전부 Long id 참조), `open-in-view: false`라
 * 지연 로딩으로 커넥션을 다시 잡는 경로 자체가 없다. 읽는 값은 전부 기본 컬럼이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexFeedLoader {

    private final MadeDexSlotRepository madeDexSlotRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexRecordRepository madeDexRecordRepository;
    private final MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    private final MadeDexFinder madeDexFinder;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 열람 권한 확인 → 날짜 확정 → 필요한 행을 전부 읽는다. 쿼리는 일곱 번으로 고정이다.
     *
     * 권한 확인이 날짜 검증보다 **먼저**여야 한다. 순서를 바꾸면 남의 도감에 미래 날짜를 넣었을 때
     * 404 대신 "미래 날짜" 오류가 나가서 그 도감이 존재한다는 사실이 드러난다.
     */
    public FeedData load(Long userId, Long madeDexId, LocalDate date) {
        madeDexFinder.readable(userId, madeDexId);

        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE));
        LocalDate loggedOn = date == null ? today : date;
        if (loggedOn.isAfter(today)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        }

        List<MadeDexRecord> records = madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(madeDexId, loggedOn);

        List<MadeDexMember> members =
                madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(madeDexId);

        Map<Long, User> userById = userRepository
                .findAllById(members.stream().map(MadeDexMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, List<MadeDexRecordPhoto>> photosByRecord = records.isEmpty()
                ? Map.of()
                : madeDexRecordPhotoRepository
                        .findByRecordIdInOrderBySortOrderAsc(records.stream().map(MadeDexRecord::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(MadeDexRecordPhoto::getRecordId));

        List<MadeDexSlot> slots = madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(madeDexId);

        return new FeedData(loggedOn, today, records, members, userById, photosByRecord, slots);
    }

    /** 조립에 필요한 원재료. 여기까지가 DB의 몫이고, 이후는 커넥션 없이 진행된다 */
    public record FeedData(
            LocalDate loggedOn,
            LocalDate today,
            List<MadeDexRecord> records,
            List<MadeDexMember> members,
            Map<Long, User> userById,
            Map<Long, List<MadeDexRecordPhoto>> photosByRecord,
            List<MadeDexSlot> slots
    ) {}
}
