# Desenvolvimento

Este documento é para quem **desenvolve** o CambIA (precisa do código-fonte, builda a
partir dele, roda os testes). Se você só vai **rodar o sistema já pronto** (recebeu
acesso às imagens Docker), use o [README.md](README.md) — este arquivo não se aplica
nesse caso.

## Subindo a partir do código-fonte com Docker Compose

### 1. Pré-requisitos

- [Docker Engine](https://docs.docker.com/engine/install/) e [Docker Compose plugin](https://docs.docker.com/compose/install/) (`docker compose version` deve funcionar; no Windows/Mac, instalar o Docker Desktop já traz os dois).
- Portas livres: `443` e `80` (reverse-proxy HTTPS), `5432` (Postgres) e, se for usar o Mailpit, `8025`/`1025`.
- Git, para clonar o repositório.

### 2. Obter o código

```bash
git clone <url-do-repositorio>
cd CambIA
```

### 3. Configurar o `.env`

```bash
cp .env.example .env
```

Edite o `.env` com um editor de texto. **O mais importante para acessar de outra máquina é `CERT_CN`** — leia a explicação abaixo antes de seguir. Os demais campos (envio de e-mail) já funcionam com um padrão razoável para começar a testar (link de acesso aparece no log do backend, sem precisar de SMTP configurado).

Além disso, **troque `POSTGRES_PASSWORD`** por uma senha forte antes de rodar num servidor real — o padrão (`cambia`) existe só pra não quebrar quem já está usando em dev, mas a porta `5432` do Postgres fica publicada no host por padrão (ver limitações conhecidas, mais abaixo).

#### `CERT_CN` — o nome/IP do servidor gravado no certificado HTTPS

O `reverse-proxy` gera um certificado autoassinado sozinho na primeira subida (guardado num volume Docker, não é regerado nas próximas vezes). O nome/IP nele precisa bater com o endereço que o navegador vai usar pra acessar:

- **Testando na mesma máquina**: não precisa mudar nada, o padrão `CERT_CN=localhost` já funciona.
- **Rodando num servidor e acessando de outras máquinas na rede**: defina no `.env`, **antes da primeira subida**:

  ```
  CERT_CN=IP-OU-DOMINIO-DO-SERVIDOR
  ```

  Trocando pelo endereço com que as máquinas dos usuários enxergam o servidor na rede (ex: `192.168.1.50` ou um domínio interno). Se precisar trocar depois de já ter subido uma vez, apague o volume do certificado pra forçar gerar um novo: `docker compose down && docker volume rm cambia_reverse_proxy_certs` (o nome exato do volume pode variar — confira com `docker volume ls`).

- **Certificado autoassinado**: o navegador vai mostrar um aviso de "conexão não segura" na primeira visita — é esperado (não é um certificado emitido por uma autoridade confiável), aceite o aviso pra continuar. Se sua empresa tiver uma CA própria ou um domínio público de verdade, dá pra trocar por um certificado real substituindo os arquivos em `reverse-proxy/` — fora do escopo deste guia.
- `VITE_API_URL` **não precisa ser definido** — o padrão (vazio) já funciona, porque front-end e backend ficam atrás do mesmo `reverse-proxy` (mesma origem). Só preencha isso se estiver rodando sem o reverse-proxy.

### 4. Subir os containers

```bash
docker compose up -d --build
```

Na primeira vez isso builda as imagens do backend (Maven + JDK 21) e do frontend (Node + Nginx), sobe o Postgres, aplica todas as migrações do Flyway automaticamente e inicia a aplicação. Acompanhe os logs até ver `Started CambIaApplication`:

```bash
docker compose logs -f backend
```

### 5. Verificar que subiu

```bash
curl -k https://localhost/actuator/health
```

(o `-k` ignora o aviso do certificado autoassinado — só pra linha de comando; no navegador, ver nota sobre `CERT_CN` acima). Deve responder `{"status":"UP"}`.

### 6. Criar o primeiro usuário (Admin) e fazer login

Igual ao processo descrito no [README.md](README.md), seção "Criar o primeiro usuário (Admin)" — os passos são os mesmos independente de estar rodando a partir do código ou de uma imagem publicada.

### Atualizar para uma nova versão do código

```bash
git pull
docker compose up -d --build
```

O Flyway aplica sozinho qualquer migração nova do banco.

## Rodando em desenvolvimento (sem Docker Compose, código-fonte direto)

Útil para desenvolver/depurar; para só usar o sistema, prefira a seção do Docker Compose acima.

### Pré-requisitos

- Java 21 (JDK)
- Docker Engine rodando (o backend usa [Testcontainers](https://testcontainers.com/) para subir um Postgres descartável automaticamente em testes e no modo dev — **precisa do Docker mesmo fora do Compose**)
- Node.js 22+ e npm (para o front-end)

### Backend

```bash
./mvnw spring-boot:test-run
```

(No Windows, use `mvnw.cmd spring-boot:test-run`.) Isso sobe a API em `http://localhost:8080` contra um Postgres via Testcontainers, sem precisar configurar nada — mesmo comportamento usado nos testes automatizados.

Para rodar a suíte de testes:

```bash
./mvnw test
```

### Front-end

```bash
cd frontend
npm install
npm run dev
```

Sobe em `http://localhost:5173` com hot-reload, apontando por padrão para `http://localhost:8080` (ver `frontend/.env`, variável `VITE_API_URL` — aqui, ao contrário do build de produção, é só o valor lido pelo Vite em dev, pode editar e o hot-reload já aplica).

## Estrutura do projeto

```
src/main/java/com/cambia/    Backend (Spring Boot) — um pacote por domínio (cliente, banco, operacao, usuario, auth, security, web)
src/main/resources/db/migration/   Migrações Flyway (versionadas, nunca editar uma já aplicada)
src/test/java/com/cambia/    Testes (JUnit 5 + Testcontainers)
frontend/src/                Front-end (React + Vite)
reverse-proxy/                Reverse proxy HTTPS (Nginx) — único ponto de entrada exposto no host
docs/                        Documentação de domínio, decisões, pendências e roadmap (ver abaixo)
docker-compose.yml           Orquestração dos containers, a partir do código-fonte (build:)
scripts/release.sh           Corta uma nova versão e publica as imagens (ver RELEASING.md)
```

## Documentação do projeto

- [docs/dominio.md](docs/dominio.md) — entidades, campos, o que é manual vs. calculado, fórmulas confirmadas e regras de negócio
- [docs/decisoes.md](docs/decisoes.md) — decisões arquiteturais e de produto já fechadas, incremento a incremento
- [docs/pendencias.md](docs/pendencias.md) — decisões de negócio ainda em aberto
- [docs/roadmap.md](docs/roadmap.md) — o que já foi entregue e o que falta
- [RELEASING.md](RELEASING.md) — como versionar e publicar uma imagem Docker para entregar a um cliente
