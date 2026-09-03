# Versionamento e release

Como o CambIA é entregue para clientes rodarem dentro da própria infraestrutura
(on-premise) — ver README.md — cada entrega é uma **imagem Docker versionada**, não o
código-fonte. Este documento descreve como cortar uma nova versão.

## Esquema de versão

Uma única versão (SemVer, `MAJOR.MINOR.PATCH`) para o sistema como um todo. A versão
atual vive no arquivo [`VERSION`](VERSION), na raiz do repositório, e é replicada em
`pom.xml` (`<version>`) e `frontend/package.json` (`"version"`) a cada release — essas
três fontes nunca devem divergir entre releases.

Convenção de incremento:

- **PATCH** (`1.0.0` → `1.0.1`): correção de bug, sem funcionalidade nova.
- **MINOR** (`1.0.0` → `1.1.0`): funcionalidade nova, compatível com o que já existe (o
  caso mais comum — "a cada feature nova, aumenta a versão").
  este é o incremento default sempre que a feature muda comportamento visível
  pro usuário, mesmo sem quebrar nada existente.
- **MAJOR** (`1.0.0` → `2.0.0`): mudança que quebra compatibilidade de um jeito que o
  cliente precisa agir (ex: variável de ambiente obrigatória nova, migração de dados que
  exige passo manual, remoção de um endpoint que uma integração externa possa usar).

## Uma imagem só, com tudo dentro (menos o banco)

Backend (Java), front-end (React) e reverse-proxy HTTPS (nginx) são publicados como
**uma única imagem** — `docker/Dockerfile` builda os três e monta um único container
que roda o processo Java e o nginx lado a lado (nginx serve a SPA direto e faz proxy só
das rotas de API pro backend, que escuta em loopback dentro do próprio container; o
backend reinicia sozinho se cair, ex: banco ainda não pronto na subida — ver
`docker/entrypoint.sh` pros detalhes). Simplifica a entrega: um comando de
`docker pull`, um `docker run`/serviço de compose, um log só pra acompanhar.

`db` (Postgres) continua **sempre separado** — nunca entra nesta imagem. Colocar um
banco de dados com estado dentro da mesma imagem da aplicação foi considerado e
descartado: arriscaria perda de dados ao recriar o container, impediria backup/restore
independente, e quebraria a opção de banco de dados externo que o cliente pode usar
(ver README.md). `mailpit` também fica de fora — é só uma ferramenta de teste local.

> O `docker-compose.yml` da raiz deste repositório (usado em desenvolvimento — ver
> [DEVELOPMENT.md](DEVELOPMENT.md)) continua buildando backend/frontend/reverse-proxy
> como 3 imagens separadas, propositalmente — é mais rápido pra iterar localmente
> (rebuild só do serviço que mudou) e não precisa da complexidade de rodar dois
> processos num container só. A imagem única é gerada só na hora do release, a partir
> de `docker/Dockerfile`.

## Onde a imagem é publicada

[GitHub Container Registry](https://ghcr.io) (`ghcr.io`), **privado**, na conta
`ColaboradorLeance` (a mesma usada para dar push no repositório):

- `ghcr.io/colaboradorleance/cambia`

Publicada com duas tags: a versão exata (`:1.1.0`) e `:latest` (sempre aponta pra
última publicada). Para uma entrega a um cliente específico, usar sempre a tag de
versão exata — nunca `:latest` — pra saber exatamente o que está rodando em cada lugar.

## Como cortar uma release

```bash
./scripts/release.sh 1.1.0
```

O script (ver [`scripts/release.sh`](scripts/release.sh) para o passo a passo comentado):

1. Atualiza `VERSION`, `pom.xml` e `frontend/package.json` para a nova versão.
2. Commita essa mudança e cria a tag git `v1.1.0`.
3. Publica o commit e a tag no GitHub (`origin master` + a tag).
4. Roda a suíte de testes do backend — aborta a release se algum teste falhar.
5. Builda a imagem única (`docker build -f docker/Dockerfile --no-cache`, garantindo
   que reflete exatamente o código commitado, sem cache de uma build antiga).
6. Publica a imagem no GHCR com as tags `:1.1.0` e `:latest`.

Rodar `./scripts/release.sh` **sem argumento** republica a versão já gravada em
`VERSION` (sem bump) — útil pra tentar de novo depois de uma falha de build/push no meio
do caminho, sem gastar um número de versão à toa.

### Pré-requisito único: escopo `write:packages`

O token da conta `ColaboradorLeance` usado pelo `gh`/`docker login` precisa do escopo
`write:packages` (além de `repo`, que ela já tem para dar push no código). Sem isso, o
push da imagem falha com 403/denied. Para conceder (ação única, feita interativamente
pelo dono da conta — abre o navegador para autorizar):

```bash
gh auth refresh -h github.com -s write:packages -u ColaboradorLeance
```

## Antes de entregar ao cliente

A imagem é **privada** — o cliente precisa de credenciais próprias para `docker pull`,
senão toma 401/denied mesmo tendo o nome certo da imagem. Depois da primeira
publicação, configurar o acesso do cliente ao pacote:
[github.com/ColaboradorLeance?tab=packages](https://github.com/ColaboradorLeance?tab=packages)
→ selecionar o pacote → "Package settings" → "Manage Actions access" / convidar o
usuário/organização do cliente como colaborador com permissão de leitura — ou gerar um
Personal Access Token com escopo `read:packages` só pra esse fim e repassar ao cliente
junto com as instruções de `docker login ghcr.io`.

O guia de instalação/configuração que o cliente usa a partir daí é o
[README.md](README.md) — já pronto pra copiar/colar (`docker-compose.yml` e `.env` de
exemplo inclusos), sem depender de nenhum arquivo deste repositório além dele mesmo.
