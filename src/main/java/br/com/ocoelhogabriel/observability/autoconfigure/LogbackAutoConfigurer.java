package br.com.ocoelhogabriel.observability.autoconfigure;

import br.com.ocoelhogabriel.observability.masking.MaskingMessageConverter;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Configura o Logback automaticamente caso o consumidor não tenha um
 * {@code logback-spring.xml} ou {@code logback.xml} próprio.
 * <p>
 * É acionado via {@link ApplicationEnvironmentPreparedEvent} — antes mesmo do
 * contexto Spring ser criado — garantindo que todos os logs do startup já
 * usem o pattern correto.
 * <p>
 * Se um arquivo de configuração customizado for detectado (via
 * {@code logging.config} ou presença de {@code logback-spring.xml} no classpath),
 * este configurador não faz nada, respeitando completamente a configuração do usuário.
 * <p>
 * O resultado é integração zero-config idêntica ao comportamento nativo do Spring Boot.
 *
 * @since 1.0.3
 */
public class LogbackAutoConfigurer
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  private static final String LOGBACK_SPRING_XML = "logback-spring.xml";
  private static final String LOGBACK_XML        = "logback.xml";
  private static final String LOGGING_CONFIG_KEY = "logging.config";

  /** Nome do appender registrado programaticamente. */
  static final String APPENDER_NAME = "OCOELHOGABRIEL_AUTO_CONSOLE";

  @Override
  public int getOrder() {
    // Roda logo após o LoggingApplicationListener do Spring Boot (Ordered.LOWEST_PRECEDENCE + 20)
    return Ordered.LOWEST_PRECEDENCE - 100;
  }

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
      return; // Não está usando Logback — ignora
    }

    Environment env = event.getEnvironment();

    // Respeita logging.config explícito
    if (env.containsProperty(LOGGING_CONFIG_KEY)) {
      return;
    }

    // Respeita logback-spring.xml ou logback.xml no classpath do consumidor
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl.getResource(LOGBACK_SPRING_XML) != null || cl.getResource(LOGBACK_XML) != null) {
      return;
    }

    // Evita registrar duas vezes (ex.: refresh do contexto)
    if (context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        .getAppender(APPENDER_NAME) != null) {
      return;
    }

    // ── Registra o MaskingMessageConverter ───────────────────────────
    context.putProperty("conversionRule.mask",
        MaskingMessageConverter.class.getName());

    // ── Detecta perfil ativo ──────────────────────────────────────────
    String[] activeProfiles = env.getActiveProfiles();
    boolean isProd = activeProfiles.length > 0 &&
        java.util.Arrays.stream(activeProfiles)
            .anyMatch(p -> p.equalsIgnoreCase("prod"));

    String appName = env.getProperty("spring.application.name", "app");

    // ── Monta o pattern ───────────────────────────────────────────────
    String pattern = isProd
        ? buildProdPattern(appName)
        : buildDevPattern(appName);

    // ── Cria o encoder ────────────────────────────────────────────────
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(pattern);
    encoder.setCharset(java.nio.charset.StandardCharsets.UTF_8);
    encoder.start();

    // ── Cria o appender ───────────────────────────────────────────────
    ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
    appender.setName(APPENDER_NAME);
    appender.setContext(context);
    appender.setEncoder(encoder);
    appender.start();

    // ── Adiciona ao root logger ───────────────────────────────────────
    ch.qos.logback.classic.Logger root =
        context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
    root.setLevel(Level.INFO);
    root.addAppender(appender);
  }

  // ── Patterns ──────────────────────────────────────────────────────

  private String buildDevPattern(String appName) {
    // Igual ao OCOELHOGABRIEL_DEV_PATTERN do XML, mas com %msg (não %m)
    return "%clr(%d{HH:mm:ss.SSS}){faint}"
        + " %clr(%5p)"
        + " %clr(${PID:- }){magenta}"
        + " %clr(---){faint}"
        + " %clr([%15.15t]){faint}"
        + " %clr([" + appName + ",%X{traceId:-},%X{spanId:-}]){yellow}"
        + " %clr({%X{scope:-}}){blue}"
        + " %clr(%-40.40logger{39}){cyan}"
        + " %clr(:){faint}"
        + " %msg%n%wEx";
  }

  private String buildProdPattern(String appName) {
    return "%d{ISO8601}"
        + " %5p"
        + " [" + appName + ",%X{traceId:-},%X{spanId:-}]"
        + " {%X{scope:-}}"
        + " %-40.40logger{39}"
        + " : %msg%n%wEx";
  }
}
