package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;

@Configuration
@Slf4j
public class PgVectorVectorStoreConfig {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Bean
    public PgVectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate,
                                             EmbeddingModel dashscopeEmbeddingModel,
                                             @Value("${love-app.rag.pgvector.table-name:love_app_vector_store}")
                                             String tableName,
                                             @Value("${love-app.rag.pgvector.index-type:NONE}")
                                             PgIndexType indexType) {
        validateSqlIdentifier(tableName);
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(COSINE_DISTANCE)
                .indexType(indexType)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName(tableName)
                // DashScope text embedding accepts at most 10 inputs per request.
                .maxDocumentBatchSize(10)
                .build();
    }

    @Bean
    public ApplicationRunner loveAppKnowledgeBaseInitializer(
            PgVectorStore pgVectorVectorStore,
            JdbcTemplate jdbcTemplate,
            LoveAppDocumentLoader documentLoader,
            @Value("${love-app.rag.pgvector.table-name:love_app_vector_store}") String tableName,
            @Value("${love-app.rag.pgvector.index-type:NONE}") PgIndexType indexType) {
        return args -> initializeKnowledgeBase(
                pgVectorVectorStore, jdbcTemplate, documentLoader, tableName, indexType);
    }

    private void initializeKnowledgeBase(VectorStore vectorStore,
                                         JdbcTemplate jdbcTemplate,
                                         LoveAppDocumentLoader documentLoader,
                                         String tableName,
                                         PgIndexType indexType) {
        validateSqlIdentifier(tableName);
        List<Document> sourceDocuments = documentLoader.loadMarkdowns();
        TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 10, 5000, true);
        List<Document> chunks = withDeterministicIds(splitter.apply(sourceDocuments));
        List<Document> missingChunks = findMissingChunks(jdbcTemplate, tableName, chunks);
        log.info("Loading relationship knowledge base: table={}, indexType={}, documents={}, chunks={}, missing={}",
                tableName, indexType, sourceDocuments.size(), chunks.size(), missingChunks.size());
        addInBatches(vectorStore, missingChunks, 10);
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            VectorStore pgVectorVectorStore, ChatModel dashscopeChatModel) {
        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(dashscopeChatModel))
                .targetSearchSystem("恋爱心理学知识库")
                .build();
        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(pgVectorVectorStore)
                .topK(5)
                .similarityThreshold(0.5)
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer)
                .documentRetriever(documentRetriever)
                .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 10)
                .build();
    }

    static List<Document> withDeterministicIds(List<Document> chunks) {
        List<Document> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Document chunk = chunks.get(index);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("chunk_index", index);
            String source = String.valueOf(metadata.getOrDefault("source", "relationship-knowledge"));
            String identity = source + ':' + index + ':' + chunk.getText();
            String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            result.add(new Document(id, chunk.getText(), metadata));
        }
        return result;
    }

    private void addInBatches(VectorStore vectorStore, List<Document> documents, int batchSize) {
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }

    private List<Document> findMissingChunks(JdbcTemplate jdbcTemplate,
                                             String tableName,
                                             List<Document> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(chunks.size(), "?"));
        Object[] ids = chunks.stream().map(Document::getId).toArray();
        List<String> existingIds = jdbcTemplate.queryForList(
                "SELECT id::text FROM public." + tableName + " WHERE id::text IN (" + placeholders + ")",
                String.class,
                ids);
        Set<String> existingIdSet = new HashSet<>(existingIds);
        return chunks.stream()
                .filter(chunk -> !existingIdSet.contains(chunk.getId()))
                .toList();
    }

    private static void validateSqlIdentifier(String identifier) {
        if (!SQL_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid pgvector table name: " + identifier);
        }
    }
}
