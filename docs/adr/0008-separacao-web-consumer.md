# ADR 0008 — Separar em duas aplicações (web e consumer), não uma só

## Decisão
A ingestão (consome a fila) e a exposição (API REST) viraram **duas
aplicações Spring Boot separadas**, cada uma com seu próprio JAR, sua
própria imagem Docker, e seu próprio ECS Service — não um único processo
fazendo as duas coisas.

O código comum entre as duas (DTOs, acesso ao DynamoDB, configuração dos
clientes AWS) ficou num terceiro módulo, `common`, que não roda sozinho —
só existe como dependência compartilhada.

## Por quê
As duas cargas têm padrões de escala completamente diferentes:
- O consumer precisa escalar conforme a fila cresce ou esvazia (pico de
  2000 msg/s).
- A API precisa escalar conforme volume de requisições HTTP dos clientes.

Num único processo, essas duas necessidades ficam acopladas — escalar pra
atender um pico na fila também escalaria (sem necessidade) a capacidade de
atender requisições HTTP, e vice-versa. Separado, cada serviço escala só
pelo que realmente importa pra ele (ver `terraform/autoscaling.tf`).

Também simplifica permissão: a task role da API só precisa de
`dynamodb:GetItem` na tabela de saldos — nada de fila, nada de escrita. A
task role do consumer é que tem acesso de escrita e à fila. Num processo
único, isso não daria pra separar — a mesma identidade precisaria de todas
as permissões, violando least privilege.

## Outras opções que pensei

**Manter um único processo, com um profile Spring pra ligar/desligar o
consumer** (ex: `spring.profiles.active=api-only` desabilitando o bean do
`SqsConsumer`). Rejeitada: ainda seria a mesma imagem Docker pros dois
casos, escalando por um único `desired_count`, sem separação real de
IAM/permissões — resolveria só parcialmente o problema.

## Consequências
- Precisa buildar e publicar duas imagens Docker em vez de uma (ver
  `web/Dockerfile` e `consumer/Dockerfile`).
- Localmente, é preciso rodar as duas aplicações ao mesmo tempo, em
  processos separados (ver README) — um pouco mais de fricção pra
  desenvolvimento do que um único `mvn spring-boot:run`.
- Ganho: cada serviço em produção só tem exatamente a permissão IAM que
  precisa, e escala de forma independente e mais barata (não paga pra
  escalar a API quando só a fila está cheia, e vice-versa).
