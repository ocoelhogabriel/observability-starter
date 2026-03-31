package br.com.ocoelhogabriel.observability.masking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Engine de masking que aplica uma cadeia de {@link MaskingPattern}s
 * sobre mensagens de log para ocultar dados sensíveis como CPF, email,
 * tokens Bearer, CNPJ, telefone, etc.
 * <p>
 * Inclui patterns padrão que podem ser desativados individualmente e
 * suporta patterns customizados via configuração.
 * <p>
 * <b>Performance:</b> Os patterns são compilados uma única vez no startup
 * e aplicados sequencialmente. Para logs de alta frequência, considere
 * verificar o nível de log antes de construir mensagens complexas.
 *
 * @since 1.0.0
 */
public class DataMasker {

  private final List<MaskingPattern> patterns;

  public DataMasker(List<MaskingPattern> customPatterns, boolean includeDefaults) {
    List<MaskingPattern> all = new ArrayList<>();
    if (includeDefaults) {
      all.addAll(defaultPatterns());
    }
    if (customPatterns != null) {
      all.addAll(customPatterns);
    }
    this.patterns = Collections.unmodifiableList(all);
  }

  /** Cria um DataMasker apenas com os patterns padrão. */
  public DataMasker() {
    this(null, true);
  }

  /**
   * Aplica todos os patterns de masking sobre a mensagem.
   * Se a mensagem for null, retorna null.
   */
  public String mask(String message) {
    if (message == null) return null;
    String result = message;
    for (MaskingPattern pattern : patterns) {
      result = pattern.apply(result);
    }
    return result;
  }

  /** Retorna a lista de patterns ativos (imutável). */
  public List<MaskingPattern> activePatterns() {
    return patterns;
  }

  // ── Patterns padrão ───────────────────────────────────────────────

  private static List<MaskingPattern> defaultPatterns() {
    return List.of(
        // CPF: 123.456.789-00 → ***.***.***-**
        new MaskingPattern("cpf",
            "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}",
            "***.***.***-**"),

        // CPF sem pontuação: 12345678900 (11 dígitos isolados)
        new MaskingPattern("cpf-raw",
            "(?<![\\d])\\d{11}(?![\\d])",
            "***********"),

        // CNPJ: 12.345.678/0001-90 → **.***.****/****-**
        new MaskingPattern("cnpj",
            "\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}",
            "**.***.****/****-**"),

        // Email: user@domain.com → ***@***
        new MaskingPattern("email",
            "[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}",
            "***@***"),

        // Bearer token: Bearer eyJhbGci... → Bearer [MASKED]
        new MaskingPattern("bearer-token",
            "Bearer\\s+[A-Za-z0-9._\\-]+",
            "Bearer [MASKED]"),

        // Telefone BR: (11) 99999-9999 ou +55 11 99999-9999
        new MaskingPattern("phone-br",
            "(?:\\+55\\s?)?\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}",
            "(XX) XXXXX-XXXX"),

        // Senha em JSON: "password":"abc123" → "password":"[MASKED]"
        new MaskingPattern("password-json",
            "(\"(?:password|senha|secret|token|apiKey|api_key)\"\\s*:\\s*\")([^\"]+)(\")",
            "$1[MASKED]$3"),

        // Cartão de crédito: 4 grupos de 4 dígitos
        new MaskingPattern("credit-card",
            "\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}",
            "****-****-****-****")
    );
  }
}
