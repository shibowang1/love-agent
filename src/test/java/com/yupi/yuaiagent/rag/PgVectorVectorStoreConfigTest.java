package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PgVectorVectorStoreConfigTest {

    @Test
    void generatesStableIdsForKnowledgeChunks() {
        List<Document> chunks = List.of(
                new Document("unstable-a", "沟通时先倾听", Map.of("source", "dating.md")),
                new Document("unstable-b", "再表达自己的感受", Map.of("source", "dating.md")));

        List<Document> first = PgVectorVectorStoreConfig.withDeterministicIds(chunks);
        List<Document> second = PgVectorVectorStoreConfig.withDeterministicIds(chunks);

        assertEquals(first.stream().map(Document::getId).toList(), second.stream().map(Document::getId).toList());
        assertNotEquals(first.get(0).getId(), first.get(1).getId());
        assertEquals(0, first.get(0).getMetadata().get("chunk_index"));
    }
}
