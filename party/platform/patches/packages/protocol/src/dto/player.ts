export type ConnectionRole = "host" | "controller";
export type PlayerPresence = "online" | "reconnecting" | "offline";
export type PlayerSetupValue = string | string[];

/** How sharp a platform bot plays. `hard` is the default; `expert` is the top tier. */
export type BotDifficulty = "normal" | "hard" | "expert";

export interface PlayerSnapshot {
  id: string;
  name: string;
  avatar?: string | null;
  color: string;
  selectedCharacterId: string | null;
  selectedCharacterName: string | null;
  setupSelections: Record<string, PlayerSetupValue>;
  isReady: boolean;
  connected: boolean;
  presence: PlayerPresence;
  score: number;
  joinedAt: number;
  lastSeenAt: number;
  reconnectGraceEndsAt: number | null;
  /** True when this seat is driven by the server instead of a phone. */
  isBot?: boolean;
  botDifficulty?: BotDifficulty;
}
