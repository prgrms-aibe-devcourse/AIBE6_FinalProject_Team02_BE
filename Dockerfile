FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src src

RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

# HEIC(아이폰 원본) 디코딩용. ImageIO가 heic/heif를 읽지 못해 heif-convert로 JPEG를 거친다.
# 순수 Java HEIC 디코더가 없어 외부 바이너리를 쓴다 (TwelveMonkeys 지원 목록에도 HEIF는 없다).
# 런타임에 실행되므로 빌더가 아니라 이 스테이지에 설치한다.
#
# libheif는 코덱을 플러그인으로 분리해 둔다. libheif-examples만 넣으면 AVIF(aom)만 딸려 오고
# `heif-convert --list-decoders`의 HEIC 항목이 비어 아이폰 HEIC를 열지 못한다.
# HEVC 디코더(libde265)를 함께 설치해야 한다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libheif-examples libheif-plugin-libde265 \
 && rm -rf /var/lib/apt/lists/*

ENV SERVER_PORT=8080

COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]