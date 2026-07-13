# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the project
./mvnw clean compile

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=LoveAppTest

# Run a single test method
./mvnw test -Dtest=LoveAppTest#testChat

# Start the application (port 8123, context path /api)
./mvnw spring-boot:run

# Package as JAR
./mvnw clean package -DskipTests
```

## Architecture Overview

**Tech stack**: Spring Boot 3.5.14, Java 21, Maven, Alibaba DashScope (Qwen) via Spring AI Alibaba.

**Core domain**: An AI-powered love/relationship counseling agent. Uses LLM chat with optional RAG over Chinese-language relationship advice documents stored as markdown.

### Key Components

- **`LoveApp`** (`app/LoveApp.java`) — Central chat component. Constructs a `ChatClient` with system prompt (love psychology expert persona), chat memory, and optional advisors. Provides three chat methods:
  - `doChat()` — Simple multi-turn conversation with memory
  - `doChatWithReport()` — Structured output via `entity(LoveReport.class)`
  - `doChatWithRag()` — Chat augmented with vector store knowledge base (currently using pgvector store)

- **Advisors** (`advisor/`) — Spring AI call-interceptor chain:
  - `MyLoggerAdvisor` — Logs user prompts and AI responses
  - `ReReadingAdvisor` — Implements Re2 (Re-Reading) strategy to improve reasoning by repeating the user query

- **Chat Memory** (`chatmemory/`) — `FileBasedChatMemory` persists conversations to disk using Kryo serialization (`chat-memory/*.kryo` files), keyed by conversation ID.

- **RAG Pipeline** (`rag/`):
  - `LoveAppDocumentLoader` — Loads markdown documents from `classpath:document/*.md` using `MarkdownDocumentReader`
  - `LoveAppVectorStoreConfig` — In-memory `SimpleVectorStore` bean (`loveAppVectorStore`)
  - `PgVectorVectorStoreConfig` — PostgreSQL pgvector store bean (`pgVectorVectorStore`), with HNSW index, cosine distance, 1536 dimensions. The main app class **excludes** `PgVectorStoreAutoConfiguration` in favor of this manual config.

- **Demo Invocation** (`demo/invoke/`) — Three approaches to call the AI model (for reference): raw HTTP via Hutool, Alibaba SDK, and Spring AI `ChatModel`. Not used in production paths.

### Configuration

- **Active profile**: `local` (set in `application.yml`)
- **`application-local.yml`** contains the DashScope API key and PostgreSQL connection string for the RAG vector store
- Swagger UI at `/api/swagger-ui.html` (Knife4j)
- Health check endpoint at `GET /api/health`

### Document Resources

RAG knowledge base consists of three markdown files under `src/main/resources/document/`:
- 恋爱常见问题和回答 - 单身篇.md (singles)
- 恋爱常见问题和回答 - 恋爱篇.md (dating)
- 恋爱常见问题和回答 - 已婚篇.md (married)

These are loaded at startup and embedded into the vector store.
