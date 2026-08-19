# Decisões de arquitetura (ADRs)

Aqui estão registradas as principais decisões técnicas do projeto — o que
foi escolhido, por quê, e o que mais foi considerado.

| ADR | Decisão |
|---|---|
| [0001](0001-escolha-banco-de-dados.md) | Qual banco de dados usar |
| [0002](0002-estrategia-ordenacao-conflitos.md) | O que fazer quando mensagens chegam fora de ordem |
| [0003](0003-idempotencia-ingestao.md) | Evitar processar a mesma transação duas vezes |
| [0004](0004-modelo-consistencia.md) | Por que o saldo mostrado pode estar levemente atrasado |
| [0005](0005-padroes-resiliencia.md) | Como lidar com falhas ao salvar no banco |
| [0006](0006-escala-consumidor.md) | Como dar conta de 2000 mensagens por segundo |
| [0007](0007-estrategia-pipeline-deploy.md) | Estratégia de pipeline e deploy (canary) |
| [0008](0008-separacao-web-consumer.md) | Por que separar em duas aplicações (web e consumer) |
