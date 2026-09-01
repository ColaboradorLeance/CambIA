FROM nginx:alpine
RUN apk add --no-cache openssl
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY gerar-certificado.sh /docker-entrypoint.d/40-gerar-certificado.sh
RUN chmod +x /docker-entrypoint.d/40-gerar-certificado.sh
EXPOSE 80 443
