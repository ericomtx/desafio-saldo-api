# ADR 0004 — O saldo mostrado pode estar levemente atrasado

## Decisão
A API sempre responde com o saldo mais recente que ela já processou — sem
esperar ou verificar se tem alguma transação ainda "no caminho" na fila.

## Por quê
Como a ingestão acontece por fila, sempre vai existir uma pequena janela de
tempo entre "a transação foi enviada" e "o saldo já está atualizado no
banco, pronto pra API mostrar". Isso é chamado de consistência eventual —
e, dado que o sistema usa fila, é praticamente inevitável, não é algo que
eu poderia simplesmente "desligar".

A alternativa seria fazer a API esperar/checar a fila antes de responder,
mas isso não faz sentido: o banco funciona 24 horas por dia recebendo
transações continuamente, então nunca existe um momento "a fila está
vazia, pode responder com certeza total".

Prefiro deixar isso claro e documentado do que fingir uma garantia que o
sistema não tem.

## O que isso significa na prática
Numa operação normal, esse atraso deve ser de milissegundos, talvez alguns
segundos no pico de movimento. Não é "saldo em tempo real perfeito", é
"saldo mais atualizado disponível no momento".
