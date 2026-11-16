import { minionsTdText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
import { minionsTdSetupConfig } from "./protocol.js";
import {
  listMinionsTdMaps,
  minionsTdRoomSettingKeys,
  resolveMinionsTdMap
} from "./server/minionsTdConfig.js";

export const minionsTdManifest = {
  id: "minions-td",
  translations: minionsTdText,
  displayName: "MinionsTD",
  description: "Construye torres, envía esbirros y mantén tu carril vivo más que tus rivales.",
  minPlayers: 2,
  maxPlayers: 4,
  hostView: "MinionsTdHostScene",
  controllerView: "minions-td",
  controllerLayout: "tower_defense",
  supportsTeams: false,
  estimatedRoundDurationMs: 180_000,
  lobbySetup: {
    title: "Configuración de MinionsTD",
    description: "Elige el mapa, las vidas iniciales y el oro inicial antes de la ronda.",
    fields: [
      {
        kind: "select",
        id: "mapId",
        settingKey: minionsTdRoomSettingKeys.selectedMapId,
        actionKey: "mapId",
        label: "Mapa",
        defaultValue: resolveMinionsTdMap(null, 1).id,
        options: listMinionsTdMaps().map((map) => ({
          id: map.id,
          label: map.name
        }))
      },
      {
        kind: "number",
        id: "startingLives",
        settingKey: minionsTdRoomSettingKeys.startingLives,
        actionKey: "startingLives",
        label: "Vidas iniciales",
        min: minionsTdSetupConfig.startingLives.min,
        max: minionsTdSetupConfig.startingLives.max,
        step: minionsTdSetupConfig.startingLives.step,
        defaultValue: minionsTdSetupConfig.startingLives.defaultValue
      },
      {
        kind: "number",
        id: "startingGold",
        settingKey: minionsTdRoomSettingKeys.startingGold,
        actionKey: "startingGold",
        label: "Oro inicial",
        min: minionsTdSetupConfig.startingGold.min,
        max: minionsTdSetupConfig.startingGold.max,
        step: minionsTdSetupConfig.startingGold.step,
        defaultValue: minionsTdSetupConfig.startingGold.defaultValue
      }
    ]
  },
  phaseDurations: {
    roundIntroMs: 1_800,
    countdownMs: 2_200,
    resultMs: 4_500,
    scoreboardMs: 4_500
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#6e8b74", icon: "tower", eyebrow: "Tower Defense" },
  audio: { track: { profile: "strategy", bpm: 96, rootMidi: 43, masterGain: 0.16 } },
} as const satisfies GameManifest;

export const manifest = minionsTdManifest;
