# ADR 0005 — Como lidar com falhas ao salvar no banco

## Decisão
Três proteções, cada uma cobrindo um tipo diferente de problema:

**1. Tentar de novo, com espera crescente e um pouco de aleatoriedade.**
Se uma escrita no banco falhar por um problema passageiro (banco
sobrecarregado, timeout), o sistema tenta de novo automaticamente, esperando
um pouco mais a cada tentativa. Adiciono também um "tempo aleatório" em
cima da espera — isso evita que, se várias partes do sistema falharem ao
mesmo tempo, todas tentem de novo exatamente no mesmo instante e piorem a
sobrecarga.

**2. Parar de tentar se o banco estiver realmente fora do ar (circuit
breaker).**
Se as falhas continuarem acontecendo demais em pouco tempo, o sistema para
de tentar por um tempo, em vez de continuar insistindo sem parar. Isso evita
sobrecarregar ainda mais um banco que já está com problema, e dá tempo pra
ele se recuperar.

**3. Mensagens problemáticas vão para uma "fila de erro" (DLQ).**
Se uma mensagem específica falhar repetidamente mesmo depois de várias
tentativas (por exemplo, por estar com formato errado), ela é
automaticamente movida pra uma fila separada, só de mensagens com problema.
Assim ela não fica travando o processamento das mensagens seguintes, e dá
pra investigar depois com calma.

*(Implementado em `terraform/sqs.tf` — `redrive_policy` com
`maxReceiveCount = 5`, apontando pra uma fila `-dlq` separada.)*

## Por quê
O desafio pede atenção a esse tipo de proteção, e o ponto mais frágil do
sistema é justamente a escrita no banco — é onde algo pode falhar por
motivos fora do nosso controle (rede, sobrecarga momentânea, etc).
