# Phase 1: understand and run the first Spring AI endpoint

## 1. Requirement and scope

Build the smallest useful vertical slice:

```text
validated HTTP request → Spring AI → real model → nonempty HTTP answer
```

This proves dependency compatibility, provider wiring, configuration, error handling, and test strategy before introducing laptop search. It does not implement an agent, product search, or a recommendation engine.

## 2. AI concepts, translated for a Java backend developer

| AI concept | Java analogy / purpose | Used here? |
|---|---|---|
| Model / weights | The learned program that generates output; not a Java dependency | Through Ollama or hosted API |
| Inference | Executing that model for a request | One synchronous call |
| Prompt | Input instructions and data sent to the model | System + user messages |
| System message | Higher-priority behavioral instructions | Checked-in system prompt |
| User message | Untrusted task input | Validated `message` |
| Token | A model-specific piece of text, not necessarily a word/character | Provider counts them; our limit is characters |
| Context window | Maximum tokens the model can consider | Keep requests small; no history yet |
| Temperature | Sampling variability; lower is usually more predictable | 0.2, not a correctness guarantee |
| Output-token limit | Bound on generated response length | 512 configured output tokens |
| `ChatModel` | Spring AI's provider-adapter abstraction | Ollama or OpenAI implementation |
| `ChatClient` | Fluent application-facing API over a model | Injected into `ChatService` |
| Structured output | Mapping model output to a Java type and validating it | Phase 2 |
| Tool calling | Model proposes a typed application action; Java executes it | Later marketplace phases |
| Advisor | Interceptor around a model call, such as memory or RAG | None added in Phase 1 |
| Chat memory | Re-supplying previous messages as context | Not implemented |
| Embedding | Numerical representation useful for semantic retrieval | Not implemented |
| RAG | Retrieve evidence, then put relevant passages in model context | Phase 8 |

An LLM is probabilistic. A successful HTTP call does not make its statements true. In later phases, Java will own budget arithmetic and product filtering; the LLM will interpret language and explain grounded results.

## 3. Architecture and package structure

See [architecture.md](architecture.md) for component and sequence diagrams.

```text
.
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── .env.example
├── src/main/java/io/github/anushkasomi01/laptopfinder/
│   ├── LaptopFinderApplication.java
│   ├── api/
│   │   ├── ChatController.java
│   │   ├── ApiExceptionHandler.java
│   │   └── dto/{ChatRequest,ChatReply,ApiError}.java
│   ├── ai/
│   │   ├── ChatConfiguration.java
│   │   ├── ChatService.java
│   │   ├── EmptyModelResponseException.java
│   │   └── ModelProviderException.java
│   └── observability/CorrelationIdFilter.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-hosted.yml
│   └── prompts/assistant-system.txt
├── src/test/java/                 # unit, HTTP contract, adapter configuration, live smoke
├── src/test/resources/application-test.yml
├── docs/openapi.yaml
└── .github/workflows/
```

These are package boundaries inside one application, not independently deployed modules.

## 4. Dependencies and version choices

The complete build definition is in [`pom.xml`](../pom.xml).

- **JDK 21:** supported LTS Java baseline with records and modern language features.
- **Spring Boot 4.1.2:** stable parent managing the web, validation, actuator, and test stack.
- **Spring AI 2.0.2:** stable generation compatible with Boot 4.0.x / 4.1.x. Its BOM aligns Spring AI modules.
- **Maven Wrapper 3.9.16:** reproducible Maven bootstrap; contributors do not need a separate Maven installation.
- `spring-boot-starter-webmvc`: MVC REST endpoint. Boot 4 uses this name instead of the old `spring-boot-starter-web`.
- `spring-boot-starter-validation`: Jakarta Bean Validation on request DTOs.
- `spring-boot-starter-actuator`: health/readiness and management foundation.
- `spring-ai-starter-model-ollama`: local provider auto-configuration.
- `spring-ai-starter-model-openai`: optional hosted provider auto-configuration.
- `spring-boot-starter-webmvc-test`, test scope: Boot 4 MVC testing support, JUnit Jupiter, Mockito, AssertJ, and Spring test utilities.

Both providers are on the classpath, but `spring.ai.model.chat` selects exactly one. Other model capabilities are disabled because this phase needs only chat. This avoids accidentally creating an embedding model or downloading embedding weights.

