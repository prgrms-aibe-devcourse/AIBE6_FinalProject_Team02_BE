package com.backend_catcheat.spike.vision.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Component
public class DexCatalog {

    private static final Logger log = LoggerFactory.getLogger(DexCatalog.class);
    private static final String CATALOG_PATH = "spike/dex-sample.json";

    private final List<DexSlot> slots;

    public DexCatalog(ObjectMapper objectMapper) {
        this.slots = load(objectMapper);
        log.info("[spike] 도감 샘플 카탈로그 {}칸 로드 (본편 200칸의 부분집합)", slots.size());
    }

    public List<DexSlot> slots() {
        return slots;
    }

    public int size() {
        return slots.size();
    }

    private static List<DexSlot> load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(CATALOG_PATH).getInputStream()) {
            return List.copyOf(objectMapper.readValue(in, new TypeReference<List<DexSlot>>() {
            }));
        } catch (IOException e) {
            throw new UncheckedIOException("도감 샘플 카탈로그를 읽을 수 없습니다: " + CATALOG_PATH, e);
        }
    }

    public record DexSlot(long id, String name, String category, List<String> aliases) {

        public List<String> aliasesOrEmpty() {
            return aliases == null ? List.of() : aliases;
        }
    }
}
