package br.com.ocoelhogabriel.observability.masking;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * Logback {@link CompositeConverter} que aplica masking de dados sensíveis
 * sobre a mensagem formatada do log antes de ser escrita.
 * <p>
 * Pode ser registrado no logback-spring.xml como conversion rule:
 * <pre>{@code
 * <conversionRule conversionWord="mask"
 *   converterClass="br.com.ocoelhogabriel.observability.masking.MaskingMessageConverter"/>
 *
 * <pattern>%mask(%msg) %n</pattern>
 * }</pre>
 * <p>
 * Alternativamente, o masking é aplicado diretamente pelo {@link br.com.ocoelhogabriel.observability.core.ObservableLogger}
 * antes de delegar ao SLF4J, o que é mais eficiente pois evita regex em logs que
 * já foram mascarados.
 *
 * @since 1.0.0
 */
public class MaskingMessageConverter extends CompositeConverter<ILoggingEvent> {

  private static volatile DataMasker sharedMasker;

  /**
   * Define o DataMasker compartilhado. Chamado pelo auto-configuration no startup.
   */
  public static void setSharedMasker(DataMasker masker) {
    sharedMasker = masker;
  }

  @Override
  protected String transform(ILoggingEvent event, String in) {
    DataMasker masker = sharedMasker;
    if (masker == null || in == null) {
      return in;
    }
    return masker.mask(in);
  }
}
