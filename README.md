# API de Consulta de Saldo

Desafio técnico — Engenheiro de Software (squad core banking).

Consome transações financeiras de uma fila SQS e expõe uma API REST para
consulta do saldo mais atual de uma conta.

## Arquitetura

O sistema é dividido em **duas aplicações independentes**, cada uma com seu
próprio deploy — não é um monólito fazendo as duas coisas:

- **`consumer/`** — worker que consome a fila SQS
  (`transacoes-financeiras-processadas`), aplica as regras de
  consistência/idempotência, e persiste no DynamoDB. Sem servidor HTTP.
- **`web/`** — API REST (`GET /balances/{accountId}`) que só lê o saldo
  persistido. Não toca na fila.

As duas compartilham código comum (DTOs, acesso a dados, config de clientes
AWS) através do módulo **`common/`**, que não é deployável sozinho — só
existe como dependência dos outros dois.

Essa separação existe porque as duas cargas têm padrões de escala
completamente diferentes (consumer escala pela profundidade da fila, API
escala por CPU/tráfego) — ver [`terraform/autoscaling.tf`](terraform/autoscaling.tf)
e [`docs/adr/0006-escala-consumidor.md`](docs/adr/0006-escala-consumidor.md).

As decisões técnicas por trás de cada escolha (banco de dados, tratamento de
mensagens fora de ordem, idempotência, resiliência, estratégia de deploy)
estão documentadas em [`docs/adr/`](docs/adr/README.md).

## Estrutura do projeto

```
desafio-saldo-api/
├── common/       biblioteca compartilhada (não deployável)
├── web/          API REST — deployável, tem Dockerfile próprio
├── consumer/     worker de ingestão — deployável, tem Dockerfile próprio
├── docs/adr/     decisões arquiteturais
└── terraform/    infraestrutura como código (ECS, DynamoDB, SQS, ALB, API Gateway, Auto Scaling, IAM)
```

## Stack

- Java 21 + Spring Boot 3.3, projeto Maven multi-módulo
- AWS SDK v2 (DynamoDB, SQS)
- Resilience4j (retry + circuit breaker)
- LocalStack (simula SQS localmente, via Docker) — usado só em dev
- Terraform (infraestrutura real da AWS — ver `terraform/`)

## Como rodar localmente

### 1. Pré-requisitos

- Java 21
- Maven
- Docker (ou Podman com suporte a `compose`)

### 2. Subir a fila SQS local (LocalStack)

Este projeto depende de um `docker-compose.yml` que sobe o LocalStack com a
fila já populada com 300.000 transações sintéticas em 10.000 contas —
fornecido junto com o desafio (não confundir com o Terraform em `terraform/`,
que é infraestrutura de produção).

**Importante**: esse `docker-compose.yml` vem configurado com
`SERVICES=sqs` apenas — como este projeto também usa DynamoDB, é necessário
adicionar `dynamodb` à lista antes de subir:

```yaml
# dentro do serviço "localstack" no docker-compose.yml
environment:
  - SERVICES=sqs,dynamodb
```

```
docker compose up
```

Aguarde a mensagem `message-generator exited with code 0` — isso confirma
que a fila está pronta.

### 3. Compilar o projeto inteiro

Na raiz (compila os 3 módulos, na ordem certa de dependência):

```
mvn clean install
```

### 4. Rodar as duas aplicações

Duas formas — escolha uma:

**Opção A — via Docker/Podman (mais próximo de produção, builda as imagens
de verdade a partir dos Dockerfiles):**

```
docker compose -f docker-compose.yml -f docker-compose.apps.yml up --build
```

Isso combina o `docker-compose.yml` do desafio (LocalStack + gerador de
transações) com o `docker-compose.apps.yml` deste repositório (as duas
aplicações) — todos os três serviços (LocalStack, web, consumer) sobem
juntos, na mesma rede, conectados automaticamente.

Se estiver usando Podman em vez de Docker (comum em ambientes corporativos
que não liberam Docker), o comando equivalente é:

```
podman compose -f docker-compose.yml -f docker-compose.apps.yml up --build
```

**Opção B — via Maven (mais rápido pra iterar durante desenvolvimento),
em terminais separados:**

```
mvn spring-boot:run -pl consumer
```

```
mvn spring-boot:run -pl web
```

Ao subir, cada aplicação:
- Cria automaticamente as tabelas do DynamoDB no LocalStack (`saldos` e
  `transacoes-processadas`), se ainda não existirem — só em dev; em
  produção isso é desligado via `APP_AWS_BOOTSTRAP_TABLES=false`
  (ver `terraform/ecs.tf`), já que a IAM Role de produção não tem permissão
  de `CreateTable` (least privilege).
- O `consumer` começa a consumir a fila imediatamente.
- O `web` só sobe a API — não faz nada até receber uma requisição.

### 5. Consultar um saldo

```
curl http://localhost:8080/balances/{accountId}
```

Troque `{accountId}` por um UUID de conta real — qualquer um dos que
aparecem nas mensagens publicadas na fila. Resposta esperada:

```json
{
  "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
  "owner": "315e3cfe-f4af-4cd2-b298-a449e614349a",
  "balance": { "amount": 183.12, "currency": "BRL" },
  "updated_at": "2025-07-04T18:04:13.433Z"
}
```

Se a conta ainda não tiver nenhuma transação processada, a API retorna `404`.

### 6. Rodar os testes

```
mvn test
```

## Deploy em produção (AWS real)

A infraestrutura completa está em [`terraform/`](terraform/) — cada
aplicação vira sua própria imagem Docker (`web/Dockerfile`,
`consumer/Dockerfile`), seu próprio ECS Service, com Auto Scaling
independente. A API fica atrás de um ALB interno + API Gateway; o consumer
não recebe tráfego de entrada nenhum, só consome a fila.

```
cd terraform
terraform init
terraform plan
```

Ver [`docs/adr/0007-estrategia-pipeline-deploy.md`](docs/adr/0007-estrategia-pipeline-deploy.md)
para a estratégia de deploy (canary) proposta para mitigar risco de bug em
produção.

## Documentação adicional

- [`docs/adr/`](docs/adr/README.md) — decisões arquiteturais, com motivadores e alternativas consideradas
- [`docs/diagrams/deploy-arquitetura-cloud.svg`](docs/diagrams/deploy-arquitetura-cloud.svg) — diagrama de deploy em cloud (rascunho — recriar em draw.io com ícones oficiais AWS antes da entrega final)
- [`docs/diagrams/pipeline-deploy-canary.svg`](docs/diagrams/pipeline-deploy-canary.svg) — pipeline de deploy com estratégia canary
