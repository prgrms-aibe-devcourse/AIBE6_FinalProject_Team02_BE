package com.backend_catcheat.domain.made.service;


import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.badge.dto.EquippedBadgeViewDTO;
import com.backend_catcheat.domain.badge.service.EquippedBadgeResolver;
import com.backend_catcheat.domain.made.dto.*;
import com.backend_catcheat.domain.made.entity.MadeDexComment;
import com.backend_catcheat.domain.made.entity.MadeDexCommentLike;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.repository.MadeDexCommentLikeRepository;
import com.backend_catcheat.domain.made.repository.MadeDexCommentRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.user.dto.UserBriefDTO;
import com.backend_catcheat.global.event.CommentCreatedEvent;
import com.backend_catcheat.global.event.CommentLikedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class MadeDexCommentService {

    private final MadeDexCommentRepository madeDexCommentRepository;
    private final MadeDexRecordRepository madeDexRecordRepository;
    private final MadeDexCommentLikeRepository madeDexCommentLikeRepository;
    private final UserRepository userRepository;
    private final EquippedBadgeResolver equippedBadgeResolver;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    // 댓글 생성 메서드
    public MadeDexCommentCreateResponseDTO create(Long userId, Long recordId, MadeDexCommentCreateRequestDTO dto) {
        // 댓글 내용이 비어있을 시 예외처리
        if(dto.content() == null || dto.content().isBlank()) {
            throw new CustomException(ErrorCode.MADE_DEX_COMMENT_CONTENT_REQUIRED);
        }

        MadeDexComment comment = MadeDexComment.write(recordId, userId, dto.content());

        madeDexCommentRepository.save(comment);

        MadeDexRecord record = madeDexRecordRepository.findById(recordId).orElse(null);
        Long recordOwnerId = record == null ? null : record.getAuthorId();

        if(recordOwnerId != null && !recordOwnerId.equals(userId)) {
            applicationEventPublisher.publishEvent(
                    new CommentCreatedEvent(comment.getId(), recordId, record.getMadeDexId(), userId, recordOwnerId));
        }

        return new MadeDexCommentCreateResponseDTO(comment.getId());
    }

    // 게시 카드안에 모든 댓글 조회 메서드
    public List<MadeDexCommentDTO> findByRecord(Long userId, Long recordId) {

        List<MadeDexComment> comments = madeDexCommentRepository.findByMadeDexRecordIdOrderByCreatedAtAsc(recordId);

        List<Long> commentIds = comments.stream()
                .map(MadeDexComment::getId)
                .toList();

        Set<Long> likedCommentIds = madeDexCommentLikeRepository.findByCommentIdInAndUserId(commentIds, userId)
                .stream()
                .map(MadeDexCommentLike::getCommentId)
                .collect(Collectors.toSet());

        List<Long> authorIds = comments.stream()
                .map(MadeDexComment::getAuthorId)
                .distinct()
                .toList();

        List<User> authors = userRepository.findAllById(authorIds);

        Map<Long, User> userById = authors.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, EquippedBadgeViewDTO> badgeById = equippedBadgeResolver.resolveByBadgeId(authors);

        return comments.stream().
                map(c -> {
                    User author = userById.get(c.getAuthorId());
                    UserBriefDTO authorDTO = author == null ? null : new UserBriefDTO(
                            author.getId(),
                            author.getNickname(),
                            s3PresignedUrlService.createDownloadUrl(author.getProfileImageKey()),
                            author.getEquippedBadgeId() == null ? null : badgeById.get(author.getEquippedBadgeId())
                    );
                    return new MadeDexCommentDTO(
                            c.getId(),
                            authorDTO,
                            c.getContent(),
                            c.getLikeCount(),
                            likedCommentIds.contains(c.getId()),
                            c.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional
    // 수정 메서드
    public void update(Long userId, Long commentId, MadeDexCommentUpdateRequestDTO dto) {
        // findById 로 MadeDexComment 객체를 찾는다
        MadeDexComment comment = madeDexCommentRepository.findById(commentId).
                // 해당 ID를 가진 객체가 없을시 예외처리
                orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_COMMENT_NOT_FOUND));

        // 작성자 외에 접근 예외처리
        if(!comment.isAuthor(userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_COMMENT_NOT_AUTHOR);
        }

        comment.update(dto.content());
    }

    @Transactional
    // 삭제 메서드 각 코드는 삭제와 비슷한것 같음!
    public void delete(Long userId, Long commentId) {

        MadeDexComment comment = madeDexCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_COMMENT_NOT_FOUND));

        if(!comment.isAuthor(userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_COMMENT_NOT_AUTHOR);
        }

        madeDexCommentLikeRepository.deleteByCommentId(commentId);
        madeDexCommentRepository.delete(comment);
    }

    @Transactional
    public MadeDexCommentLikeResponseDTO toggleLike(Long userId, Long commentId) {

        MadeDexComment comment = madeDexCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_COMMENT_NOT_FOUND));

        return madeDexCommentLikeRepository.findByCommentIdAndUserId(commentId, userId)
                .map(like -> {
                    madeDexCommentLikeRepository.delete(like);
                    comment.decreaseLike();
                    return new MadeDexCommentLikeResponseDTO(false, comment.getLikeCount());
                })
                .orElseGet(() -> {
                    madeDexCommentLikeRepository.save(MadeDexCommentLike.of(commentId, userId));
                    comment.increaseLike();

                    if (!comment.isAuthor(userId)) {
                        Long madeDexId = madeDexRecordRepository.findById(comment.getMadeDexRecordId())
                                .map(MadeDexRecord::getMadeDexId)
                                .orElse(null);
                        applicationEventPublisher.publishEvent(new CommentLikedEvent(
                                commentId, comment.getMadeDexRecordId(), madeDexId, userId, comment.getAuthorId()));
                    }

                    return new MadeDexCommentLikeResponseDTO(true, comment.getLikeCount());
                });


    }

}
