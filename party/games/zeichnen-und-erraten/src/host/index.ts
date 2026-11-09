import Phaser from "phaser";
import { zeichnenUndErratenManifest } from "../manifest.js";
import { renderZeichnenUndErratenState } from "./ZeichnenUndErratenRenderer.js";
import { renderRoundScreens } from "./roundScreens.js";
import { bindPlatformTheme, tokens } from "./platformTheme.js";

type SupportedLanguage = "de" | "en";

interface HostAppStateLike {
  room?: {
    language?: SupportedLanguage;
  } | null;
  game?: {
    phase?: string;
    message?: string;
    state?: unknown;
  } | null;
}

interface HostClientLike {
  subscribe(listener: (state: HostAppStateLike) => void): () => void;
}

export class ZeichnenUndErratenHostScene extends Phaser.Scene {
  private unsubscribe?: () => void;

  constructor() {
    super(zeichnenUndErratenManifest.hostView);
  }

  create(): void {
    bindPlatformTheme(this.registry);
    const client = this.registry.get("hostClient") as HostClientLike;

    let latestState: HostAppStateLike | undefined;
    const render = (state: HostAppStateLike) => {
      latestState = state;
      // Intro and result screens belong to this game, not the platform.
      if (renderRoundScreens(this, state)) {
        return;
      }

      const gameState = state.game?.state as
        | {
            drawerName?: string;
            maskedWord?: string;
            finishAt?: number | null;
            canvasAspectRatio?: number;
            gameScores?: Array<{ playerId: string; playerName: string; total: number; delta: number }>;
            revealedWord?: string;
            winnerName?: string;
            strokes?: Array<{ id: string; color: string; points: Array<{ x: number; y: number }> }>;
            guesses?: Array<{ playerName: string; guess: string; correct: boolean }>;
          }
        | undefined;
      const en = state.room?.language === "en";

      this.children.removeAll(true);
      this.cameras.main.setBackgroundColor(tokens().color.background);

      renderZeichnenUndErratenState(
        this,
        gameState ?? {},
        state.game?.message ?? (en ? "The drawer paints while the others guess." : "El ilustrador pinta, los demás adivinan."),
        state.room?.language
      );
    };
    this.unsubscribe = client.subscribe(render);
    const timer = this.time.addEvent({
      delay: 1_000,
      loop: true,
      callback: () => {
        if (latestState?.game?.phase === "playing" && latestState.game.state) render(latestState);
      }
    });
    const onResize = () => {
      if (latestState) render(latestState);
    };
    this.scale.on(Phaser.Scale.Events.RESIZE, onResize);

    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.scale.off(Phaser.Scale.Events.RESIZE, onResize);
      timer.remove(false);
      this.unsubscribe?.();
      this.unsubscribe = undefined;
    });
  }
}

export const hostGame = {
  id: zeichnenUndErratenManifest.id,
  displayName: zeichnenUndErratenManifest.displayName,
  sceneKey: zeichnenUndErratenManifest.hostView,
  scene: ZeichnenUndErratenHostScene
} as const;
