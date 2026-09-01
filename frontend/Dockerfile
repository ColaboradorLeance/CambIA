FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Achado de revisão de segurança: a imagem nginx:alpine já processa toda requisição nos
# processos "worker" (usuário nginx, sem privilégio) por padrão — só o processo "master"
# (que não lida com requisição nenhuma, só liga o socket e sobe os workers) ainda era
# root, e só porque escutava numa porta privilegiada (<1024). Este container nunca é
# exposto direto no host — só o reverse-proxy fala com ele, dentro da rede do Docker
# Compose (Fase 3A) — então ouvir numa porta alta (8080, ver nginx.conf) permite rodar o
# container inteiro, master incluído, sem privilégio nenhum. /var/cache/nginx (buffers
# temporários) e o arquivo de pid nascem donos de root na imagem oficial — sem esse ajuste
# o nginx nem consegue subir rodando sem privilégio.
RUN chown -R nginx:nginx /var/cache/nginx \
	&& sed -i 's#pid *\/run\/nginx.pid;#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf
USER nginx
EXPOSE 8080
