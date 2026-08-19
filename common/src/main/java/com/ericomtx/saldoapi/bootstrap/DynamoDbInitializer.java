package com.ericomtx.saldoapi.bootstrap;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

/**
 * Cria as tabelas do DynamoDB automaticamente se não existirem — só por
 * conveniência para rodar localmente contra o localstack.
 *
 * Em produção, esse componente é DESLIGADO (ver terraform/ecs.tf, que
 * define APP_AWS_BOOTSTRAP_TABLES=false nas duas task definitions) — as
 * tabelas são provisionadas via Terraform, e a IAM Role da task nem tem
 * permissão de CreateTable/ListTables (least privilege). Sem essa flag, a
 * aplicação tentaria chamar uma API que não tem permissão e falharia no
 * startup em produção.
 */
@Component
@ConditionalOnProperty(name = "app.aws.bootstrap-tables", havingValue = "true", matchIfMissing = true)
public class DynamoDbInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(DynamoDbInitializer.class);

    private final DynamoDbClient dynamoDb;

    public DynamoDbInitializer(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @PostConstruct
    void init() {
        createTableIfNotExists("saldos", "accountId");
        createTableIfNotExists("transacoes-processadas", "transactionId");
    }

    private void createTableIfNotExists(String tableName, String partitionKey) {
        List<String> existing = dynamoDb.listTables().tableNames();
        if (existing.contains(tableName)) {
            return;
        }

        LOG.info("Tabela '{}' não existe, criando...", tableName);
        try {
            dynamoDb.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(KeySchemaElement.builder()
                    .attributeName(partitionKey)
                    .keyType(KeyType.HASH)
                    .build())
                .attributeDefinitions(AttributeDefinition.builder()
                    .attributeName(partitionKey)
                    .attributeType(ScalarAttributeType.S)
                    .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
        } catch (ResourceInUseException e) {
            // Corrida entre web e consumer subindo ao mesmo tempo — os dois
            // checam "existe?" antes de qualquer um terminar de criar. Quem
            // perde a corrida cai aqui: a tabela já existe (criada pelo
            // outro), então não é erro de verdade, só um "cheguei atrasado".
            LOG.info("Tabela '{}' já foi criada por outra instância — seguindo normalmente.", tableName);
        }
    }
}