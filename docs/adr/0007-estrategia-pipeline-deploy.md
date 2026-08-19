# ADR 0007 — Estratégia de pipeline e deploy

## Decisão
Usar **deploy canary**: toda nova versão vai primeiro pra só 10% do
tráfego, fica um tempo sendo observada automaticamente, e só depois é
liberada pros outros 90% — ou é revertida sozinha, se algo der errado.

## Por quê
O ponto que mais importa aqui é o que o próprio desafio pede: uma
estratégia que evite que um bug afete **todos** os clientes de uma vez.

Se eu simplesmente atualizasse a aplicação inteira de uma vez só, um bug
que passasse despercebido nos testes afetaria 100% dos clientes assim que
o deploy terminasse. Com canary, na pior das hipóteses, só uma fração
pequena do tráfego é afetada, e por pouco tempo — o próprio sistema detecta
o problema (via métricas de erro/latência) e desfaz a mudança sozinho,
antes que o resto dos clientes seja exposto.

## Como funciona, na prática
1. Código passa por testes automatizados antes de qualquer deploy.
2. A nova versão é publicada, mas recebendo só 10% das requisições — a
   versão antiga continua respondendo os outros 90%.
3. Por um período definido (por exemplo, 10-15 minutos), o sistema observa
   métricas como taxa de erro e tempo de resposta dessa nova versão.
4. Se as métricas piorarem além de um limite aceitável, a mudança é
   desfeita automaticamente, sem precisar de ninguém intervir na hora.
5. Se estiver tudo certo, o tráfego migra gradualmente até chegar em 100%
   na nova versão.

## Outras opções que pensei

**Blue-Green (troca tudo de uma vez, mas com ambiente de fallback pronto)**
— também resolve bem o problema de rollback rápido, mas não limita quantos
clientes são afetados no momento da troca: se o bug só aparece sob tráfego
real (não pego nos testes), os 100% dos clientes já foram expostos antes de
alguém perceber e reverter. Canary é mais direto pra resolver
especificamente "não afetar todos os clientes de uma vez", que é o que foi
pedido.

**Deploy direto, sem estratégia especial** — rejeitado, é exatamente o
cenário que o desafio pede pra evitar.

## O que isso precisa, tecnicamente
Como o sistema já roda em ECS Fargate (ver diagrama de deploy), essa
estratégia é viável usando o **AWS CodeDeploy**, que tem suporte nativo a
deploys canary pra ECS — não precisa construir esse controle de tráfego
gradual na mão.
