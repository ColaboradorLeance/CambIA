#!/bin/sh
# Roda automaticamente na inicialização do container (mecanismo padrão da imagem
# nginx:alpine para scripts em /docker-entrypoint.d/). Gera um certificado autoassinado
# na primeira vez que o container sobe; nas próximas, reaproveita o que já existe no
# volume (senão o navegador voltaria a desconfiar do certificado a cada restart).
#
# CERT_CN define o nome/IP do servidor no certificado — mude no .env pra produção
# (ex: CERT_CN=192.168.1.50 ou CERT_CN=cambia.suaempresa.local). O padrão "localhost"
# só serve pra testar na própria máquina.
set -e

CERT_DIR=/etc/nginx/certs
CERT_CN="${CERT_CN:-localhost}"

if [ ! -f "$CERT_DIR/fullchain.pem" ] || [ ! -f "$CERT_DIR/privkey.pem" ]; then
	echo "[gerar-certificado] Nenhum certificado encontrado — gerando um autoassinado para CN=$CERT_CN"
	mkdir -p "$CERT_DIR"
	openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
		-keyout "$CERT_DIR/privkey.pem" \
		-out "$CERT_DIR/fullchain.pem" \
		-subj "/CN=$CERT_CN" \
		-addext "subjectAltName=DNS:$CERT_CN,DNS:localhost,IP:127.0.0.1"
else
	echo "[gerar-certificado] Certificado já existe no volume, mantendo o atual"
fi