The business service imports `ChatClient`, not `OllamaChatModel`, `OpenAiChatModel`, or either provider's SDK.

**Version migration warning:** Spring AI 2 uses flat properties such as `spring.ai.ollama.chat.model` and `spring.ai.openai.chat.temperature`. Older examples often use `chat.options.*`. Boot 4 also moves `@AutoConfigureMockMvc` to `org.springframework.boot.webmvc.test.autoconfigure`. Copying old imports/properties without checking the selected version is a common source of failures.

## 5. Important implementation steps

### Step A: define the public contract

`ChatRequest` is a Java record with `@NotBlank` and `@Size(max = 4000)`. `ChatReply` contains only `answer`. There is no client-supplied system prompt, API key, model name, or endpoint.

`ChatController` uses `@Valid @RequestBody`, consumes/produces JSON, and delegates immediately. It does not build prompts or catch provider-specific exceptions.

The length limit counts Java UTF-16 code units, not tokens. Some Unicode characters count as two units. Token budgets remain provider/model-specific.

### Step B: configure a ChatClient

`ChatConfiguration` receives Boot's automatically configured `ChatClient.Builder` and installs the external system prompt:

```java
return builder
        .defaultSystem(systemPrompt)
        .build();
```

Spring AI supplies the builder around the selected `ChatModel`. Keeping prompt construction here makes the service easier to test and prevents provider configuration leaking into controllers.

The prompt explicitly says there is no live marketplace access. It prohibits invented listings and treats quoted product descriptions as data rather than instructions. This is a behavioral guardrail, not an authorization mechanism.

### Step C: make one stateless call

The core of `ChatService` is:

```java
answer = chatClient.prompt()
        .user(message.strip())
        .call()
        .content();
```

`.user(...)` supplies a user-role message. We do not concatenate user text into the system prompt. `.call()` selects synchronous generation; this endpoint does not stream tokens. `.content()` extracts generated text.

Each invocation builds a new request containing the default system message and current user message. No static message list or memory advisor exists, so one caller's previous question is not carried into the next request.

Null/blank output raises `EmptyModelResponseException`. Provider runtime failures are wrapped in `ModelProviderException`. The original exception remains available for local debugging, but its potentially sensitive message is neither returned nor logged.

### Step D: translate failures centrally

`ApiExceptionHandler` maps validation/malformed JSON to 400, empty output to 502, and provider failure to 503. Unexpected errors become a generic 500. Error bodies contain a timestamp, HTTP status, stable code, safe message, correlation ID, and optional field violations—never rejected prompt values.

The correlation filter creates a UUID, places it in MDC and the `X-Request-ID` response header, and clears MDC in `finally`. Client-provided IDs are ignored to prevent log injection. The logging pattern includes the MDC request ID.

### Step E: externalize model configuration

`application.yml` selects Ollama and configures model name, temperature, output limit, HTTP timeouts, and total retry attempts. `pull-model-strategy: never` prevents implicit weight downloads.

`application-hosted.yml` selects OpenAI and resolves `${OPENAI_API_KEY}` without a fallback secret. Set `SPRING_PROFILES_ACTIVE=hosted` to activate it. A compatible provider must support the selected model and options; configuration compatibility does not imply every OpenAI-like server implements every feature.

No fallback to canned text is used. If the model fails, the API reports failure.

## 6. Run commands

