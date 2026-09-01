FROM nginx:alpine
RUN apk add --no-cache openssl
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY gerar-certificado.sh /docker-entrypoint.d/40-gerar-certificado.sh
# Achado de revisão de segurança: mesmo raciocínio do frontend/Dockerfile — os workers já
# processam toda requisição como usuário nginx por padrão; só o master ainda era root, e
# só pra conseguir ouvir nas portas privilegiadas 80/443. Passa a ouvir em portas altas
# (8080/8443, ver nginx.conf) — o mapeamento pras portas 80/443 do host continua igual
# (docker-compose.yml), então não muda nada pra quem acessa de fora. /etc/nginx/certs
# (onde o certificado autoassinado é gerado/guardado, num volume) precisa já nascer dono
# do usuário nginx, senão o container não consegue nem criar o certificado na primeira
# subida rodando sem privilégio.
RUN chmod +x /docker-entrypoint.d/40-gerar-certificado.sh \
	&& mkdir -p /etc/nginx/certs \
	&& chown -R nginx:nginx /etc/nginx/certs \
	&& chown -R nginx:nginx /var/cache/nginx \
	&& sed -i 's#pid *\/run\/nginx.pid;#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf
USER nginx
EXPOSE 8080 8443
