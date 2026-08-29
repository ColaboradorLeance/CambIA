# Incremento 10 — Tela de Operação (primeira versão, sem a referência visual)

Status: ✅ concluído (versão funcional) — layout final e campos novos continuam bloqueados

Você pediu para seguir pela funcionalidade central sem ainda ter enviado a imagem de referência. Para não travar, construí uma **primeira versão funcional** da tela de Operação usando somente os campos já confirmados no domínio (nenhum dos 13 campos novos da tela do cliente, nenhum layout específico) — quando a imagem chegar, ajustamos a disposição visual e adicionamos o que for preciso.

## Problema real encontrado antes de começar

Para criar uma Operação, o usuário precisa escolher um Cliente e um Banco existentes num `<select>`. Mas a regra de acesso atual só deixava o perfil **Admin** listar Clientes e Bancos (`GET /clientes`, `GET /bancos`) — um Usuário comum tentando montar essa tela levaria `403` só para carregar as opções do formulário.

## Correção no backend (TDD)

- **Red**: atualizei `SecurityTests` esperando que perfil Usuário consiga fazer `GET /clientes` e `GET /bancos` (200), mas continue sem poder `POST` (403) — rodei e falhou como esperado.
- **Green**: ajustei `SecurityConfig` para liberar `GET` em `/clientes/**` e `/bancos/**` para qualquer usuário autenticado, mantendo `POST/PUT/DELETE` exclusivos do Admin. `/usuarios/**` continua 100% Admin (não é necessário para o fluxo de Operação).
- **Regressão**: suíte completa, **58 testes, 0 falhas**.

Essa distinção (Admin gerencia cadastros, Usuário só consulta o que precisa para operar) é uma leitura direta da regra já registrada em [decisoes.md](decisoes.md) — não uma regra nova inventada.

## Tela de Operação (front-end)

- Nova página `OperacoesPage`, com link "Operações" visível para **qualquer usuário logado** (Admin ou Usuário), diferente das outras telas que são só-Admin.
- Formulário com os campos manuais confirmados: Data, Código do banco, Cliente (dropdown), Banco (dropdown), C/V, PR/CR/VIR, Moeda, Valor em ME, Spot Asset, Nivelamento, Taxa Final. Os campos C/V, PR/CR/VIR e Moeda são texto livre (como já é no backend), com sugestões automáticas dos valores conhecidos (`C`/`V`, `Pronto`/`Crédito`/`Virtual`, `USD`/`EUR`/`GBP`) para facilitar sem restringir.
- Tabela mostrando as operações já registradas, com nome de Cliente e Banco resolvidos (não só o ID), status, e os três valores calculados (`R$`, `Total Bruto do Câmbio`, `Comissão Líquida`).
- Botão "Completar" para operações `EM_ANDAMENTO`, chamando o endpoint de mudança de status já existente.
- **Sem edição/exclusão de operação** — o backend não oferece isso (decisão já registrada desde o Incremento 5).

## Como foi validado
Rodei de verdade num Chromium (Playwright), contra a stack inteira em Docker: login → carregar a tela de Operações → preencher o formulário com os **valores reais da linha da TLX** usada na descoberta do domínio → registrar (fica `EM_ANDAMENTO`, sem valores calculados) → clicar em "Completar" → os valores aparecem corretos (`R$ 4.590.512,99`, Total Bruto `9.206,52`, Comissão `6.144,43`, usando o percentual de comissão cadastrado no banco de teste). Sem erros no console.

## O que ainda falta (bloqueado)
- Layout em duas colunas / agrupamento visual da tela de referência do cliente — preciso da imagem.
- Os 13 campos novos identificados na tela (Data da Liquidação, Despesa do Banqueiro, etc.) — significado, origem e fórmula ainda desconhecidos.
- Regras de validação mais específicas para C/V, PR/CR/VIR e Moeda continuam em aberto (texto livre por enquanto).

## Próximo passo
Enviar a imagem da tela de referência para eu ajustar o layout e investigar os 13 campos novos — ou seguir para outra frente (Fechamento diário, Relatórios, ou os pontos de "pronto para uso real": SMTP e credenciais).
