# ADR 0001 — Escolha do banco de dados

## Decisão
Usar **DynamoDB**.

## Por quê
A API só precisa fazer uma coisa: dado o ID de uma conta, achar o saldo
dela. Não tem relatório, não tem cruzamento de dados, é busca simples por
chave. É exatamente o tipo de problema que um banco de chave-valor resolve
bem.

Também escolhi DynamoDB porque:
- Já é da AWS, então não precisa instalar/manter nenhum banco separado.
- Aguenta os 2000 registros por segundo pedidos no desafio sem precisar de
  ajuste fino de configuração.
- Tem um recurso nativo (escrita condicional) que ajuda bastante a resolver
  o problema de mensagens chegando fora de ordem — ver ADR 0002.

## Outras opções que pensei

**PostgreSQL** — mais familiar, mas exigiria mais trabalho manual pra
aguentar esse volume de escrita, e a "escrita condicional" que preciso
teria que ser feita na mão.

**Redis** — rápido, mas é feito pra ser mais um cache do que a fonte
principal de verdade. Pra dado de saldo bancário, prefiro algo pensado pra
ser persistente por padrão.
