# CambIA

Sistema interno para registrar e controlar operações de compra e venda de moedas internacionais (câmbio), substituindo o controle feito hoje em planilha. Uso interno, 10 a 50 usuários, hospedagem em servidor próprio (on-premise).

## Stack técnica

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Flyway |
| Banco de dados | PostgreSQL 16 |
| Front-end | React 19 (Vite), servido em produção via Nginx |
| Autenticação | Sem senha, por "magic link" (token enviado por e-mail) |
| Empacotamento | Docker + Docker Compose |

## O que já existe

Núcleo do backend completo (Cliente, Banco, Usuário, Autenticação, Operação com cálculo automático e status ANDAMENTO/CONFIRMADO/CANCELADO), Fechamento Diário (com export em PDF/Excel e job agendado), Relatórios (operações filtradas, comparativos, rankings, posição em aberto) e front-end cobrindo todas essas telas. Detalhes de cada incremento em [docs/roadmap.md](docs/roadmap.md); regras de negócio e fórmulas confirmadas em [docs/dominio.md](docs/dominio.md).

---

## Subindo o sistema com Docker Compose (caminho recomendado)

É a forma como o sistema é distribuído: um `reverse-proxy` HTTPS na frente, três serviços internos (`backend`, `frontend`, `db`) e um `mailpit` opcional para testar e-mail, todos definidos em [docker-compose.yml](docker-compose.yml). O `reverse-proxy` é o **único ponto de entrada exposto no host** — front-end e backend não são mais alcançáveis direto de fora (Fase 3A da revisão de segurança).

### 1. Pré-requisitos na máquina/servidor

