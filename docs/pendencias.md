# Pendências (decisões de negócio ainda não definidas)

Nada aqui deve ser implementado com base em suposição. São dívidas técnicas/de produto — perguntar ao usuário antes de codar a parte do sistema que depende de cada item.

## Bloqueiam a entidade Operação

~~1. Status da transação~~ — **RESOLVIDO no Incremento 7**: são só dois status, "Em andamento" e "Completo", e qualquer pessoa autenticada pode alterar o status de uma operação (sem restrição de perfil).
2. **Domínio do campo C/V** — confirmar todos os valores possíveis (conhecidos: `C`, `V`; pode existir algo como vazio/`NA` enquanto a operação está em andamento?).
3. **Significado de negócio de PR/CR/VIR** — os valores possíveis são conhecidos (`Pronto`, `Crédito`, `Virtual`), mas não o que cada um implica no processo operacional. Importante: são independentes entre si e independentes do campo Banco — não assumir relação entre eles.
4. **Regras de validação** dos campos da Operação (obrigatoriedade, valores mínimos, consistência entre Nivelamento/Taxa Final, etc.) — nenhuma definida ainda.
5. **Modelagem do cadastro de moedas** — confirmado que o sistema deve suportar múltiplas moedas estrangeiras (não só USD), com BRL como moeda de liquidação. Falta definir: lista fixa (ex: códigos ISO 4217) ou cadastro livre/administrável? Isso será decidido quando chegarmos ao Incremento 5 (Registro de Operação), não bloqueia os incrementos anteriores.
6. **"Total em reais" e "Reais final"** (campos da visão inicial) — não confirmado se são sinônimos de "R$", campos calculados diferentes, ou correspondem ao "Valor Recebido Líquido Real" da tela nova.

## Bloqueiam a tela de Operação (referência visual)

7. **Imagem da tela de referência** do cliente ainda não foi recebida — necessária para confirmar layout (duas colunas, agrupamento de campos, etc.) e para investigar os campos abaixo.
8. **Os 13 campos novos identificados na tela** — nenhum tem fórmula, origem ou regra de negócio definida ainda:
   - Data da Liquidação
   - Valor Moeda (possível duplicata de "Valor em ME")
   - Cotação Nivelamento (possível duplicata de "Nivelamento")
   - Cotação Final (possível duplicata de "Taxa Final")
   - Valor Histórico em Aberto
   - Despesa do Banqueiro
   - Despesa do Banqueiro em Real
   - Valor IOF Despesa Banqueiro
   - Valor IR Despesa Banqueiro
   - Valor Total Despesa Banqueiro
   - Total Ajuste NDF
   - Valor Recebido Líquido Real

## Bloqueiam controle de acesso fino

~~9. Permissões detalhadas por perfil~~ — **RESOLVIDO**: 3 perfis (Admin, Analista — renomeado de "Usuário" —, Consultor só-visualização). Operação só edita "Em andamento", por Admin/Analista; qualquer perfil autenticado vê relatórios/fechamentos/histórico. Ver [decisoes.md](decisoes.md).

## Bloqueiam o uso real da autenticação em produção

10. ~~Envio real de e-mail (SMTP)~~ Resolvido: implementado envio por SMTP (`SmtpMagicLinkSender`), desligado por padrão (`LoggingMagicLinkSender` continua ativo em dev). A empresa vai usar o relay SMTP do Microsoft 365 / Exchange Online hospedado no Azure — **ainda falta o plano do Azure ser contratado**, então as credenciais reais continuam pendentes. Enquanto isso, o fluxo de envio real já pode ser testado de ponta a ponta contra o Mailpit (serviço incluso no `docker-compose.yml`) — ver `.env.example` e `docs/decisoes.md`.

## Fora do escopo atual, mas mencionadas na visão original (retomar quando chegar a vez)

11. Regras específicas de PF vs. PJ no cadastro de Cliente.
12. Cálculo de IOF/IR (explicitamente adiado pelo usuário, mas os campos da nova tela sugerem que pode voltar a ser necessário).
13. ~~Fechamento diário — formato, conteúdo, exportação.~~ Resolvido no Incremento 13 (13a-13d) — ver [decisoes.md](decisoes.md).
14. Relatórios analíticos — quais métricas, para quais perfis. Inclui os comparativos/tendências deliberadamente deixados de fora do Fechamento Diário.
