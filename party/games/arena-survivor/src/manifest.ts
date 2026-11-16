import { arenaSurvivorText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
import { arenaSurvivorRoomSettingKeys } from "./server/arenaSurvivorConfig.js";
import { arenaSurvivorCharacterDefinitions } from "./server/definitions/characterDefinitions.js";
import { arenaSurvivorSetupConfig } from "./protocol.js";
import {
  arenaSurvivorDefaultVisualTheme,
  arenaSurvivorVisualThemeOptions,
  resolveArenaSurvivorCharacterPortraitPath
} from "./visualThemes.js";

export const arenaSurvivorManifest = {
  id: "arena-survivor",
  translations: arenaSurvivorText,
  displayName: "Arena Survivor",
  description: "Ueberlebe in der Arena gegen immer neue Gegner.",
  minPlayers: 1,
  maxPlayers: 4,
  hostView: "ArenaSurvivorHostScene",
  controllerView: "arena-survivor",
  controllerLayout: "virtual_joystick",
  supportsTeams: false,
  estimatedRoundDurationMs: 45_000,
  roundCompletionMode: "wait_for_ready",
  lobbySetup: {
    title: "Run Setup",
    description: "Lege die Schwierigkeit fest und gib den Run frei.",
    fields: [
      {
        kind: "select",
        id: "mode",
        settingKey: "arenaSurvivorMode",
        actionKey: "mode",
        label: "Modus / Mode",
        description: "Wave: Wellen und Shops. Survival: 20 Minuten in der grossen Frostfire-Welt.",
        options: [{ id: "wave", label: "Wave" }, { id: "survival", label: "Survival · Frostfire Saga" }],
        defaultValue: "wave"
      },
      {
        kind: "select",
        id: "visualTheme",
        settingKey: arenaSurvivorRoomSettingKeys.visualTheme,
        actionKey: "visualTheme",
        label: "Art Design",
        description: "Waehle das komplette visuelle Set fuer Map, Figuren, Gegner und Ausruestung.",
        options: arenaSurvivorVisualThemeOptions,
        defaultValue: arenaSurvivorSetupConfig.visualTheme.defaultValue
      },
      {
        kind: "number",
        id: "difficulty",
        settingKey: arenaSurvivorRoomSettingKeys.difficultyTier,
        actionKey: "difficulty",
        label: "Schwierigkeit",
        description: "Gefahr 1 bis 5.",
        min: arenaSurvivorSetupConfig.difficulty.min,
        max: arenaSurvivorSetupConfig.difficulty.max,
        step: arenaSurvivorSetupConfig.difficulty.step,
        defaultValue: arenaSurvivorSetupConfig.difficulty.defaultValue
      }
    ],
    confirmation: {
      settingKey: arenaSurvivorRoomSettingKeys.setupConfirmed,
      actionType: "confirm-lobby",
      label: "Run freigeben",
      description: "Startet automatisch, sobald alle Spieler bereit sind."
    }
  },
  playerSetup: {
    kind: "choice",
    title: "Charakterwahl",
    description: "Waehle deinen Charakter fuer den Run.",
    required: true,
    options: arenaSurvivorCharacterDefinitions.map((character) => ({
      id: character.id,
      name: character.name,
      title: character.title,
      archetype: character.archetype,
      description: character.description,
      portraitPath: resolveArenaSurvivorCharacterPortraitPath(character.id, arenaSurvivorDefaultVisualTheme),
      portraitPathBySetting: {
        settingKey: arenaSurvivorRoomSettingKeys.visualTheme,
        values: Object.fromEntries(
          arenaSurvivorVisualThemeOptions.map((theme) => [
            theme.id,
            resolveArenaSurvivorCharacterPortraitPath(character.id, theme.id)
          ])
        )
      },
      visual: {
        primaryColor: character.visual.primaryColor,
        secondaryColor: character.visual.secondaryColor,
        accentColor: character.visual.accentColor
      }
    }))
  },
  phaseDurations: {
    roundIntroMs: 1_500,
    countdownMs: 2_000,
    resultMs: 4_000,
    scoreboardMs: 4_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#b1503a", icon: "swords", eyebrow: "Arena" },
  audio: {
    track: { profile: "battle", bpm: 108, rootMidi: 45, masterGain: 0.18 },
    trackBySetting: {
      settingKey: "arenaSurvivorVisualTheme",
      values: {
        "frostfire-saga": { profile: "frostfire", bpm: 92, rootMidi: 50, masterGain: 0.15 },
        "marshmallow-mayhem": { profile: "sugarCountry", bpm: 114, rootMidi: 52, masterGain: 0.14 }
      }
    }
  },
  hostChrome: { joinOverlayWhenFinished: false },
  broadcast: {
    // The server ticks every 16 ms. With the platform default of 33 ms every
    // second tick fell just short of the gap, so the host got states at an
    // uneven 32/48 ms rhythm (~21-31 Hz). 24 ms lets exactly every second tick
    // through: a steady ~31 Hz and 16 ms less input-to-screen delay on average.
    hostStateIntervalMs: 24,
    // Phones only show their own HP/XP/material while a wave runs; 10 Hz is
    // plenty and keeps the Wi-Fi free for the stick input going the other way.
    controllerStateIntervalMs: 100,
    // A stick input only sets a direction; the player moves in the next tick,
    // which broadcasts anyway. No extra full-state build per input.
    deferInputBroadcastToTick: true
  },
} as const satisfies GameManifest;
