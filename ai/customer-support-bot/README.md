# Customer Support Bot — Future Investigation

## Concept

An agentic chatbot that handles multi-turn customer conversations, looks up a product knowledge base, and resolves or escalates issues autonomously without human intervention until explicitly required.

## Key Capabilities to Explore

- **Multi-turn conversation memory** — maintain context across HTTP requests using Redis or an in-process session store
- **RAG knowledge base** — embed product docs into a vector store (pgvector on the existing Postgres from `docker-compose.yml`), retrieve relevant chunks per user turn
- **Escalation policy** — if Claude's confidence is low or user sentiment turns negative over N consecutive turns, hand off to a human queue and emit a structured escalation event
- **Outcome tracking** — each conversation produces a typed result (resolved / escalated / abandoned) written to the DB for downstream analytics

## Suggested Stack

- Spring Boot + Spring AI (`ChatClient` with `@Tool`-annotated methods)
- pgvector extension on the shared Postgres container for embeddings
- Claude with a system prompt defining the support persona and escalation threshold

## Starting Points

- Spring AI docs: https://docs.spring.io/spring-ai/reference/
- pgvector with Spring AI: search "VectorStore" in Spring AI docs
- Compare Spring AI vs LangChain4j for RAG pipeline complexity before committing
