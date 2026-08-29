# Incremento 12 — Construtor visual de fórmula (arrastar/soltar)

Status: ✅ concluído

Etapa 2 da fórmula de comissão (etapa 1 foi o [Incremento 11](incremento-11.md) — campo de texto). Agora existe o construtor visual que você pediu.

## O que foi feito

- **Backend**: novo endpoint `POST /bancos/formula/teste` — recebe uma fórmula e um valor de exemplo de Total Bruto do Câmbio, retorna o resultado calculado (ou `400` se a fórmula for inválida), sem precisar salvar nenhum banco. Existe só para dar retorno imediato durante a montagem da fórmula.
- **Front-end**: componente `FormulaBuilder`, usado na tela de Bancos:
  - **Paleta** de blocos arrastáveis: `N` (Total Bruto do Câmbio), `(`, `)`, `+`, `−`, `×`, `÷`, `%`, e um bloco de número.
  - **Área de montagem**: arraste os blocos da paleta para lá, na ordem que formam a fórmula. Blocos podem ser removidos (×) ou reordenados (arrastando entre si).
  - Bloco de número vira um campo editável assim que solto — digite o valor direto nele.
  - **Pré-visualização** da fórmula resultante em tempo real (ex: `N*70%-N*70%*4,65%`), gerada a partir dos blocos.
  - **Testar fórmula**: campo para um Total Bruto de exemplo + botão que chama o backend e mostra o resultado (ou o erro), sem precisar salvar o banco.
  - Ao **editar um banco existente**, a fórmula salva é decomposta de volta em blocos automaticamente (um tokenizador simples no front-end), então você continua vendo e editando visualmente, não só o texto puro.

## Como foi validado

1. Testes de integração novos para o endpoint de teste de fórmula (`BancoControllerTests`) — fórmula válida retorna o resultado certo, fórmula inválida retorna `400`. Regressão completa: **65 testes, 0 falhas**.
2. **Ponta a ponta no navegador** (Playwright, contra a stack em Docker):
   - Abri a edição do banco TLX já cadastrado — a fórmula real (`N*70%-N*70%*4,65%`) apareceu decomposta corretamente em blocos visuais.
   - Montei uma fórmula nova **só arrastando blocos** (`N`, `×`, número, `%`), editei o número para `50` direto no bloco, testei com Total Bruto de exemplo `1000` — resultado `500` (correto: `1000 × 50%`).
   - Salvei o banco BZA com essa fórmula montada visualmente — apareceu certinho na tabela (`N*50%`), junto com a TLX preservada.
   - Sem erros no console em nenhuma etapa.

## O que ainda falta
- Só `N` (Total Bruto do Câmbio) está disponível como variável — se surgir necessidade de outra, isso exige uma nova rodada de confirmação (mesma pendência já registrada no Incremento 11).
- O reordenamento de blocos já existentes dentro do canvas funciona via arrastar, mas não foi testado tão a fundo quanto o fluxo principal (montar do zero) — vale ficar de olho no uso real.

## Próximo passo
Depende de você: seguir para Fechamento diário, Relatórios, resolver os pontos de "pronto para uso real" (SMTP, credenciais), ou revisar algo já entregue.
