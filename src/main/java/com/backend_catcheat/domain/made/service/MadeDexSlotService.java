package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexSlotDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotDeleteResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexSlotService {

    private final MadeDexSlotRepository madeDexSlotRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexFinder madeDexFinder;
    private final Clock clock;

    public List<MadeDexSlotDTO> findSlots(Long userId, Long madeDexId) {
        madeDexFinder.readable(userId, madeDexId);

        Set<Long> withRecords = new HashSet<>(madeDexSlotRepository.findSlotIdsWithRecords(madeDexId));
        return madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(madeDexId).stream()
                .map(slot -> toDTO(slot, withRecords.contains(slot.getId())))
                .toList();
    }

    @Transactional
    public MadeDexSlotDTO add(Long userId, Long madeDexId, String rawName) {
        // 개수 판정과 저장 사이에 끼어들면 7번째 슬롯이 생긴다
        madeDexFinder.locked(madeDexId);
        requireMember(madeDexId, userId);

        String name = requireName(rawName);
        if (madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(madeDexId) >= MadeDexSlot.MAX_SLOTS) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_LIMIT_EXCEEDED);
        }
        requireNameNotTaken(madeDexId, name);

        MadeDexSlot slot = madeDexSlotRepository.save(
                MadeDexSlot.of(madeDexId, name, nextSortOrder(madeDexId)));
        return toDTO(slot, false);
    }

    @Transactional
    public MadeDexSlotDTO rename(Long userId, Long madeDexId, Long slotId, String rawName) {
        // 중복 검사와 저장 사이에 끼어들면 둘 다 통과해 유니크 인덱스에서 터진다
        madeDexFinder.locked(madeDexId);
        requireMember(madeDexId, userId);

        MadeDexSlot slot = slotOf(madeDexId, slotId);
        String name = requireName(rawName);
        if (!name.equals(slot.getName())) {
            requireNameNotTaken(madeDexId, name);
            slot.rename(name);
        }
        return toDTO(slot);
    }

    /** 보이는 슬롯 전체를 새 순서로 다시 매긴다. 숨긴 슬롯은 순서가 없어 대상에서 빠진다 */
    @Transactional
    public List<MadeDexSlotDTO> reorder(Long userId, Long madeDexId, List<Long> slotIds) {
        madeDexFinder.locked(madeDexId);
        requireMember(madeDexId, userId);

        List<MadeDexSlot> visible =
                madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(madeDexId);
        requireSameSlots(visible, slotIds);

        Map<Long, MadeDexSlot> byId = visible.stream()
                .collect(Collectors.toMap(MadeDexSlot::getId, Function.identity()));
        for (int order = 0; order < slotIds.size(); order++) {
            byId.get(slotIds.get(order)).moveTo(order);
        }

        Set<Long> withRecords = new HashSet<>(madeDexSlotRepository.findSlotIdsWithRecords(madeDexId));
        return visible.stream()
                .sorted(Comparator.comparingInt(MadeDexSlot::getSortOrder))
                .map(slot -> toDTO(slot, withRecords.contains(slot.getId())))
                .toList();
    }

    /** 기록이 하나라도 있으면 지우지 않고 숨긴다. 응답의 hidden이 어느 쪽인지 알려준다 */
    @Transactional
    public MadeDexSlotDeleteResponseDTO delete(Long userId, Long madeDexId, Long slotId) {
        madeDexFinder.locked(madeDexId);
        requireMember(madeDexId, userId);

        MadeDexSlot slot = slotOf(madeDexId, slotId);
        if (slot.isHidden()) {
            return new MadeDexSlotDeleteResponseDTO(true);
        }
        if (madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(madeDexId) <= MadeDexSlot.MIN_SLOTS) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_LAST_ONE);
        }

        // 작성자가 지운 기록도 이 슬롯을 참조하므로, 남아 있으면 지울 수 없다
        if (madeDexSlotRepository.existsRecordReferencing(slotId)) {
            slot.hide(LocalDateTime.now(clock));
            return new MadeDexSlotDeleteResponseDTO(true);
        }

        madeDexSlotRepository.delete(slot);
        return new MadeDexSlotDeleteResponseDTO(false);
    }

    @Transactional
    public MadeDexSlotDTO restore(Long userId, Long madeDexId, Long slotId) {
        madeDexFinder.locked(madeDexId);
        requireMember(madeDexId, userId);

        MadeDexSlot slot = slotOf(madeDexId, slotId);
        if (!slot.isHidden()) {
            return toDTO(slot);
        }
        if (madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(madeDexId) >= MadeDexSlot.MAX_SLOTS) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_LIMIT_EXCEEDED);
        }
        // 숨어 있는 동안에는 유니크 인덱스가 비껴가므로 같은 이름이 새로 생겼을 수 있다
        requireNameNotTaken(madeDexId, slot.getName());

        // restore() 뒤에 부르면 flush된 자기 자신이 최대값 계산에 섞인다
        int lastOrder = nextSortOrder(madeDexId);
        slot.restore();
        slot.moveTo(lastOrder);
        return toDTO(slot);
    }

    /** 화면이 들고 있던 목록과 어긋나면 빠진 슬롯이 맨 뒤로 밀리므로 다시 받아 가게 한다 */
    private void requireSameSlots(List<MadeDexSlot> visible, List<Long> slotIds) {
        Set<Long> requested = slotIds == null ? Set.of() : new HashSet<>(slotIds);
        if (slotIds == null
                || requested.size() != slotIds.size()
                || requested.size() != visible.size()
                || !visible.stream().allMatch(slot -> requested.contains(slot.getId()))) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_ORDER_MISMATCH);
        }
    }

    private MadeDexSlot slotOf(Long madeDexId, Long slotId) {
        MadeDexSlot slot = madeDexSlotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_SLOT_NOT_FOUND));
        // 남의 그룹 구성이 드러나지 않도록 없는 것과 같게 답한다
        if (!slot.belongsTo(madeDexId)) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_NOT_FOUND);
        }
        return slot;
    }

    private void requireMember(Long madeDexId, Long userId) {
        if (!madeDexMemberRepository.existsByMadeDexIdAndUserId(madeDexId, userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_NOT_MEMBER);
        }
    }

    private void requireNameNotTaken(Long madeDexId, String name) {
        if (madeDexSlotRepository.existsByMadeDexIdAndNameAndHiddenAtIsNull(madeDexId, name)) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_NAME_DUPLICATED);
        }
    }

    private int nextSortOrder(Long madeDexId) {
        return madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(madeDexId)
                .stream()
                .mapToInt(MadeDexSlot::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private String requireName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_NAME_REQUIRED);
        }
        String name = rawName.trim();
        if (name.length() > MadeDexSlot.NAME_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_NAME_TOO_LONG);
        }
        return name;
    }

    private MadeDexSlotDTO toDTO(MadeDexSlot slot) {
        return toDTO(slot, madeDexSlotRepository.countRecords(slot.getId()) > 0);
    }

    private MadeDexSlotDTO toDTO(MadeDexSlot slot, boolean hasRecords) {
        return new MadeDexSlotDTO(
                slot.getId(),
                slot.getName(),
                slot.getSortOrder(),
                slot.isHidden(),
                hasRecords);
    }
}
