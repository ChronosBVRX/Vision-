export const supportedLanguages = ["de", "en", "es"] as const;

export type SupportedLanguage = (typeof supportedLanguages)[number];

/**
 * Vision+ Party es español-first. La plataforma conserva los idiomas del
 * upstream para los tipos y los manifiestos de los juegos, pero toda sesión
 * se normaliza a español.
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
  return "es";
}
