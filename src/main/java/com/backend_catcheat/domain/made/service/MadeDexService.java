package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexService {

    private final MadeDexRepository madeDexRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;

    @Transactional
    public MadeDexCreateResponseDTO create(Long ownerId, MadeDexCreateRequestDTO request) {
        String name = blankToNull(request.name());
        if (name == null) {
            throw new CustomException(ErrorCode.MADE_DEX_NAME_REQUIRED);
        }
        if (name.length() > MadeDex.NAME_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_NAME_TOO_LONG);
        }

        String description = blankToNull(request.description());
        if (description != null && description.length() > MadeDex.DESCRIPTION_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_DESCRIPTION_TOO_LONG);
        }

        Visibility visibility = request.visibility() == null ? Visibility.PRIVATE : request.visibility();

        MadeDex madeDex = madeDexRepository.save(
                MadeDex.open(ownerId, name, description, visibility));
        madeDexMemberRepository.save(
                MadeDexMember.owner(madeDex.getId(), ownerId, LocalDateTime.now()));

        return new MadeDexCreateResponseDTO(madeDex.getId());
    }

    public List<MadeDexSummaryDTO> findMine(Long userId) {
        Map<Long, MadeDexRole> roleByMadeDexId = madeDexMemberRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(MadeDexMember::getMadeDexId, MadeDexMember::getRole));
        if (roleByMadeDexId.isEmpty()) {
            return List.of();
        }

        Set<Long> madeDexIds = roleByMadeDexId.keySet();
        Map<Long, Long> memberCountByMadeDexId =
                madeDexMemberRepository.countByMadeDexIds(madeDexIds).stream()
                        .collect(Collectors.toMap(
                                MadeDexMemberCountDTO::madeDexId, MadeDexMemberCountDTO::memberCount));

        return madeDexRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(madeDexIds).stream()
                .map(madeDex -> new MadeDexSummaryDTO(
                        madeDex.getId(),
                        madeDex.getName(),
                        madeDex.getDescription(),
                        madeDex.getVisibility(),
                        memberCountByMadeDexId.getOrDefault(madeDex.getId(), 0L),
                        roleByMadeDexId.get(madeDex.getId())))
                .toList();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
