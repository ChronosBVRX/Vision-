import { controllerGame as banterboltControllerGame } from "@open-party-lab/game-banterbolt/controller";
import { controllerGame as youGotItControllerGame } from "@open-party-lab/game-you-got-it/controller";
import { controllerGame as merkissionerControllerGame } from "@open-party-lab/game-merkissioner/controller";
import { controllerGame as pantomimeControllerGame } from "@open-party-lab/game-pantomime/controller";
import { controllerGame as zeichnenUndErratenControllerGame } from "@open-party-lab/game-zeichnen-und-erraten/controller";
import { controllerGame as schaetzoramaControllerGame } from "@open-party-lab/game-schaetzorama/controller";
import { controllerGame as imposterControllerGame } from "@open-party-lab/game-imposter/controller";
import { controllerGame as buzzwortControllerGame } from "@open-party-lab/game-buzzwort/controller";
import { controllerGame as schattenjagdControllerGame } from "@open-party-lab/game-schattenjagd/controller";
import { controllerGame as flatterfluffControllerGame } from "@open-party-lab/game-flatterfluff/controller";
import { controllerGame as magicArenaControllerGame } from "@open-party-lab/game-magic-arena/controller";
import { controllerGame as cardTableControllerGame } from "@open-party-lab/game-card-table/controller";
import { controllerGame as dungeonPartyControllerGame } from "@open-party-lab/game-dungeon-party/controller";
import type { ControllerGameRegistration } from "../registry.js";

export const externalControllerGameRegistrations: ControllerGameRegistration[] = [
  banterboltControllerGame as ControllerGameRegistration,
  youGotItControllerGame as ControllerGameRegistration,
  merkissionerControllerGame as ControllerGameRegistration,
  pantomimeControllerGame as ControllerGameRegistration,
  zeichnenUndErratenControllerGame as ControllerGameRegistration,
  schaetzoramaControllerGame as ControllerGameRegistration,
  imposterControllerGame as ControllerGameRegistration,
  buzzwortControllerGame as ControllerGameRegistration,
  schattenjagdControllerGame as ControllerGameRegistration,
  flatterfluffControllerGame as ControllerGameRegistration,
  magicArenaControllerGame as ControllerGameRegistration,
  cardTableControllerGame as ControllerGameRegistration,
  dungeonPartyControllerGame as ControllerGameRegistration
];