Follow the [README quick start](../README.md#quick-start-local-ollama). The essential commands are:

```bash
java -version                    # should report Java 21
./mvnw --version
ollama pull qwen2.5:7b-instruct
ollama list
./mvnw clean verify
./mvnw spring-boot:run
```

In another terminal:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail -i http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Explain the difference between RAM and SSD storage."}'
```

Real-model connectivity check:

```bash
RUN_LIVE_AI_TESTS=true ./mvnw -Dtest=LiveModelSmokeTest test
```

The opt-in test is skipped by default. It does not require exact generated wording, because exact wording is not a stable contract. Semantic evaluation datasets belong in later phases.

## 7. Test strategy

| Test class | What it proves | External model needed? |
|---|---|---|
| `ChatServiceTest` | Nonempty output, blank input/output, error wrapping, system/user role separation, no accumulated history | No; Mockito `ChatModel` |
| `ChatApiIntegrationTest` | Actual MVC JSON/binding/validation path, stable errors, status codes, correlation headers, health, no prompt/secret logging | No; Mockito `ChatModel` |
| `HostedConfigurationTest` | Hosted adapter and flat Spring AI 2 properties bind into a usable context | No; fake test-only key, no hosted request |
| `LiveModelSmokeTest` | A real Ollama request succeeds through Spring AI and the service | Yes; explicit opt-in |

The ordinary test profile selects `spring.ai.model.chat=none`; test configurations provide a substitute model. This prevents CI from silently using a real hosted key in the environment. Tests are not evidence that a real model resists every prompt injection: the deterministic injection test verifies message-role separation only.

No marketplace tests exist yet because no marketplace integration exists. Provider timeout/partial-failure tests will be added with the marketplace layer.

## 8. Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| `java: command not found` | JDK missing | Install JDK 21 and set PATH/JAVA_HOME |
| Compiler rejects `--release 21` | Maven runs on older Java | Check `./mvnw --version`, not only `java -version` |
| Wrapper TLS/download failure | Proxy, blocked Maven Central, or missing CA configuration | Use a trusted network or your organization's Maven mirror; do not disable TLS verification |
| Boot/AI `NoSuchMethodError` or missing classes | Mixed dependency generations | Keep the pinned Boot parent and Spring AI BOM; inspect `./mvnw dependency:tree` |
| `Connection refused` on 11434 | Ollama not running or wrong URL | Start Ollama once; check `/api/tags` and `OLLAMA_BASE_URL` |
| Model not found | Weights not pulled or tag mismatch | Compare `ollama list` with `OLLAMA_MODEL`, then pull the exact tag |
| First call very slow | Cold model load / CPU inference | Warm the model, allow time, or select a smaller model |
| HTTP 503 | Provider unavailable, rejected key, quota, timeout, or protocol mismatch | Check model-server health and account limits; do not enable secret-bearing HTTP logs |
| Hosted startup complains about `OPENAI_API_KEY` | Hosted profile without a key | Set it securely in the process environment |
| HTTP 400 | Blank/oversized message or invalid JSON | Inspect `fieldErrors`; use `Content-Type: application/json` |
| HTTP 502 | Model returned no usable text | Check model compatibility/output limits; retry deliberately |
| Port 8080 already used | Another service | Change `SERVER_PORT` |
| Tests accidentally contact a provider | Test profile overridden | Run the normal Maven test command and keep `application-test.yml` |
| `Address already in use` for Ollama | Desktop/system service already running | Reuse it rather than launching another `ollama serve` |

Do not solve TLS or authentication problems by disabling certificate checks, bypassing access controls, or printing credentials.

## 9. Design decisions and trade-offs

- **Synchronous before streaming:** easier HTTP contracts, errors, and testing; token streaming can come later.
- **Ollama first:** no API bill and prompts stay on the configured machine; hardware and model management become your responsibility.
- **Hosted optional:** usually easier compute/quality scaling, but introduces cost, network dependence, and provider data handling.
- **One ChatClient, one active provider:** simple replacement boundary without a premature multi-model routing framework.
- **Stateless first:** avoids accidental cross-user context leaks; follow-up context will require explicit ownership and lifecycle rules.
- **No database yet:** no persistence requirement exists in this slice; adding PostgreSQL now would be unused infrastructure.
- **Checked-in OpenAPI first:** small contract without another documentation dependency; generated interactive docs can follow.
- **No fake success fallback:** deterministic test answers exist only in tests, never in production responses.

## 10. Completion gate

- [x] Compatible stable dependency baseline selected from upstream release information.
- [x] Minimal real Spring AI call implemented behind a validated REST endpoint.
- [x] Local and optional hosted configuration separated from business logic.
- [x] Safe validation/error handling, bounded timeouts/retries, health, correlation IDs, graceful shutdown.
- [x] Unit, HTTP integration, hosted configuration, and opt-in live tests written.
- [x] Architecture, OpenAPI, setup, security limitations, and Git instructions documented.
- [x] Build/test execution and live-model verification recorded in [verification.md](verification.md): 28 deterministic tests passed; real Ollama service and packaged HTTP calls passed.
- [x] Milestone commits pushed to the existing repository's fixed session branch; remote SHAs and CI verified.
- [ ] Separately named public repository created by the user and second remote connected (blocked by integration permissions, not by the application code).

Only move to Phase 2 after the execution gates pass. Phase 2 starts with structured criteria extraction and Java validation—not marketplace scraping or recommendations.
