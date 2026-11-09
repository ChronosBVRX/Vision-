import { pathToFileURL } from "node:url";
import { createMagicArenaDebugState, getInitiativeOrder } from "./magicArenaRules.js";

export function runMagicArenaDebugStart(playerCount = 2): void {
  const state = createMagicArenaDebugState(playerCount);

  console.log(
    JSON.stringify(
      {
        gameId: state.gameId,
        magicPhase: state.magicPhase,
        roundIndex: state.roundIndex,
        players: state.players.map((player) => ({
          playerId: player.playerId,
          name: player.name,
          figures: player.figures.map((figure) => ({
            figureId: figure.figureId,
            hp: figure.hp,
            status: figure.status,
            coord: figure.coord
          })),
          hand: player.hand.map((card) => card.cardId)
        })),
        initiativeOrder: getInitiativeOrder(state),
        deckCount: state.deck.length,
        discardCount: state.discardPile.length
      },
      null,
      2
    )
  );
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  const playerCount = Number.parseInt(process.argv[2] ?? "2", 10);
  runMagicArenaDebugStart(playerCount);
}
