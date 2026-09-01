FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

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
