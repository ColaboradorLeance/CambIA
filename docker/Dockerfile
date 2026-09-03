# Imagem única do CambIA: backend (Java), front-end (React) e reverse-proxy HTTPS
# (nginx) juntos no mesmo container. O banco de dados (Postgres) continua sempre
# separado — nunca entra nesta imagem (ver README.md e RELEASING.md pro porquê).
#
# Build a partir da RAIZ do repositório — o contexto precisa enxergar tanto src/ quanto
# frontend/, que ficam em diretórios irmãos deste Dockerfile:
#   docker build -f docker/Dockerfile -t cambia .
#
# Usada apenas para a imagem PUBLICADA (ver scripts/release.sh). O docker-compose.yml
# da raiz do repositório, usado em desenvolvimento (DEVELOPMENT.md), continua buildando
# backend/frontend/reverse-proxy como 3 imagens separadas — mais rápido pra iterar
# (rebuild só do que mudou), sem precisar da complexidade desta imagem única.

# ---- Stage 1: build do backend --------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

# ---- Stage 2: build do front-end -------------------------------------------------
FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ .
# Mesmo cuidado do frontend/Dockerfile original (achado de revisão ao vivo, duas vezes):
# o ARG precisa ser usado diretamente na linha do RUN, não repassado por uma ENV numa
# instrução separada — ver frontend/Dockerfile pra explicação completa do bug de cache
# do BuildKit que isso evita.
ARG VITE_API_URL=
RUN VITE_API_URL=$VITE_API_URL npm run build

# ---- Stage 3: imagem final — JRE + nginx no mesmo container ----------------------
FROM eclipse-temurin:21-jre-alpine

# tini como PID 1: sem ele, sinais (ex: docker stop) e zumbis de processo (o backend
# reinicia sozinho se cair — ver entrypoint.sh) não são tratados corretamente por
# nenhum shell comum. Mesmo problema que qualquer container com mais de um processo
# de longa duração tem, resolvido do jeito padrão da indústria.
#
# Acidente de percurso encontrado ao buildar pela primeira vez: o pacote "nginx" do
# Alpine (via apk) usa uma estrutura DIFERENTE da imagem oficial nginx:alpine — aqui,
# /etc/nginx/conf.d/*.conf é incluído no contexto RAIZ do nginx.conf (só pra módulos/
# snippets), e quem inclui configuração de "server {}" de verdade é
# /etc/nginx/http.d/*.conf, dentro do bloco http{}. Por isso o arquivo vai pra
# http.d, não conf.d (ao contrário do que as duas imagens antigas — frontend/Dockerfile
# e reverse-proxy/Dockerfile, baseadas na imagem oficial nginx:alpine — faziam). Também
# reaproveita o usuário "nginx" que o próprio pacote já cria com posse correta de
# /var/log/nginx e /var/lib/nginx — criar um usuário próprio exigiria chown manual
# nesses diretórios também, sem ganhar nada com isso.
RUN apk add --no-cache nginx openssl tini \
	&& mkdir -p /etc/nginx/certs /usr/share/nginx/html /var/cache/nginx \
	&& chown -R nginx:nginx /var/cache/nginx /etc/nginx/certs /usr/share/nginx/html \
	&& sed -i 's#pid *\/run\/nginx.pid;#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf

COPY --from=backend-build --chown=nginx:nginx /app/target/*.jar /app/app.jar
COPY --from=frontend-build --chown=nginx:nginx /app/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/http.d/default.conf
COPY --chown=nginx:nginx docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

USER nginx
# 8080 (redireciona pra HTTPS) e 8443 (HTTPS, com o certificado autoassinado gerado no
# entrypoint) — portas altas porque o container roda sem privilégio (mesmo raciocínio
# de segurança do reverse-proxy/Dockerfile original). O backend Java escuta só em
# 127.0.0.1:8081, nunca exposto fora do container — só o nginx fala com ele.
EXPOSE 8080 8443

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
	CMD wget -qO- http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["tini", "--", "/entrypoint.sh"]
