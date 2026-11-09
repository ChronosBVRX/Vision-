import type Phaser from "phaser";
import type { HostGame } from "@open-party-lab/game-core";
import { hostGame as banterboltHostGame } from "@open-party-lab/game-banterbolt/host";
import { hostGame as youGotItHostGame } from "@open-party-lab/game-you-got-it/host";
import { hostGame as merkissionerHostGame } from "@open-party-lab/game-merkissioner/host";
import { hostGame as pantomimeHostGame } from "@open-party-lab/game-pantomime/host";
import { hostGame as zeichnenUndErratenHostGame } from "@open-party-lab/game-zeichnen-und-erraten/host";
import { hostGame as schaetzoramaHostGame } from "@open-party-lab/game-schaetzorama/host";
import { hostGame as imposterHostGame } from "@open-party-lab/game-imposter/host";
import { hostGame as buzzwortHostGame } from "@open-party-lab/game-buzzwort/host";
import { hostGame as schattenjagdHostGame } from "@open-party-lab/game-schattenjagd/host";
import { hostGame as flatterfluffHostGame } from "@open-party-lab/game-flatterfluff/host";
import { hostGame as magicArenaHostGame } from "@open-party-lab/game-magic-arena/host";
import { hostGame as cardTableHostGame } from "@open-party-lab/game-card-table/host";
import { hostGame as dungeonPartyHostGame } from "@open-party-lab/game-dungeon-party/host";

export const externalHostGames: HostGame[] = [
  banterboltHostGame as HostGame,
  youGotItHostGame as HostGame,
  merkissionerHostGame as HostGame,
  pantomimeHostGame as HostGame,
  zeichnenUndErratenHostGame as HostGame,
  schaetzoramaHostGame as HostGame,
  imposterHostGame as HostGame,
  buzzwortHostGame as HostGame,
  schattenjagdHostGame as HostGame,
  flatterfluffHostGame as HostGame,
  magicArenaHostGame as HostGame,
  cardTableHostGame as HostGame,
  dungeonPartyHostGame as HostGame
];

export const externalHostGameRegistry: Record<string, HostGame> = Object.fromEntries(
  externalHostGames.map((game) => [game.id, game])
);

export const externalHostScenes: Phaser.Types.Scenes.SceneType[] = externalHostGames.flatMap((game) =>
  game.scene ? [game.scene as Phaser.Types.Scenes.SceneType] : []
);
