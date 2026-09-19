# Imagem da API (ADR-0047). O Render constrói a partir daqui.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY ["Gerenciador de Medicamentos/pom.xml", "pom.xml"]
COPY ["Gerenciador de Medicamentos/src", "src"]
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 cuidamed
WORKDIR /app
COPY --from=build /app/target/idosos-medicamentos-1.0-SNAPSHOT.jar app.jar
USER cuidamed

# O plano gratuito do Render tem 512 MB de memória: limita a JVM para caber.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
