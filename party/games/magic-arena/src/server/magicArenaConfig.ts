export const MAGIC_ARENA_GAME_ID = "magic-arena";

export const BOARD_RADIUS = 5;
export const FIGURES_PER_PLAYER = 6;
export const FIGURE_MAX_HP = 4;
export const START_HAND_CARDS = 6;
export const STARTING_HAND_SIZE = START_HAND_CARDS;
export const MAX_HAND_CARDS = 10;
export const PLANNED_ACTIONS_PER_ROUND = 4;
export const MAX_OCCUPANTS_PER_FIELD = 1;
export const WATER_RESCUE_ROUNDS = 1;

export const SPECIAL_CRYSTAL_COUNT_RADIUS_5 = 6;
export const SPECIAL_EXPLOSIVE_COUNT_RADIUS_5 = 5;
export const SPECIAL_CRACKED_COUNT_RADIUS_5 = 7;
export const CRACKED_BREAK_CHANCE = 0.2;
export const CRYSTAL_SPAWN_START_ROUND = 2;
export const CRYSTAL_SPAWN_CHANCE = 0.66;

export const WEAK_FIELD_HP_MIN = 1;
export const WEAK_FIELD_HP_MAX = 3;
export const NORMAL_FIELD_HP_MIN = 4;
export const NORMAL_FIELD_HP_MAX = 5;
export const STRONG_FIELD_HP_MIN = 6;
export const STRONG_FIELD_HP_MAX = 7;

export const ROUND_EVENT_START_ROUND = 3;
export const ROUND_EVENT_CHANCE = 0.33;
export const THIN_FLOOR_EXTRA_DAMAGE_CHANCE = 0.1;
export const STORM_CLOUD_ACTIVE_ROUNDS = 2;

export const ARENA_DECAY_FIRST_FIGURE_THRESHOLD = 6;
export const ARENA_DECAY_FIRST_ROUND = 15;
export const ARENA_DECAY_FIRST_DAMAGE = 1;
export const ARENA_DECAY_SECOND_FIGURE_THRESHOLD = 3;
export const ARENA_DECAY_SECOND_ROUND = 30;
export const ARENA_DECAY_SECOND_DAMAGE = 2;

export const DIG_SMALL_CHEST_CHANCE = 0.35;
export const DIG_BIG_CHEST_CHANCE = 0.25;
export const DIG_TREASURE_CHANCE = 0.1;
export const DIG_NOTHING_CHANCE = 0.1;
export const DIG_BURIED_BOMB_CHANCE = 0.1;
export const DIG_UNSTABLE_GROUND_CHANCE = 0.1;

export const DIG_BASE_FIELD_DAMAGE = 1;
export const DIG_BURIED_BOMB_DAMAGE = 1;
export const DIG_BOMB_DAMAGE = DIG_BURIED_BOMB_DAMAGE;
export const DIG_UNSTABLE_EXTRA_FIELD_DAMAGE = 2;

export const PUSH_CHAIN_LIMIT = 10;
export const EXECUTION_STEP_MS = 2100;
export const MAX_ACTION_LOG_ENTRIES = 24;

export const MAGIC_ARENA_PLANNING_MODES = ["planned", "sequential"] as const;
export const MAGIC_ARENA_DEFAULT_PLANNING_MODE = "planned";

/** Sequential mode: every player takes exactly one turn per round. */
export const SEQUENTIAL_ACTIONS_PER_ROUND = 1;

export const magicArenaRoomSettingKeys = {
  planningMode: "magicArenaPlanningMode"
} as const;

export const magicArenaColors = [
  "#38bdf8",
  "#fb7185",
  "#34d399",
  "#facc15"
] as const;
