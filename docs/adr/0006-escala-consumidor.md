# ADR 0006 — Como dar conta de 2000 mensagens por segundo

## Decisão
Duas coisas combinadas:

**Ler em lotes, não uma mensagem por vez.** A fila permite pegar até 10
mensagens de uma vez só. Isso reduz bastante o número de "viagens" até a
fila, comparado a buscar mensagem por mensagem.

**Rodar várias cópias do consumidor ao mesmo tempo.** Em vez de um único
processo lendo a fila sozinho, várias instâncias podem ler a mesma fila em
paralelo, sem risco de duas pegarem a mesma mensagem — a fila já cuida
disso sozinha.

## Por quê
Um único processo, lendo uma mensagem de cada vez, não aguentaria 2000
mensagens por segundo — a demora de cada chamada ao banco, multiplicada por
esse volume, estouraria rápido. Ler em lote e rodar em paralelo são as duas
formas mais diretas de multiplicar a capacidade sem complicar demais o
código.

## O que isso implica
Já que várias cópias processam ao mesmo tempo, não dá pra garantir a ordem
entre mensagens de contas diferentes — o que reforça por que a regra da ADR
0002 (só atualiza se for mais recente) é tão importante: com processamento
em paralelo, mensagens fora de ordem ficam ainda mais prováveis, não menos.
