# ADR 0003 — Evitar processar a mesma transação duas vezes

## Decisão
Cada transação tem um ID único. Guardo os IDs já processados por um tempo
(24 horas) pra saber se uma mensagem já foi vista antes.

## Ordem importa: marcar só DEPOIS de escrever com sucesso
A verificação ("já vi esse ID?") acontece antes de processar. Mas marcar
como processada só acontece **depois** que o saldo é gravado com sucesso —
nunca antes.

Isso não é só um detalhe de implementação: se a transação fosse marcada
como processada antes da escrita do saldo, e essa escrita falhasse por
qualquer motivo, a mensagem reentregue pela fila veria "já processada" e
seria ignorada — só que o saldo nunca teria sido aplicado de verdade. A
transação ficaria "perdida" silenciosamente, sem erro nenhum aparente.
Marcar só após o sucesso garante que uma falha no meio do caminho sempre
permite nova tentativa.

## Por quê ter dedup, já que a escrita é condicional (ADR 0002)
A fila usada garante que toda mensagem será entregue **pelo menos uma
vez** — o que, na prática, significa que às vezes a mesma mensagem pode
chegar repetida. O sistema precisa lidar bem com isso.

Vale notar: mesmo sem essa proteção, a regra da ADR 0002 (só atualiza se
for mais recente) já evitaria que o dado ficasse errado no banco — uma
mensagem repetida, na segunda vez, simplesmente não passaria na
verificação de "é mais novo?". Mas guardar os IDs já vistos ajuda a
detectar e contar quantas mensagens duplicadas estão chegando, o que é
útil pra monitorar a saúde do sistema.

## Outras opções que pensei

**Não fazer nada além da ADR 0002** — considerei, já que tecnicamente
funcionaria. Mas decidi manter essa camada extra pra ter visibilidade sobre
duplicatas, não só corrigir o resultado final silenciosamente.
