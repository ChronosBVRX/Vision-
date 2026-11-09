import type {
  ZeichnenUndErratenLobbyState,
  ZeichnenUndErratenWordCategory
} from "../protocol.js";

type SupportedLanguage = "de" | "en";

export const zeichnenUndErratenRoomSettingKeys = {
  wordCategory: "zeichnenUndErratenWordCategory",
  roundSeconds: "zeichnenUndErratenRoundSeconds"
} as const;

const defaultWordCategory: ZeichnenUndErratenWordCategory = "standard";
export const defaultRoundSeconds = 90;

export function resolveZeichnenUndErratenRoundSeconds(settings: Readonly<Record<string, unknown>>): number | null {
  const value = settings[zeichnenUndErratenRoomSettingKeys.roundSeconds];
  if (typeof value !== "number" || !Number.isFinite(value)) return defaultRoundSeconds;
  if (value === 0) return null;
  return Math.min(180, Math.max(15, Math.round(value / 15) * 15));
}

export function isZeichnenUndErratenWordCategory(
  value: unknown
): value is ZeichnenUndErratenWordCategory {
  return value === "standard" || value === "adult" || value === "all";
}

export function resolveZeichnenUndErratenWordCategory(
  settings: Readonly<Record<string, unknown>>
): ZeichnenUndErratenWordCategory {
  const value = settings[zeichnenUndErratenRoomSettingKeys.wordCategory];
  return isZeichnenUndErratenWordCategory(value) ? value : defaultWordCategory;
}

export function getZeichnenUndErratenLobbyState(
  settings: Readonly<Record<string, unknown>>,
  language: SupportedLanguage
): ZeichnenUndErratenLobbyState {
  const en = language === "en";

  return {
    selectedCategory: resolveZeichnenUndErratenWordCategory(settings),
    categories: [
      {
        id: "standard",
        label: en ? "U18" : "U18",
        description: en ? "Friendly drawing words for every group." : "Familienfreundliche Zeichenbegriffe."
      },
      {
        id: "adult",
        label: en ? "18+" : "\u00dc18",
        description: en ? "Mature party words for adults." : "Erwachsene Party-Begriffe."
      },
      {
        id: "all",
        label: en ? "All" : "Alle",
        description: en ? "Use both family and mature word lists." : "U18- und \u00dc18-Begriffe zusammen."
      }
    ]
  };
}
