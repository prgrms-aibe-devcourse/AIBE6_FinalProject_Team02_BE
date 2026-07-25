package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.service.DexCatalog.DexSlot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DexMatcher {

    public enum MatchType {
        EXACT,
        ALIAS,
        CONTAINS,
        UNMAPPED
    }

    public record MatchResult(DexSlot slot, MatchType matchType) {

        public static MatchResult unmapped() {
            return new MatchResult(null, MatchType.UNMAPPED);
        }

        public boolean isMapped() {
            return slot != null;
        }
    }

    private final DexCatalog catalog;
    private final Map<String, DexSlot> exactIndex = new HashMap<>();
    private final Map<String, DexSlot> aliasIndex = new HashMap<>();

    public DexMatcher(DexCatalog catalog) {
        this.catalog = catalog;
        for (DexSlot slot : catalog.slots()) {
            exactIndex.put(normalize(slot.name()), slot);
            for (String alias : slot.aliasesOrEmpty()) {
                aliasIndex.putIfAbsent(normalize(alias), slot);
            }
        }
    }

    public MatchResult match(String aiName) {
        if (!StringUtils.hasText(aiName)) {
            return MatchResult.unmapped();
        }
        String normalized = normalize(aiName);

        DexSlot exact = exactIndex.get(normalized);
        if (exact != null) {
            return new MatchResult(exact, MatchType.EXACT);
        }

        DexSlot alias = aliasIndex.get(normalized);
        if (alias != null) {
            return new MatchResult(alias, MatchType.ALIAS);
        }

        Optional<DexSlot> contained = catalog.slots().stream()
                .filter(slot -> normalized.contains(normalize(slot.name())))
                .max(Comparator.comparingInt(slot -> slot.name().length()));

        return contained
                .map(slot -> new MatchResult(slot, MatchType.CONTAINS))
                .orElseGet(MatchResult::unmapped);
    }

    private static String normalize(String value) {
        return value.replaceAll("[\\s·\\-_]", "").toLowerCase();
    }
}
