# syntax=docker/dockerfile:1
# Backend: build com Maven, imagem final só com o JRE 25, usuário sem root e healthcheck na porta de gerência.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/fiscalhubbackend-*.jar extract --layers --launcher --destination /extraido

# JRE em Ubuntu (não Alpine): o Batik desenha o texto dos gráficos SVG do PDF com as fontes do AWT (fontconfig)
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app fiscalhub
WORKDIR /app
# camadas do Spring Boot: dependências mudam pouco, o código muda sempre
COPY --from=build /extraido/dependencies/ ./
COPY --from=build /extraido/spring-boot-loader/ ./
COPY --from=build /extraido/snapshot-dependencies/ ./
COPY --from=build /extraido/application/ ./
USER 10001
EXPOSE 8080 8081
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://127.0.0.1:${MANAGEMENT_SERVER_PORT:-8081}/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
