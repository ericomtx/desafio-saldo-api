# ADR 0002 — O que fazer quando as mensagens chegam fora de ordem

## Decisão
Antes de salvar um saldo novo, comparo o horário dessa transação com o
horário do que já está salvo. Só atualizo se a transação nova for **mais
recente** do que a que já está no banco.

## Por quê
A fila usada (SQS Standard) não garante que as mensagens cheguem na ordem
que foram enviadas. Isso significa que uma transação mais antiga pode, às
vezes, chegar depois de uma mais nova.

Se eu simplesmente salvasse "o que chegou por último", correria o risco de
sobrescrever um saldo correto e atualizado com um saldo antigo — e o
cliente veria um saldo errado até a próxima transação chegar.

Cada mensagem já vem com um horário bem preciso (em microssegundos). Uso
esse horário como critério: "só atualiza se for mais novo do que o que já
está salvo". Se chegar uma mensagem atrasada, ela é simplesmente ignorada
(não é erro, é esperado) — e eu registro isso em log só pra acompanhar com
que frequência isso acontece.

## Um detalhe importante: transações rejeitadas
O gerador de transações simula rejeição por saldo insuficiente. Quando isso
acontece, o saldo que vem dentro da mensagem **não é um saldo novo pra
aplicar** — é o saldo que já existia antes daquela tentativa de débito
(a transação foi barrada, então o saldo não mudou de verdade).

Isso significa que a regra de "só atualiza se for mais recente" (explicada
acima) continua valendo normalmente mesmo pra mensagens rejeitadas — o
sistema simplesmente grava esse mesmo saldo de novo, com o timestamp da
tentativa rejeitada. Na prática, isso funciona corretamente sem precisar de
nenhum tratamento especial: uma transação rejeitada só "confirma" o saldo
que já estava lá, não o altera.

## Outras opções que pensei

**Confiar na ordem de chegada** — rejeitei, é justamente o problema descrito
acima.

**Trocar a fila pra um tipo que garante ordem (FIFO)** — não dava pra
trocar, o desafio já define o tipo da fila. Mas mesmo que desse: a
garantia que esse sistema precisa não é "ordem de entrega", é "aplicar
sempre o dado mais recente" — isso já é resolvido pela escrita condicional
por timestamp acima, independente do tipo de fila. FIFO garante ordem
*como foi enviado*, mas não protege contra o produtor publicar fora de
ordem (retry, atraso de rede do lado de quem gera a transação) — só um
consumidor consciente de timestamp resolve isso de fato. Idempotência
também seria necessária de qualquer forma (FIFO só garante "exactly-once"
dentro de uma janela de 5 minutos), então a proteção que já construí não
seria eliminada trocando de fila — só mudaria onde a garantia mora.