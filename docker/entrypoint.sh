#!/bin/sh
# Ponto de entrada da imagem única do CambIA: gera o certificado HTTPS autoassinado (se
# ainda não existir), sobe o backend Java em segundo plano — reiniciando sozinho se ele
# cair — e mantém o nginx em primeiro plano (front-end + proxy da API), processando
# tudo dentro do mesmo container.
set -e

CERT_DIR=/etc/nginx/certs
CERT_CN="${CERT_CN:-localhost}"

# Mesma lógica do antigo reverse-proxy/gerar-certificado.sh: gera na primeira subida,
# reaproveita o que já existe no volume nas próximas (senão o navegador voltaria a
# desconfiar do certificado a cada restart).
if [ ! -f "$CERT_DIR/fullchain.pem" ] || [ ! -f "$CERT_DIR/privkey.pem" ]; then
	echo "[entrypoint] Nenhum certificado encontrado — gerando um autoassinado para CN=$CERT_CN"
	openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
		-keyout "$CERT_DIR/privkey.pem" \
		-out "$CERT_DIR/fullchain.pem" \
		-subj "/CN=$CERT_CN" \
		-addext "subjectAltName=DNS:$CERT_CN,DNS:localhost,IP:127.0.0.1"
else
	echo "[entrypoint] Certificado já existe no volume, mantendo o atual"
fi

# Backend roda em segundo plano, só em loopback (127.0.0.1:8081 — nunca exposto fora do
# container), reiniciando sozinho se cair. Cobre o caso do banco de dados ainda não
# estar pronto quando o container sobe: sem isso, o container inteiro ficaria de pé com
# o nginx respondendo 502 pra sempre, sem se recuperar sozinho depois que o banco
# finalmente ficasse disponível.
(
	while true; do
		java -Dserver.port=8081 -Dserver.address=127.0.0.1 -jar /app/app.jar && break
		echo "[entrypoint] backend caiu — tentando de novo em 3s..."
		sleep 3
	done
) &

# nginx em primeiro plano — é o processo que mantém o container vivo (tini, como PID 1,
# encaminha sinais como SIGTERM pra ele corretamente).
exec nginx -g "daemon off;"
