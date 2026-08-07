package com.backend_catcheat.domain.made.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 그룹마다 이름과 개수가 달라 전역 enum으로 두지 않는다
@Entity
@Table(name = "made_dex_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexSlot extends BaseEntity {

    public static final int NAME_MAX = 20;
    public static final int MIN_SLOTS = 1;
    public static final int MAX_SLOTS = 6;

    // 순서까지 이 목록이 정한다
    public static final List<String> DEFAULT_NAMES = List.of("아침", "점심", "저녁");

    @Column(name = "made_dex_id", nullable = false)
    private Long madeDexId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 기록이 붙은 슬롯은 삭제 대신 이 값을 채운다
    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    private MadeDexSlot(Long madeDexId, String name, int sortOrder) {
        this.madeDexId = madeDexId;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static MadeDexSlot of(Long madeDexId, String name, int sortOrder) {
        return new MadeDexSlot(madeDexId, name, sortOrder);
    }

    public static List<MadeDexSlot> defaultsFor(Long madeDexId) {
        List<MadeDexSlot> slots = new ArrayList<>(DEFAULT_NAMES.size());
        for (int order = 0; order < DEFAULT_NAMES.size(); order++) {
            slots.add(of(madeDexId, DEFAULT_NAMES.get(order), order));
        }
        return slots;
    }

    // 기록은 슬롯 id로 묶여 있어 이름을 바꾸면 과거 기록에도 소급된다
    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void hide(LocalDateTime now) {
        this.hiddenAt = now;
    }

    public void restore() {
        this.hiddenAt = null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public boolean belongsTo(Long madeDexId) {
        return this.madeDexId.equals(madeDexId);
    }
}
