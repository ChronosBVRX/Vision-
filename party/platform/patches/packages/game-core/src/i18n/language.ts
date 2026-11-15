export const supportedLanguages = ["es"] as const;

export type SupportedLanguage = (typeof supportedLanguages)[number];

/**
 * Vision+ Party es español-only. No hay alemán ni inglés en el producto.
 */
export const defaultLanguage: SupportedLanguage = "es";

export const languageLabels: Record<SupportedLanguage, string> = {
  es: "Español"
};

export function isSupportedLanguage(value: unknown): value is SupportedLanguage {
  return value === "es";
}

export function normalizeLanguage(
  _value: unknown,
  _fallback: SupportedLanguage = defaultLanguage
): SupportedLanguage {
  return "es";
}
PY