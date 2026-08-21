package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.LikedLogitRecordResponseDTO;
import com.backend_catcheat.domain.made.dto.MyLogitCommentResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexComment;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordLike;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexCommentRepository;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordLikeRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 마이 -> 내 활동의 로그잇 쪽 조회 */
@Service
@RequiredArgsConstructor
public class MadeDexActivityService {
    private final MadeDexMemberRepository memberRepository;
    private final MadeDexRepository madeDexRepository;
    private final MadeDexRecordRepository recordRepository;
    private final MadeDexRecordLikeRepository recordLikeRepository;
    private final MadeDexCommentRepository commentRepository;
    private final MadeDexRecordPhotoRepository photoRepository;
    private final MadeDexSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    /** 내가 쓴 로그잇 댓글 전부, 최신순 */
    @Transactional(readOnly = true)
    public List<MyLogitCommentResponseDTO> getMyComments(Long userId) {
        Map<Long, String> dexNames = liveDexNames(userId);
        if (dexNames.isEmpty()) return List.of();

        List<MadeDexComment> comments = commentRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        if (comments.isEmpty()) return List.of();

        Map<Long, MadeDexRecord> records = liveRecords(
                comments.stream().map(MadeDexComment::getMadeDexRecordId).distinct().toList(), dexNames);
        if (records.isEmpty()) return List.of();

        Map<Long, String> slotNames = slotNames(records.values());

        return comments.stream()
                .map(c -> {
                    MadeDexRecord r = records.get(c.getMadeDexRecordId());
                    if (r == null) return null;   // 삭제된 기록 · 삭제된 로그잇 · 내가 나간 로그잇
                    return new MyLogitCommentResponseDTO(
                            c.getId(),
                            r.getMadeDexId(),
                            dexNames.get(r.getMadeDexId()),
                            r.getId(),
                            r.getLoggedOn(),
                            slotNames.get(r.getSlotId()),
                            c.getContent(),
                            c.getLikeCount(),
                            c.getCreatedAt(),
                            c.getUpdatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** 내가 좋아요한 로그잇 기록 전부, 내가 누른 순 */
    @Transactional(readOnly = true)
    public List<LikedLogitRecordResponseDTO> getLikedRecords(Long userId) {
        Map<Long, String> dexNames = liveDexNames(userId);
        if (dexNames.isEmpty()) return List.of();

        List<MadeDexRecordLike> likes = recordLikeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (likes.isEmpty()) return List.of();

        Map<Long, MadeDexRecord> records = liveRecords(
                likes.stream().map(MadeDexRecordLike::getRecordId).distinct().toList(), dexNames);
        if (records.isEmpty()) return List.of();

        Map<Long, String> slotNames = slotNames(records.values());
        Map<Long, MadeDexRecordPhoto> thumbnails = thumbnails(records.keySet());
        Map<Long, User> authors = userRepository
                .findAllById(records.values().stream().map(MadeDexRecord::getAuthorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return likes.stream()
                .map(like -> {
                    MadeDexRecord r = records.get(like.getRecordId());
                    if (r == null) return null;
                    User author = authors.get(r.getAuthorId());
                    MadeDexRecordPhoto photo = thumbnails.get(r.getId());
                    return new LikedLogitRecordResponseDTO(
                            r.getId(),
                            r.getMadeDexId(),
                            dexNames.get(r.getMadeDexId()),
                            r.getLoggedOn(),
                            slotNames.get(r.getSlotId()),
                            r.getAuthorId(),
                            author == null ? null : author.getNickname(),
                            author == null ? null : s3PresignedUrlService.createDownloadUrl(author.getProfileImageKey()),
                            photo == null ? null : s3PresignedUrlService.createDownloadUrl(photo.getImageKey()),
                            // 사진이 없으면 썸네일도 null이라 FE가 읽지 않지만 기본값을 맞춰 둠
                            photo == null ? MadeDexRecordPhoto.CROP_DEFAULT : photo.getCropX(),
                            photo == null ? MadeDexRecordPhoto.CROP_DEFAULT : photo.getCropY(),
                            r.getLikeCount(),
                            like.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** 지금 내가 멤버이고 살아 있는 로그잇의 id -> 이름 */
    private Map<Long, String> liveDexNames(Long userId) {
        List<Long> myDexIds = memberRepository.findByUserId(userId).stream()
                .map(MadeDexMember::getMadeDexId)
                .toList();
        if (myDexIds.isEmpty()) return Map.of();

        return madeDexRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(myDexIds).stream()
                .collect(Collectors.toMap(MadeDex::getId, MadeDex::getName));
    }

    /** 살아 있고 내가 볼 수 있는 로그잇에 속한 기록만 id -> 기록 */
    private Map<Long, MadeDexRecord> liveRecords(List<Long> recordIds, Map<Long, String> dexNames) {
        return recordRepository.findByIdInAndDeletedAtIsNull(recordIds).stream()
                .filter(r -> dexNames.containsKey(r.getMadeDexId()))
                .collect(Collectors.toMap(MadeDexRecord::getId, r -> r));
    }

    private Map<Long, String> slotNames(Collection<MadeDexRecord> records) {
        List<Long> slotIds = records.stream().map(MadeDexRecord::getSlotId).distinct().toList();
        return slotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(MadeDexSlot::getId, MadeDexSlot::getName));
    }

    /** 기록마다 대표 사진 한 장 — sort_order가 가장 앞선 것 */
    private Map<Long, MadeDexRecordPhoto> thumbnails(Collection<Long> recordIds) {
        return photoRepository.findByRecordIdInOrderBySortOrderAsc(recordIds).stream()
                .collect(Collectors.toMap(MadeDexRecordPhoto::getRecordId, p -> p, (first, later) -> first));
    }
}
