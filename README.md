# 🔭 Observability Spring Boot Starter

[![CI](https://github.com/ocoelhogabriel/observability-starter/actions/workflows/publish.yml/badge.svg)](https://github.com/ocoelhogabriel/observability-starter/actions)
[![JitPack](https://jitpack.io/v/ocoelhogabriel/observability-starter.svg)](https://jitpack.io/#ocoelhogabriel/observability-starter)
[![Java](https://img.shields.io/badge/Java-21+-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Biblioteca de observabilidade production-ready para microserviços Spring Boot.**

Auto-detecção de método chamador via `StackWalker`, structured logging com fluent API, propagação automática de contexto de correlação, masking de dados sensíveis, métricas Micrometer, audit trail e muito mais — tudo com **zero configuração manual**.

---

## ✨ Diferenciais

| Feature | Descrição |
|---------|-----------|
| 🧠 **Auto-detect de função** | Detecta automaticamente qual método chamou o log via `StackWalker` (Java 21) — sem boilerplate |
| 🔗 **Correlation Context** | Contexto imutável auto-propagado entre HTTP → `@Async` → Kafka → Scheduler |
| 🎯 **Fluent API** | `log.operation("X").tenant(t).entity("User", id).info(...)` |
| 🔒 **Data Masking** | 8 patterns padrão (CPF, CNPJ, email, token, telefone, cartão, senha) + customizáveis |
| 📊 **Métricas automáticas** | Cada log ERROR/WARN vira counter no Micrometer/Prometheus |
| ⏱️ **@Observed** | Annotation que gera logs de entrada/saída + Timer Micrometer automaticamente |
| 📋 **Audit Trail** | Logger dedicado com schema fixo para compliance |
| 🎲 **Smart Sampling** | Sampling probabilístico de DEBUG/TRACE em produção |
| 💚 **Health Indicator** | Pipeline de logging monitorado em `/actuator/health` |
| ⚡ **Zero-config** | Spring Boot Auto-configuration — adicione a dependência e funciona |

---

## 🚀 Quick Start

### 1. Adicionar o repositório JitPack

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

### 2. Adicionar a dependência

```xml
<dependency>
  <groupId>com.github.ocoelhogabriel</groupId>
  <artifactId>observability-starter</artifactId>
  <version>v1.0.0</version>
</dependency>
```

> **Sem autenticação.** JitPack é público — qualquer pessoa pode usar a dependência.

### 3. Pronto! Use no código

```java
import br.com.ocoelhogabriel.observability.core.ObservableLogger;
import br.com.ocoelhogabriel.observability.core.ObservableLoggerFactory;

@Service
public class UserService {

    private static final ObservableLogger log = ObservableLoggerFactory.getLogger(UserService.class);

    public User createUser(String name) {
        log.info("Criando usuário {}", name);
        // Output: Fn[createUser] → Criando usuário Gabriel
        //         ↑ auto-detectado!

        User user = repository.save(/* ... */);

        log.info("Usuário criado com ID {}", user.getId());
        // Output: Fn[createUser] → Usuário criado com ID 42
        return user;
    }
}
```

---

## 📖 Guia de Uso

### Auto-detecção de Método (StackWalker)

O logger **detecta automaticamente** qual método chamou o log. Não precisa informar o nome da função:

```java
public void processPayment(PaymentRequest req) {
    log.info("Processando pagamento de R$ {}", req.amount());
    // Output: Fn[processPayment] → Processando pagamento de R$ 150.00

    log.warn("Tentativa {} de {}", attempt, maxRetries);
    // Output: Fn[processPayment] → Tentativa 2 de 3

    log.error("Falha no gateway", exception);
    // Output: Fn[processPayment] → Falha no gateway
    //         java.lang.RuntimeException: Connection timeout...
}
```

Se quiser sobrescrever o nome, basta passar explicitamente:

```java
log.info("GATEWAY_CALL", "Chamando gateway {}", gatewayUrl);
// Output: Fn[GATEWAY_CALL] → Chamando gateway https://api.pag.com/v2
```

---

### Fluent API

Para logs ricos em contexto, use a API fluente:

```java
log.operation("CREATE_USER")
   .tenant(tenantId)
   .user(currentUserId)
   .entity("User", newUser.getId().toString())
   .durationMs(elapsed)
   .outcome("SUCCESS")
   .field("source", "API")
   .info("Usuário criado com sucesso");
```

No JSON de produção, cada campo aparece como atributo de primeiro nível:

```json
{
  "timestamp": "2026-03-31T17:30:00.000Z",
  "level": "INFO",
  "message": "Usuário criado com sucesso",
  "operation": "CREATE_USER",
  "tenantId": "acme-corp",
  "userId": "user-123",
  "entityType": "User",
  "entityId": "user-456",
  "durationMs": "142",
  "outcome": "SUCCESS",
  "source": "API",
  "traceId": "a1b2c3d4e5f6g7h8",
  "spanId": "1234abcd5678efgh"
}
```

---

### @Observed — Instrumentação Automática

Anote métodos com `@Observed` para gerar logs + métricas automaticamente:

```java
import br.com.ocoelhogabriel.observability.aop.Observed;

@Service
public class PaymentService {

    @Observed(operation = "processPayment", logArgs = true)
    public PaymentResult process(PaymentCommand cmd) {
        // ▶ [processPayment] args=[PaymentCommand{amount=150.00}]
        // ... lógica ...
        // ◀ [processPayment] duration=342ms
        return result;
    }

    @Observed(logResult = true, level = Observed.LogLevel.INFO)
    public List<Payment> findByTenant(String tenantId) {
        // ▶ [PaymentService.findByTenant]
        // ◀ [PaymentService.findByTenant] duration=28ms result=[Payment{...}]
        return payments;
    }
}
```

**Métricas Micrometer geradas automaticamente:**
- `ocoelhogabriel.observed.duration{class=PaymentService, method=process, operation=processPayment, outcome=SUCCESS}`

---

### Audit Trail

Logger dedicado para eventos de auditoria com schema fixo:

```java
import br.com.ocoelhogabriel.observability.audit.AuditLogger;
import br.com.ocoelhogabriel.observability.audit.AuditEvent;

@Service
public class AuthService {

    private final AuditLogger auditLogger;

    public void login(String userId, String ip) {
        auditLogger.log(AuditEvent.builder()
            .action("USER_LOGIN")
            .actor(userId)
            .target("Session", sessionId)
            .tenant(tenantId)
            .outcome(AuditEvent.Outcome.SUCCESS)
            .detail("IP: " + ip)
            .meta("userAgent", userAgent)
            .build());
    }
}
```

---

### Correlation Context

O contexto de correlação é **automaticamente** criado para cada HTTP request e propagado para threads `@Async`:

```java
// Acessar o contexto atual em qualquer lugar:
CorrelationContext ctx = CorrelationContextHolder.get();
String traceId  = ctx.traceId();   // auto-gerado ou do Brave
String tenantId = ctx.tenantId();  // do header X-Tenant-Id

// Enriquecer depois da autenticação:
CorrelationContextHolder.enrich(builder ->
    builder.userId(authenticatedUser.getId())
           .tenantId(authenticatedUser.getTenantId()));
```

**Headers HTTP lidos automaticamente:**

| Header | Campo no contexto |
|--------|-------------------|
| `X-Trace-Id` | `traceId` |
| `X-Request-Id` | `requestId` |
| `X-Tenant-Id` | `tenantId` |
| `X-User-Id` | `userId` |
| `X-Session-Id` | `sessionId` |

---

## ⚙️ Configuração

Todas as features são configuráveis via `application.yaml`:

```yaml
ocoelhogabriel:
  observability:
    enabled: true                              # Master switch
    service-name: ${spring.application.name}   # Auto-detectado

    # Log automático de HTTP request/response
    request-logging:
      enabled: true
      include-headers: [Content-Type, Accept, Authorization]
      log-body: false

    # Masking de dados sensíveis
    masking:
      enabled: true
      include-defaults: true    # CPF, CNPJ, email, token, telefone, cartão, senha
      patterns:                 # Patterns customizados adicionais
        - name: internal-id
          regex: "ID-\\d{8}"
          replacement: "ID-********"

    # Sampling probabilístico (produção)
    sampling:
      enabled: false
      debug-rate: 0.1            # 10% dos DEBUG
      trace-rate: 0.01           # 1% dos TRACE

    # Audit trail
    audit:
      enabled: true

    # Métricas Micrometer
    metrics:
      enabled: true

    # Health indicator
    health:
      enabled: true
```

---

## 🔒 Data Masking

Dados sensíveis são **automaticamente mascarados** nos logs:

| Tipo | Input | Output |
|------|-------|--------|
| CPF | `123.456.789-00` | `***.***.***-**` |
| CNPJ | `12.345.678/0001-90` | `**.***.****/****-**` |
| Email | `user@domain.com` | `***@***` |
| Bearer Token | `Bearer eyJhbGci...` | `Bearer [MASKED]` |
| Telefone BR | `(11) 99999-9999` | `(XX) XXXXX-XXXX` |
| Senha JSON | `"password":"abc123"` | `"password":"[MASKED]"` |
| Cartão | `4111 1111 1111 1111` | `****-****-****-****` |

---

## 📊 Métricas Prometheus

Métricas expostas automaticamente em `/actuator/prometheus`:

| Métrica | Tipo | Descrição |
|---------|------|-----------|
| `ocoelhogabriel_log_events` | Counter | Eventos por nível e logger |
| `ocoelhogabriel_log_total` | Counter | Todos os ERROR/WARN (via appender) |
| `ocoelhogabriel_observed_duration` | Timer | Duração de métodos `@Observed` |

---

## 📁 Logback Integration

Inclua os defaults da lib no seu `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

  <!-- Defaults da lib: patterns, masking converter, appender console -->
  <include resource="logback-defaults-ocoelhogabriel.xml"/>

  <!-- DEV — console colorido com traceId/spanId/scope -->
  <springProfile name="!prod">
    <root level="INFO">
      <appender-ref ref="OCOELHOGABRIEL_CONSOLE"/>
    </root>
  </springProfile>

  <!-- PROD — JSON estruturado para Loki/ELK/CloudWatch -->
  <springProfile name="prod">
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"app":"${appName}","env":"${activeProfile}"}</customFields>
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>tenantId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>scope</includeMdcKeyName>
        <includeMdcKeyName>operation</includeMdcKeyName>
        <includeMdcKeyName>durationMs</includeMdcKeyName>
        <includeMdcKeyName>outcome</includeMdcKeyName>
        <charset>UTF-8</charset>
      </encoder>
    </appender>

    <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
      <appender-ref ref="JSON_STDOUT"/>
      <queueSize>2048</queueSize>
      <discardingThreshold>0</discardingThreshold>
    </appender>

    <root level="INFO">
      <appender-ref ref="ASYNC_JSON"/>
    </root>
  </springProfile>
</configuration>
```

---

## 🏗️ Arquitetura

```
br.com.ocoelhogabriel.observability
├── core/
│   ├── ObservableLogger          # Logger principal (StackWalker + Fluent API)
│   ├── ObservableLoggerFactory   # Factory com cache
│   └── LogEvent                  # Builder fluente
├── context/
│   ├── CorrelationContext        # Contexto imutável (traceId, tenantId, userId...)
│   ├── CorrelationContextHolder  # ThreadLocal + MDC sync
│   └── ContextPropagator        # TaskDecorator para @Async
├── filter/
│   ├── CorrelationFilter         # Monta contexto de headers HTTP
│   └── RequestLoggingFilter      # Auto-log de request/response
├── aop/
│   ├── @Observed                 # Annotation
│   └── ObservedAspect            # Aspect com Timer Micrometer
├── masking/
│   ├── DataMasker                # Engine com 8 patterns padrão
│   ├── MaskingPattern            # Record (name, regex, replacement)
│   └── MaskingMessageConverter   # Logback converter
├── sampling/
│   └── LogSampler                # TurboFilter para DEBUG/TRACE
├── metrics/
│   └── LogMetricsAppender        # Appender → Micrometer Counter
├── audit/
│   ├── AuditEvent                # Evento imutável de auditoria
│   └── AuditLogger               # Logger dedicado (audit.*)
├── health/
│   └── LoggingHealthIndicator    # /actuator/health
└── autoconfigure/
    ├── ObservabilityAutoConfiguration  # @AutoConfiguration
    └── ObservabilityProperties         # @ConfigurationProperties
```

---

## 📋 Requisitos

- **Java** 21+
- **Spring Boot** 4.x
- **Logback** (vem com Spring Boot)
- **Micrometer** (vem com `spring-boot-starter-actuator`)

### Dependências opcionais (para features completas)

```xml
<!-- Obrigatório para @Observed -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>

<!-- Obrigatório para métricas Prometheus -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Obrigatório para JSON logs em produção -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>8.0</version>
</dependency>
```

---

## 🔄 CI/CD

O projeto usa **GitHub Actions** para build e testes automáticos.

A publicação é feita automaticamente via **[JitPack](https://jitpack.io/#ocoelhogabriel/observability-starter)** — basta criar uma tag/release no GitHub e o JitPack compila e disponibiliza publicamente.

### Publicar uma nova versão

```bash
# 1. Commit suas mudanças
git add .
git commit -m "feat: nova feature"
git push origin main

# 2. Crie a tag
git tag v1.0.0
git push --tags
```

A partir desse momento, qualquer pessoa pode usar `v1.0.0` como versão na dependência.

---

## 🛠️ Desenvolvimento Local

```bash
# Clonar
git clone git@github.com:ocoelhogabriel/observability-starter.git
cd observability-starter

# Compilar
mvn clean compile

# Instalar localmente
mvn clean install -DskipTests

# Rodar testes
mvn test
```

---

## 📄 Licença

MIT © [ocoelhogabriel](https://github.com/ocoelhogabriel)
