# Assumptions and limitations — Phase 1

1. This is a development foundation, not a publicly secured production service. Authentication, authorization, abuse controls, and rate limiting are not implemented.
2. No Amazon, Flipkart, or other marketplace has been contacted. No API access, current prices, stock, product URLs, or product specifications are assumed or fabricated.
3. Chat responses come from an actual configured LLM, but its general knowledge is not verified product evidence. Prompt instructions cannot guarantee factual correctness or prompt-injection resistance.
4. Chat is stateless. A follow-up such as “show only Lenovo” does not yet have prior search context.
5. Ollama must be installed separately, running, and have the configured model pulled. The application does not download weights on startup.
6. The default 7B model is a development recommendation, not a measured best model. Hardware needs and output quality vary. The 0.5B CI model is for connectivity only.
7. Hosted use requires an authorized account, a supported model, network access, and potentially paid usage. Setting `store=false` requests disabled provider response storage where supported; it does not mean the provider retains no operational or abuse-monitoring data. Review the provider's policy.
8. Sending a prompt to a hosted endpoint discloses that prompt to the endpoint. `OPENAI_BASE_URL` must therefore be trusted. Never put secrets in user messages.
9. Health/readiness report application availability, not model reachability. A live smoke test is separate to avoid model calls on every probe.
10. Timeouts and retry counts are bounded and configurable. There is no circuit breaker, provider failover, request deduplication, or distributed rate limit yet.
11. Provider errors all map to a safe 503 in this milestone; the API does not yet distinguish bad credentials, quota exhaustion, and timeouts with separate public codes. Diagnose using provider status and sanitized local logs.
12. Message validation happens after JSON deserialization. A public deployment still needs upstream request-size limits and abuse controls; the 4000-character DTO constraint is not a network-level body-size firewall.
13. OpenAPI is checked in and contract-tested, not generated with an interactive UI yet.
14. Spring AI supplies model observation instrumentation; a production metrics/tracing backend and token-cost dashboards are not configured yet.
15. No conversation or search data is persisted. PostgreSQL and pgvector are intentionally deferred until their features are implemented.
16. Marketplace terms, official API availability, credentials, caching permissions, and attribution requirements must be investigated before live integration. Synthetic providers will be clearly labelled.
17. Docker/Compose, React, RAG, AWS, and full CI/CD are later phases. The existing workflows only build/test this backend and optionally smoke-test Ollama.
18. The sandbox could not create the requested second GitHub repository because the integration lacks repository-creation permission. Existing-branch pushes and the exact setup for the new repository are documented separately.
