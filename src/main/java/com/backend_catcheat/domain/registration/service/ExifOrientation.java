package com.backend_catcheat.domain.registration.service;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * JPEG의 EXIF 회전 정보를 읽어 이미지를 실제로 돌린다.
 *
 * `ImageIO.read()`는 EXIF Orientation을 적용하지 않는다. 아이폰 세로 사진은 픽셀이
 * 가로로 저장되고 "돌려서 보라"는 지시가 EXIF에만 있어, 그대로 쓰면 눕는다.
 *
 * HEIC 경로는 여기 오기 전에 이미 바로 서 있다 — libheif가 회전을 적용해 내보낸다.
 * 그 결과 JPEG의 Orientation은 1이라 이 코드가 그대로 통과시킨다.
 *
 * 태그 하나를 읽으려고 의존성을 더하지 않았다. 파싱이 실패하면 회전 없음(1)으로 떨어진다.
 */
final class ExifOrientation {

    static final int NORMAL = 1;

    private static final int JPEG_SOI = 0xFFD8;
    private static final int MARKER_APP1 = 0xE1;
    private static final int MARKER_SOS = 0xDA;
    private static final int ORIENTATION_TAG = 0x0112;
    private static final byte[] EXIF_ID = {'E', 'x', 'i', 'f', 0, 0};

    private ExifOrientation() {
    }

    /** 못 읽으면 1(회전 없음). 회전 정보 하나 때문에 등록 자체를 막을 이유가 없다 */
    static int of(byte[] source) {
        try {
            return parse(source);
        } catch (RuntimeException e) {
            return NORMAL;
        }
    }

    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation <= NORMAL || orientation > 8) {
            return image;
        }
        // 5~8은 가로세로가 뒤바뀐다
        boolean swapped = orientation >= 5;
        int width = swapped ? image.getHeight() : image.getWidth();
        int height = swapped ? image.getWidth() : image.getHeight();

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(image, transformFor(orientation, image.getWidth(), image.getHeight()), null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** EXIF Orientation 1~8의 정의를 그대로 옮긴 것 */
    private static AffineTransform transformFor(int orientation, int width, int height) {
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { // 좌우 뒤집기
                t.scale(-1, 1);
                t.translate(-width, 0);
            }
            case 3 -> { // 180도
                t.translate(width, height);
                t.rotate(Math.PI);
            }
            case 4 -> { // 상하 뒤집기
                t.scale(1, -1);
                t.translate(0, -height);
            }
            case 5 -> { // 좌우 뒤집고 90도
                t.rotate(Math.PI / 2);
                t.scale(1, -1);
            }
            case 6 -> { // 시계 방향 90도 — 아이폰 세로 사진이 여기다
                t.translate(height, 0);
                t.rotate(Math.PI / 2);
            }
            case 7 -> { // 좌우 뒤집고 270도
                t.scale(-1, 1);
                t.translate(-height, 0);
                t.translate(0, width);
                t.rotate(3 * Math.PI / 2);
            }
            case 8 -> { // 시계 반대 방향 90도
                t.translate(0, width);
                t.rotate(3 * Math.PI / 2);
            }
            default -> {
            }
        }
        return t;
    }

    private static int parse(byte[] source) {
        if (source == null || source.length < 4 || u16(source, 0) != JPEG_SOI) {
            return NORMAL;
        }
        int pos = 2;
        while (pos + 4 <= source.length) {
            if ((source[pos] & 0xFF) != 0xFF) {
                return NORMAL;
            }
            int marker = source[pos + 1] & 0xFF;
            // 스탠드얼론 마커는 길이 필드가 없다
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                pos += 2;
                continue;
            }
            if (marker == MARKER_SOS) {
                return NORMAL; // 여기부터는 압축 데이터다
            }
            int length = u16(source, pos + 2);
            if (length < 2 || pos + 2 + length > source.length) {
                return NORMAL;
            }
            if (marker == MARKER_APP1 && startsWithExif(source, pos + 4)) {
                return readTiff(source, pos + 4 + EXIF_ID.length);
            }
            pos += 2 + length;
        }
        return NORMAL;
    }

    private static boolean startsWithExif(byte[] source, int offset) {
        if (offset + EXIF_ID.length > source.length) {
            return false;
        }
        for (int i = 0; i < EXIF_ID.length; i++) {
            if (source[offset + i] != EXIF_ID[i]) {
                return false;
            }
        }
        return true;
    }

    /** TIFF 헤더 → IFD0 → Orientation(0x0112) */
    private static int readTiff(byte[] source, int tiffStart) {
        if (tiffStart + 8 > source.length) {
            return NORMAL;
        }
        boolean littleEndian;
        if (source[tiffStart] == 'I' && source[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (source[tiffStart] == 'M' && source[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return NORMAL;
        }

        long ifdOffset = u32(source, tiffStart + 4, littleEndian);
        int ifd = (int) (tiffStart + ifdOffset);
        if (ifd < 0 || ifd + 2 > source.length) {
            return NORMAL;
        }

        int entries = u16(source, ifd, littleEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > source.length) {
                return NORMAL;
            }
            if (u16(source, entry, littleEndian) == ORIENTATION_TAG) {
                // SHORT 한 개는 값 영역 앞 2바이트에 그대로 들어 있다
                int value = u16(source, entry + 8, littleEndian);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int u16(byte[] b, int i) {
        return u16(b, i, false);
    }

    private static int u16(byte[] b, int i, boolean littleEndian) {
        int first = b[i] & 0xFF;
        int second = b[i + 1] & 0xFF;
        return littleEndian ? (second << 8) | first : (first << 8) | second;
    }

    private static long u32(byte[] b, int i, boolean littleEndian) {
        return littleEndian
                ? (long) (b[i] & 0xFF) | ((long) (b[i + 1] & 0xFF) << 8)
                        | ((long) (b[i + 2] & 0xFF) << 16) | ((long) (b[i + 3] & 0xFF) << 24)
                : ((long) (b[i] & 0xFF) << 24) | ((long) (b[i + 1] & 0xFF) << 16)
                        | ((long) (b[i + 2] & 0xFF) << 8) | (long) (b[i + 3] & 0xFF);
    }
}
