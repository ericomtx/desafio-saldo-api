package com.ericomtx.saldoapi.bootstrap;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

// Cria as tabelas se não existirem — conveniência pra dev local. Em
// produção fica desligado (APP_AWS_BOOTSTRAP_TABLES=false no ecs.tf),
// já que as tabelas são do Terraform e a task role nem tem permissão pra isso.
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
            // web e consumer sobem juntos e podem tentar criar ao mesmo
            // tempo — quem chega depois cai aqui, sem problema.
            LOG.info("Tabela '{}' já foi criada por outra instância — seguindo normalmente.", tableName);
        }
    }
}