# F0-2/docs/07 §3: build multi-stage, mesma imagem em local/demo/prod (D-28).

FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace

# Camada de dependencias isolada: so reconstroi ao mudar o pom.xml.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --create-home --shell /usr/sbin/nologin reservas
USER reservas
WORKDIR /app
COPY --from=build --chown=reservas:reservas /workspace/target/reservas.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
