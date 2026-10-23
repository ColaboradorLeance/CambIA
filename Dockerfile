# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
# Cache do repositório Maven entre builds (BuildKit): sem ele, TODO build baixava todas as
# dependências do zero, e uma instabilidade de rede com o Maven Central derrubava o build
# (visto em 2026-10-23: handshake TLS encerrado após ~20 min). Com o cache, só baixa o que
# mudou; as tentativas extras cobrem oscilação momentânea.
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests -Daether.connector.http.retryHandler.count=5 package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Achado de revisão de segurança: a JVM rodava como root sem nenhuma necessidade — não
# precisa de porta privilegiada (8080), nem de escrever em lugar nenhum do sistema de
# arquivos além do /tmp já compartilhado pela imagem. Um usuário dedicado, sem privilégio
# nenhum, limita o estrago de uma eventual falha de segurança na aplicação ou numa
# dependência (ex.: RCE por uma lib vulnerável) — sem isso, essa falha já nasceria com
# controle total do container.
RUN addgroup -S cambia && adduser -S cambia -G cambia
COPY --from=build --chown=cambia:cambia /app/target/*.jar app.jar
USER cambia
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