- [Docker Engine](https://docs.docker.com/engine/install/) e [Docker Compose plugin](https://docs.docker.com/compose/install/) (`docker compose version` deve funcionar; no Windows/Mac, instalar o Docker Desktop já traz os dois).
- Portas livres: `443` e `80` (reverse-proxy HTTPS), `5432` (Postgres) e, se for usar o Mailpit, `8025`/`1025`.
- Git, para clonar o repositório.

### 2. Obter o código

```bash
git clone <url-do-repositorio>
cd CambIA
```

### 3. Configurar o `.env`

Copie o modelo e ajuste os valores antes de subir:

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

(o `-k` ignora o aviso do certificado autoassinado — só pra linha de comando; no navegador, ver nota sobre `CERT_CN` acima). Deve responder `{"status":"UP"}`. O sistema inteiro fica em `https://localhost` (ou `https://SEU_SERVIDOR` a partir de outra máquina) — não existe mais porta separada pro front-end.

### 6. Criar o primeiro usuário (Admin)

O sistema não vem com nenhum usuário cadastrado. O primeiro Admin é criado por um endpoint que **só funciona uma única vez**, enquanto não existir nenhum usuário no banco:

```bash
curl -k -X POST https://localhost/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"voce@suaempresa.com.br"}'
```

Depois disso, esse endpoint passa a responder `409 Conflict` — novos usuários são criados de dentro do sistema, pela tela "Usuários", por um Admin já logado.

### 7. Fazer login (sem senha, por link mágico)

1. Acesse `https://localhost` (ou o endereço do servidor) — vai redirecionar para `/login`. Aceite o aviso do certificado autoassinado na primeira visita.
2. Digite o e-mail cadastrado e clique em "Enviar link de acesso".
3. **Enquanto o envio real de e-mail (SMTP) não estiver configurado** (ver seção abaixo), o link não chega por e-mail de verdade — ele aparece no log do backend:

   ```bash
   docker compose logs backend | grep "Link mágico"
   ```

   Vai aparecer algo como `Link mágico para voce@suaempresa.com.br: /auth/verify?token=xxxxxxxx-...`. Copie só o valor depois de `token=`.
4. Cole esse valor no campo "Token" da tela e clique em "Entrar".

A sessão dura 8 horas — em produção (via `https://`, atrás do reverse-proxy) fica guardada num cookie `httpOnly`, não acessível por JavaScript; cada link mágico expira em 15 minutos ou no primeiro uso. Tentativas repetidas de login (por e-mail ou por IP) têm um limite — depois de algumas tentativas seguidas, é preciso esperar alguns minutos.

---

## Envio real de e-mail (opcional, mas necessário antes de usar em produção de verdade)

Sem isso configurado, todo login exige pegar o token no log do backend (aceitável para homologação/uso restrito, não recomendado para os usuários finais no dia a dia). Duas opções, documentadas lado a lado em [.env.example](.env.example):

- **Opção 1 — SMTP real** (ex: relay do Microsoft 365/Exchange Online): defina no `.env` `CAMBIA_MAIL_HABILITADO=true`, `CAMBIA_MAIL_REMETENTE`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Depois, `docker compose up -d` (não precisa rebuildar, são variáveis lidas em tempo de execução pelo backend).
- **Opção 2 — Mailpit** (teste local, sem enviar e-mail de verdade a ninguém): use os valores comentados no `.env.example` (`SPRING_MAIL_HOST=mailpit`, etc.) e acesse `http://localhost:8025` para ver os e-mails "enviados" capturados pelo Mailpit (que já sobe junto no `docker compose up`).

## Operações de manutenção

- **Parar tudo**: `docker compose down` (mantém os dados do Postgres, guardados no volume `db_data`).
- **Apagar tudo, incluindo o banco de dados**: `docker compose down -v` — **irreversível**, use só se realmente quiser zerar os dados.
- **Ver logs**: `docker compose logs -f backend` / `docker compose logs -f frontend`.
- **Backup do banco**: `docker compose exec db pg_dump -U cambia cambia > backup.sql` (rotina de backup automatizado ainda não faz parte do sistema — ver limitações abaixo).
- **Atualizar para uma nova versão do código**: `git pull` seguido de `docker compose up -d --build` (o Flyway aplica sozinho qualquer migração nova do banco).

---

## Rodando em desenvolvimento (sem Docker Compose, código-fonte direto)

Útil para desenvolver/depurar; para só usar o sistema em produção, prefira a seção do Docker Compose acima.

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

---

## Estrutura do projeto

```
src/main/java/com/cambia/    Backend (Spring Boot) — um pacote por domínio (cliente, banco, operacao, usuario, auth, security, web)
src/main/resources/db/migration/   Migrações Flyway (versionadas, nunca editar uma já aplicada)
src/test/java/com/cambia/    Testes (JUnit 5 + Testcontainers)
frontend/src/                Front-end (React + Vite)
reverse-proxy/                Reverse proxy HTTPS (Nginx) — único ponto de entrada exposto no host
docs/                        Documentação de domínio, decisões, pendências e roadmap (ver abaixo)
docker-compose.yml           Orquestração dos containers para rodar o sistema completo
scripts/release.sh           Corta uma nova versão e publica as imagens (ver RELEASING.md)
```

## Documentação do projeto

- [docs/dominio.md](docs/dominio.md) — entidades, campos, o que é manual vs. calculado, fórmulas confirmadas e regras de negócio
- [docs/decisoes.md](docs/decisoes.md) — decisões arquiteturais e de produto já fechadas, incremento a incremento
- [docs/pendencias.md](docs/pendencias.md) — decisões de negócio ainda em aberto
- [docs/roadmap.md](docs/roadmap.md) — o que já foi entregue e o que falta
- [RELEASING.md](RELEASING.md) — como versionar e publicar uma imagem Docker para entregar a um cliente

## Limitações conhecidas antes de um uso em produção "real"

- **Certificado HTTPS autoassinado por padrão.** O `reverse-proxy` gera um certificado sozinho (ver `CERT_CN` acima) — funciona (o tráfego é criptografado de verdade), mas o navegador mostra um aviso de "conexão não segura" na primeira visita, já que não é emitido por uma autoridade confiável. Pra eliminar o aviso, é preciso trocar por um certificado de uma CA de verdade (interna da empresa, ou pública se houver domínio) — fora do escopo do setup automático atual.
- **Senha do Postgres com valor padrão fraco** (`cambia`) — configurável via `POSTGRES_PASSWORD` no `.env` (ver `.env.example`), mas o padrão continua fraco pra não quebrar quem já usa em dev. **Troque antes de expor o serviço além do localhost** — a porta `5432` fica publicada no host por padrão.
- **Envio real de e-mail (SMTP)** depende de credenciais próprias da empresa (ver seção acima) — sem isso, login exige acesso aos logs do backend para pegar o token manualmente.
- Sem rotina automatizada de backup do banco — só o comando manual de `pg_dump` citado acima.
