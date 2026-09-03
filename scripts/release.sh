#!/usr/bin/env bash
# Corta uma nova versão do sistema CambIA: builda as 3 imagens que este projeto produz
# a partir do próprio código-fonte (backend, frontend, reverse-proxy — db e mailpit são
# imagens de terceiros, não entram aqui), publica no GitHub Container Registry (privado)
# e marca a versão no git.
#
# Uso:
#   ./scripts/release.sh 1.1.0     # sobe a versão (edita VERSION/pom.xml/package.json,
#                                   # commita, cria a tag git vX.Y.Z, builda e publica)
#   ./scripts/release.sh           # republica a MESMA versão já gravada em VERSION
#                                   # (sem bump) — útil pra tentar de novo depois de um
#                                   # erro de build/push, sem duplicar o número de versão
#
# Convenção de versão (SemVer): incrementar MINOR a cada feature nova entregue ao
# cliente, PATCH pra correção sem feature nova, MAJOR só se quebrar compatibilidade de
# forma que o cliente precise agir. Ver RELEASING.md pro processo completo.
#
# Pré-requisito: a conta GitHub "ColaboradorLeance" (gh auth) precisa ter o escopo
# "write:packages" (além de "repo", que ela já tem) — sem isso o push da imagem falha
# com 403/denied. Se faltar, rode: gh auth refresh -h github.com -s write:packages -u ColaboradorLeance

set -euo pipefail
cd "$(dirname "$0")/.."

GH_USER="ColaboradorLeance"
GH_USER_LOWER="colaboradorleance"
REGISTRY="ghcr.io"
IMAGENS=(backend frontend reverse-proxy)

# Bug encontrado ao vivo na primeira execução deste script: uma falha no meio do
# caminho (ex: push sem o escopo write:packages) interrompia o script com "set -e"
# ANTES da linha que trocava a conta do gh de volta pra fereziniNi — deixando o `gh`
# "preso" em ColaboradorLeance pro resto da sessão. Um trap em EXIT roda sempre,
# sucesso ou erro, então a conta (e a sessão docker login) sempre voltam ao normal.
trap 'gh auth switch --user fereziniNi > /dev/null 2>&1 || true; docker logout "$REGISTRY" > /dev/null 2>&1 || true' EXIT

VERSAO_ATUAL="$(cat VERSION)"
NOVA_VERSAO="${1:-$VERSAO_ATUAL}"

if [[ ! "$NOVA_VERSAO" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "Versão inválida: '$NOVA_VERSAO' (esperado formato SemVer, ex: 1.2.0)" >&2
	exit 1
fi

echo "==> Versão: $VERSAO_ATUAL -> $NOVA_VERSAO"

if [[ "$NOVA_VERSAO" != "$VERSAO_ATUAL" ]]; then
	if [[ -n "$(git status --porcelain)" ]]; then
		echo "Working tree não está limpo — commite ou descarte as mudanças pendentes antes de cortar uma versão." >&2
		exit 1
	fi

	echo "$NOVA_VERSAO" > VERSION
	# pom.xml tem dois <version> (o do spring-boot-starter-parent e o do projeto) — só
	# troca o que vem logo depois do <artifactId>cambia</artifactId>, pra não mexer no
	# do parent por engano.
	sed -i "/<artifactId>cambia<\/artifactId>/{n;s/<version>.*<\/version>/<version>${NOVA_VERSAO}<\/version>/}" pom.xml
	# Só o primeiro "version" do package.json (o do próprio pacote, não de uma dependência).
	sed -i "0,/\"version\":/{s/\"version\": \"[^\"]*\"/\"version\": \"${NOVA_VERSAO}\"/}" frontend/package.json

	git add VERSION pom.xml frontend/package.json
	git commit -m "Versão ${NOVA_VERSAO}

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
	git tag -a "v${NOVA_VERSAO}" -m "Versão ${NOVA_VERSAO}"

	echo "==> Commit e tag v${NOVA_VERSAO} criados localmente. Publicando no GitHub..."
	gh auth switch --user "$GH_USER"
	git push origin master
	git push origin "v${NOVA_VERSAO}"
fi

echo "==> Rodando suíte de testes do backend antes de buildar a imagem..."
./mvnw -q test

echo "==> Buildando as imagens (sem cache, pra garantir que refletem exatamente este código)..."
docker compose build --no-cache backend frontend reverse-proxy

echo "==> Autenticando no $REGISTRY como $GH_USER..."
gh auth switch --user "$GH_USER"
gh auth token | docker login "$REGISTRY" -u "$GH_USER_LOWER" --password-stdin

for imagem in "${IMAGENS[@]}"; do
	tag_local="cambia-${imagem}:latest"
	destino="${REGISTRY}/${GH_USER_LOWER}/cambia-${imagem}"

	echo "==> Marcando e publicando ${destino}:${NOVA_VERSAO} e ${destino}:latest..."
	docker tag "$tag_local" "${destino}:${NOVA_VERSAO}"
	docker tag "$tag_local" "${destino}:latest"
	docker push "${destino}:${NOVA_VERSAO}"
	docker push "${destino}:latest"
done

echo ""
echo "==> Versão ${NOVA_VERSAO} publicada com sucesso em:"
for imagem in "${IMAGENS[@]}"; do
	echo "    ${REGISTRY}/${GH_USER_LOWER}/cambia-${imagem}:${NOVA_VERSAO}"
done
echo ""
echo "Lembrete: registrar esta versão em docs/decisoes.md (local, não vai pro git) e"
echo "avisar o cliente/repassar as credenciais de acesso ao pacote (imagens privadas)."
