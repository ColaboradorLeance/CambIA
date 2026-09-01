FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Bug encontrado ao vivo (duas vezes): o bundle publicado ficava com "http://localhost:8080"
# fixado (valor de dev de antes do reverse-proxy existir, Fase 3A) mesmo com
# docker-compose.yml já passando VITE_API_URL="" corretamente. Causa raiz: o padrão
# ARG → ENV → RUN separado (como estava aqui antes) é uma armadilha conhecida do
# Docker/BuildKit — o valor do ARG nem sempre participa do cálculo de cache da camada do
# RUN seguinte de forma confiável, então uma camada antiga (de antes da Fase 3A, quando o
# valor era "http://localhost:8080" de verdade) ficava presa e voltava a ser reaproveitada
# em builds seguintes, mesmo depois de trocar o valor padrão do ARG (viu isso acontecer de
# novo mesmo já com o default vazio). Corrigido usando o ARG diretamente na linha do RUN
# (em vez de passar por uma ENV numa instrução separada) — força esse valor a fazer parte
# do cache-key da camada de build de forma inequívoca, o jeito recomendado pra evitar
# exatamente esse problema.
ARG VITE_API_URL=
RUN VITE_API_URL=$VITE_API_URL npm run build

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
