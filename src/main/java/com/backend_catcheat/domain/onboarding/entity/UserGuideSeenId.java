package com.backend_catcheat.domain.onboarding.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** (user_id + guide_key) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserGuideSeenId implements Serializable {
    private Long userId;
    private String guideKey;
}
