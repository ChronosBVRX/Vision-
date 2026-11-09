import Phaser from "phaser";
import { schattenjagdManifest } from "../manifest.js";
import type { SchattenjagdPublicState } from "../protocol.js";
import type { SchattenjagdLanguage } from "../text.js";
import { SchattenjagdRenderer } from "./SchattenjagdRenderer.js";
import { preloadSchattenjagdAssets } from "./assets.js";
import { renderRoundScreens } from "./roundScreens.js";
import { bindPlatformTheme } from "./platformTheme.js";

interface HostClientLike {
  subscribe(callback: (state: HostAppStateLike) => void): () => void;
}

interface HostAppStateLike {
  game?: {
    state?: unknown;
    message?: string;
  } | null;
  room?: {
    language?: SchattenjagdLanguage;
    players?: Array<{ id: string; name: string; color: string }>;
  } | null;
}

export class SchattenjagdHostScene extends Phaser.Scene {
  private unsubscribe?: () => void;
  private view?: SchattenjagdRenderer;
  private lastState: SchattenjagdPublicState | null = null;
  private lastLanguage: SchattenjagdLanguage = "de";
  private resizeHandler?: () => void;

  constructor() {
    super(schattenjagdManifest.hostView);
  }

  preload(): void {
    preloadSchattenjagdAssets(this);
  }

  create(): void {
    bindPlatformTheme(this.registry);
    const client = this.registry.get("hostClient") as HostClientLike;
    this.view = new SchattenjagdRenderer(this);

    this.unsubscribe = client.subscribe((state) => {
      // Intro and result screens belong to this game, not the platform.
      if (renderRoundScreens(this, state)) {
        return;
      }

      const publicState = (state.game?.state ?? null) as SchattenjagdPublicState | null;
      this.lastLanguage = state.room?.language === "en" ? "en" : "de";
      this.lastState = publicState && publicState.map ? publicState : null;
      this.view?.render(this.lastState, this.lastLanguage);
    });

    this.resizeHandler = () => {
      this.view?.render(this.lastState, this.lastLanguage);
    };

    this.scale.on(Phaser.Scale.Events.RESIZE, this.resizeHandler);

    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.unsubscribe?.();
      this.unsubscribe = undefined;

      if (this.resizeHandler) {
        this.scale.off(Phaser.Scale.Events.RESIZE, this.resizeHandler);
        this.resizeHandler = undefined;
      }

      this.view?.destroy();
      this.view = undefined;
    });
  }

  update(): void {
    this.view?.updateCountdown(Date.now());
  }
}

export const hostGame = {
  id: schattenjagdManifest.id,
  displayName: schattenjagdManifest.displayName,
  sceneKey: schattenjagdManifest.hostView,
  scene: SchattenjagdHostScene
} as const;
