export const supportedLanguages = ["de", "en", "es"] as const;

export type SupportedLanguage = (typeof supportedLanguages)[number];

/**
 * Vision+ Party is a Spanish-first fork. The platform keeps German and English
 * for the upstream games, but every new session starts in Spanish.
 */
export const defaultLanguage: SupportedLanguage = "es";

export const languageLabels: Record<SupportedLanguage, string> = {
  de: "Deutsch",
  en: "English",
  es: "Español"
};

export function isSupportedLanguage(value: unknown): value is SupportedLanguage {
  return typeof value === "string" && supportedLanguages.includes(value as SupportedLanguage);
}

export function normalizeLanguage(
  _value: unknown,
  _fallback: SupportedLanguage = defaultLanguage
): SupportedLanguage {
  // Vision+ Party is Spanish-only: every room and screen resolves to es.
  return "es";
}
