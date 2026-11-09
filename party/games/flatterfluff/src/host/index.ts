import Phaser from "phaser";
import { flatterfluffManifest } from "../manifest.js";
import type {
  FlatterfluffGameEvent,
  FlatterfluffState,
  FlatterfluffTargetKind
} from "../protocol.js";
import { FlatterfluffAudioRig } from "./FlatterfluffAudio.js";
import { renderRoundScreens } from "./roundScreens.js";

interface HostClientLike {
  subscribe(callback: (state: HostAppStateLike) => void): () => void;
}

interface HostAppStateLike {
  game?: {
    state?: unknown;
    phase?: string;
  } | null;
  room?: {
    language?: "de" | "en";
  } | null;
}

const assetRoot = "/flatterfluff";
const targetTextureKeys: Record<FlatterfluffTargetKind, string> = {
  scout: "flatterfluff-scout",
  flier: "flatterfluff-flier",
  captain: "flatterfluff-captain",
  jumbo: "flatterfluff-captain",
  golden: "flatterfluff-sugar-puff",
  crate: "flatterfluff-ammo-crate"
};

function colorNumber(color: string): number {
  return Number.parseInt(color.replace("#", ""), 16);
}

function formatTime(remainingMs: number | null): string {
  if (remainingMs === null) {
    return "∞";
  }
  const totalSeconds = Math.max(0, Math.ceil(remainingMs / 1_000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

export class FlatterfluffHostScene extends Phaser.Scene {
  private unsubscribe?: () => void;
  private background?: Phaser.GameObjects.Image;
  private overlay?: Phaser.GameObjects.Graphics;
  private titleText?: Phaser.GameObjects.Text;
  private timerText?: Phaser.GameObjects.Text;
  private timerLabel?: Phaser.GameObjects.Text;
  private modeText?: Phaser.GameObjects.Text;
  private scoreboardLabel?: Phaser.GameObjects.Text;
  private scoreboardTexts: Phaser.GameObjects.Text[] = [];
  private ammoTexts: Phaser.GameObjects.Text[] = [];
  private aimLabels = new Map<string, Phaser.GameObjects.Text>();
  private targetSprites = new Map<string, Phaser.GameObjects.Image>();
  private visualEventIds = new Set<number>();
  private state: FlatterfluffState | null = null;
  private language: "de" | "en" = "de";
  private readonly audio = new FlatterfluffAudioRig();

  constructor() {
    super(flatterfluffManifest.hostView);
  }

  preload(): void {
    // The host shell disappears as soon as the round exists, so preload must
    // paint something immediately instead of leaving the TV black while the
    // sugar-prairie art (the heaviest host asset in the catalog) downloads.
    const width = this.scale.width;
    const height = this.scale.height;
    const loadingBg = this.add.graphics().setDepth(20_000);
    loadingBg.fillStyle(0xf7f1e7, 1).fillRect(0, 0, width, height);
    const loadingTitle = this.add.text(width / 2, height / 2 - 34, "DULCE PUNTERÍA", {
      fontFamily: 'Georgia, "Times New Roman", serif',
      fontSize: "44px",
      color: "#4a1f0b",
      fontStyle: "bold",
      letterSpacing: 2
    }).setOrigin(0.5).setDepth(20_001);
    const loadingSub = this.add.text(width / 2, height / 2 + 22, "Preparando la cacería…", {
      fontFamily: "Inter, sans-serif",
      fontSize: "18px",
      color: "#8b5e34"
    }).setOrigin(0.5).setDepth(20_001);
    const loadingBarBg = this.add.graphics().setDepth(20_001);
    const loadingBar = this.add.graphics().setDepth(20_002);
    const barWidth = Math.min(420, width * 0.42);
    const barX = width / 2 - barWidth / 2;
    const barY = height / 2 + 62;
    loadingBarBg.fillStyle(0x4a1f0b, 0.16).fillRoundedRect(barX, barY, barWidth, 8, 4);
    this.load.on("progress", (progress: number) => {
      loadingBar.clear().fillStyle(0xc8873a, 1).fillRoundedRect(barX, barY, Math.max(8, barWidth * progress), 8, 4);
    });
    this.load.once("complete", () => {
      loadingBg.destroy();
      loadingTitle.destroy();
      loadingSub.destroy();
      loadingBarBg.destroy();
      loadingBar.destroy();
    });
    this.load.image("flatterfluff-background", `${assetRoot}/sugar-prairie.png`);
    this.load.image("flatterfluff-scout", `${assetRoot}/scout.png`);
    this.load.image("flatterfluff-flier", `${assetRoot}/flier.png`);
    this.load.image("flatterfluff-captain", `${assetRoot}/captain.png`);
    this.load.image("flatterfluff-ammo-crate", `${assetRoot}/ammo-crate.png`);
    this.load.image("flatterfluff-cartridges", `${assetRoot}/cartridges.png`);
    this.load.image("flatterfluff-sugar-puff", `${assetRoot}/sugar-puff.png`);
  }

  create(): void {
    this.background = this.add.image(0, 0, "flatterfluff-background").setOrigin(0).setDepth(0);
    this.overlay = this.add.graphics().setDepth(100);
    this.createHudText();
    this.scale.on(Phaser.Scale.Events.RESIZE, this.handleResize, this);

    const client = this.registry.get("hostClient") as HostClientLike;
    this.unsubscribe = client.subscribe((appState) => {
      // Intro and result screens belong to this game, not the platform.
      if (renderRoundScreens(this, appState)) {
        return;
      }

      const nextState = (appState.game?.state ?? null) as FlatterfluffState | null;
      this.language = appState.room?.language ?? "de";
      if (!nextState) {
        return;
      }
      this.state = nextState;
      this.audio.sync(nextState);
      this.consumeVisualEvents(nextState.events);
      this.renderState();
    });

    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.unsubscribe?.();
      this.unsubscribe = undefined;
      this.scale.off(Phaser.Scale.Events.RESIZE, this.handleResize, this);
      this.audio.destroy();
      this.targetSprites.clear();
      this.aimLabels.clear();
    });
  }

  private createHudText(): void {
    const titleStyle: Phaser.Types.GameObjects.Text.TextStyle = {
      fontFamily: "Trebuchet MS, Arial, sans-serif",
      fontStyle: "bold",
      color: "#ffd449",
      stroke: "#4a1f0b",
      strokeThickness: 9,
      shadow: { color: "#2b1308", blur: 0, offsetX: 0, offsetY: 7, fill: true },
      align: "center"
    };
    const labelStyle: Phaser.Types.GameObjects.Text.TextStyle = {
      fontFamily: "Trebuchet MS, Arial, sans-serif",
      fontStyle: "bold",
      color: "#fff2c7",
      stroke: "#2b1308",
      strokeThickness: 4
    };

    this.titleText = this.add
      .text(0, 0, "DULCE PUNTERÍA", { ...titleStyle, fontSize: "58px" })
      .setOrigin(0.5, 0)
      .setDepth(110);
    this.timerLabel = this.add
      .text(0, 0, "TIEMPO", { ...labelStyle, fontSize: "20px" })
      .setOrigin(0.5, 0)
      .setDepth(110);
    this.timerText = this.add
      .text(0, 0, "01:30", {
        ...labelStyle,
        fontFamily: "Courier New, monospace",
        fontSize: "42px",
        color: "#fff7d6"
      })
      .setOrigin(0.5, 0)
      .setDepth(110);
    this.modeText = this.add
      .text(0, 0, "CACERÍA", { ...labelStyle, fontSize: "17px", color: "#ffe08a" })
      .setOrigin(0.5, 0.5)
      .setDepth(110);
    this.scoreboardLabel = this.add
      .text(0, 0, "PUNTOS", { ...labelStyle, fontSize: "19px", color: "#ffe08a" })
      .setOrigin(0.5, 0)
      .setDepth(110);

    for (let index = 0; index < 6; index += 1) {
      this.scoreboardTexts.push(
        this.add
          .text(0, 0, "", { ...labelStyle, fontSize: "18px" })
          .setDepth(110)
      );
      this.ammoTexts.push(
        this.add
          .text(0, 0, "", { ...labelStyle, fontSize: "16px" })
          .setDepth(110)
      );
    }
  }

  private handleResize(): void {
    this.renderState();
  }

  private renderState(): void {
    const state = this.state;
    if (!state || !this.background || !this.overlay) {
      return;
    }

    const width = this.scale.width;
    const height = this.scale.height;
    this.background.setDisplaySize(width, height);
    this.renderTargets(state, width, height);
    this.renderHud(state, width, height);
  }

  private renderTargets(state: FlatterfluffState, width: number, height: number): void {
    const activeIds = new Set(state.targets.map((target) => target.id));
    for (const [id, sprite] of this.targetSprites) {
      if (!activeIds.has(id)) {
        sprite.destroy();
        this.targetSprites.delete(id);
      }
    }

    for (const target of state.targets) {
      let sprite = this.targetSprites.get(target.id);
      if (!sprite) {
        sprite = this.add.image(0, 0, targetTextureKeys[target.kind]);
        this.targetSprites.set(target.id, sprite);
      }
      const sizeFactor =
        target.kind === "scout"
          ? 1.95
          : target.kind === "flier"
            ? 1.65
            : target.kind === "captain"
              ? 1.3
              : target.kind === "jumbo"
                ? 1.15
                : target.kind === "golden"
                  ? 1.5
                  : 1.6;
      const displayWidth = target.radius * width * sizeFactor * target.scale;
      const textureRatio = sprite.width > 0 ? sprite.height / sprite.width : 1;
      sprite
        .setPosition(target.x * width, target.y * height)
        .setDisplaySize(displayWidth, displayWidth * textureRatio)
        .setFlipX(target.facing < 0)
        .setDepth(10 + target.lane)
        .setAlpha(1);
    }
  }

  private drawWoodPanel(
    graphics: Phaser.GameObjects.Graphics,
    x: number,
    y: number,
    width: number,
    height: number
  ): void {
    graphics.fillStyle(0x4a240f, 0.94);
    graphics.fillRoundedRect(x + 5, y + 7, width, height, 14);
    graphics.fillStyle(0xb96b28, 0.96);
    graphics.fillRoundedRect(x, y, width, height, 14);
    graphics.lineStyle(4, 0x3a1a09, 1);
    graphics.strokeRoundedRect(x, y, width, height, 14);
    graphics.fillStyle(0x241207, 0.92);
    graphics.fillRoundedRect(x + 9, y + 9, width - 18, height - 18, 9);
  }

  private renderHud(state: FlatterfluffState, width: number, height: number): void {
    const graphics = this.overlay!;
    const compact = width < 1_250;
    const leftWidth = compact ? 166 : 196;
    const boardWidth = compact ? 230 : 280;
    const margin = compact ? 14 : 24;
    const top = compact ? 12 : 20;
    graphics.clear();

    this.drawWoodPanel(graphics, margin, top, leftWidth, compact ? 78 : 92);
    this.drawWoodPanel(
      graphics,
      width - boardWidth - margin,
      top,
      boardWidth,
      compact ? 126 : 154
    );

    this.titleText
      ?.setText(flatterfluffManifest.translations[this.language === "en" ? "en" : "de"].displayName.toUpperCase())
      .setPosition(width / 2, compact ? 8 : 12)
      .setFontSize(compact ? 42 : 58)
      .setVisible(width >= 900);
    this.timerLabel
      ?.setText(this.language === "en" ? "TIME" : "TIEMPO")
      .setPosition(margin + leftWidth / 2 + (compact ? 14 : 18), top + 8)
      .setFontSize(compact ? 15 : 17);
    this.timerText
      ?.setText(formatTime(state.remainingMs))
      .setPosition(margin + leftWidth / 2, top + (compact ? 29 : 33))
      .setFontSize(compact ? 28 : 34);
    this.modeText
      ?.setText(
        state.mode === "endless"
          ? this.language === "en"
            ? "ENDLESS"
            : "SIN FIN"
          : this.language === "en"
            ? "TIMED"
            : "CACERÍA"
      )
      .setPosition(margin + leftWidth / 2, top + (compact ? 69 : 82))
      .setFontSize(compact ? 13 : 15);
    this.scoreboardLabel
      ?.setText(this.language === "en" ? "SCORE" : "PUNTOS")
      .setPosition(width - boardWidth / 2 - margin, top + 8)
      .setFontSize(compact ? 14 : 16);

    const sortedPlayers = [...state.players].sort((left, right) => right.score - left.score);
    sortedPlayers.forEach((player, index) => {
      const text = this.scoreboardTexts[index];
      text
        .setVisible(true)
        .setText(`${index + 1}. ${player.name.slice(0, 14)}   ${player.score}`)
        .setColor(player.color)
        .setPosition(
          width - boardWidth - margin + 18,
          top + (compact ? 30 : 34) + index * (compact ? 17 : 20)
        )
        .setFontSize(compact ? 13 : 15);
    });
    for (let index = sortedPlayers.length; index < this.scoreboardTexts.length; index += 1) {
      this.scoreboardTexts[index].setVisible(false);
    }

    const ammoRowHeight = compact ? 32 : 38;
    const ammoPanelWidth = Math.min(width - margin * 2, compact ? 760 : 940);
    const ammoPanelX = (width - ammoPanelWidth) / 2;
    const ammoPanelHeight = state.players.length * ammoRowHeight + 18;
    const ammoPanelY = height - ammoPanelHeight - (compact ? 10 : 18);
    this.drawWoodPanel(graphics, ammoPanelX, ammoPanelY, ammoPanelWidth, ammoPanelHeight);

    state.players.forEach((player, index) => {
      const rowY = ammoPanelY + 13 + index * ammoRowHeight;
      const startX = ammoPanelX + (compact ? 120 : 170);
      const cartridgeWidth = compact ? 13 : 16;
      const gap = compact ? 7 : 9;
      this.ammoTexts[index]
        .setVisible(true)
        .setText(
          `${player.name.slice(0, compact ? 9 : 14)}  ${player.reloadEndsAt ? (this.language === "en" ? "RELOADING" : "RECARGANDO") : player.score}`
        )
        .setColor(player.color)
        .setPosition(ammoPanelX + 16, rowY)
        .setFontSize(compact ? 13 : 16);

      for (let ammoIndex = 0; ammoIndex < player.maxAmmo; ammoIndex += 1) {
        const x = startX + ammoIndex * (cartridgeWidth + gap);
        graphics.fillStyle(ammoIndex < player.ammo ? 0xffc52f : 0x3b2a1d, 1);
        graphics.fillRoundedRect(x, rowY + 2, cartridgeWidth, compact ? 22 : 27, 5);
        graphics.lineStyle(2, ammoIndex < player.ammo ? 0x7a3d08 : 0x1d120b, 1);
        graphics.strokeRoundedRect(x, rowY + 2, cartridgeWidth, compact ? 22 : 27, 5);
      }

      this.drawReticle(graphics, player, width, height);
      let label = this.aimLabels.get(player.playerId);
      if (!label) {
        label = this.add
          .text(0, 0, player.name, {
            fontFamily: "Trebuchet MS, Arial, sans-serif",
            fontSize: "14px",
            fontStyle: "bold",
            color: player.color,
            stroke: "#2b1308",
            strokeThickness: 4
          })
          .setOrigin(0.5, 0)
          .setDepth(104);
        this.aimLabels.set(player.playerId, label);
      }
      label
        .setText(player.name)
        .setColor(player.color)
        .setPosition(player.aimX * width, player.aimY * height + (compact ? 23 : 28));
    });
    for (let index = state.players.length; index < this.ammoTexts.length; index += 1) {
      this.ammoTexts[index].setVisible(false);
    }
  }

  private drawReticle(
    graphics: Phaser.GameObjects.Graphics,
    player: FlatterfluffState["players"][number],
    width: number,
    height: number
  ): void {
    const x = player.aimX * width;
    const y = player.aimY * height;
    const radius = width < 1_250 ? 19 : 24;
    const color = colorNumber(player.color);
    graphics.lineStyle(width < 1_250 ? 3 : 4, color, 0.98);
    graphics.strokeCircle(x, y, radius);
    graphics.beginPath();
    graphics.moveTo(x - radius - 9, y);
    graphics.lineTo(x - radius + 6, y);
    graphics.moveTo(x + radius - 6, y);
    graphics.lineTo(x + radius + 9, y);
    graphics.moveTo(x, y - radius - 9);
    graphics.lineTo(x, y - radius + 6);
    graphics.moveTo(x, y + radius - 6);
    graphics.lineTo(x, y + radius + 9);
    graphics.strokePath();
    graphics.fillStyle(color, 1);
    graphics.fillCircle(x, y, 3);
  }

  private consumeVisualEvents(events: FlatterfluffGameEvent[]): void {
    for (const event of events) {
      if (this.visualEventIds.has(event.id)) {
        continue;
      }
      this.visualEventIds.add(event.id);
      if (event.kind === "hit" || event.kind === "armor" || event.kind === "ammo") {
        this.spawnEventEffect(event);
      }
    }

    const activeIds = new Set(events.map((event) => event.id));
    for (const id of this.visualEventIds) {
      if (!activeIds.has(id)) {
        this.visualEventIds.delete(id);
      }
    }
  }

  private spawnEventEffect(event: FlatterfluffGameEvent): void {
    const width = this.scale.width;
    const height = this.scale.height;
    const x = event.x * width;
    const y = event.y * height;
    const texture =
      event.kind === "ammo" ? "flatterfluff-cartridges" : "flatterfluff-sugar-puff";
    const effect = this.add.image(x, y, texture).setDepth(80).setScale(0.16).setAlpha(0.95);
    const targetScale = event.kind === "ammo" ? 0.34 : 0.42;
    this.tweens.add({
      targets: effect,
      scale: targetScale,
      alpha: 0,
      y: y - 36,
      duration: event.kind === "ammo" ? 850 : 620,
      ease: "Cubic.Out",
      onComplete: () => effect.destroy()
    });

    const label =
      event.kind === "ammo"
        ? this.language === "en"
          ? "AMMO!"
          : "¡MUNICIÓN!"
        : event.kind === "armor"
          ? `+${event.points ?? 0}  KLONK!`
          : `+${event.points ?? 0}`;
    const text = this.add
      .text(x, y - 22, label, {
        fontFamily: "Trebuchet MS, Arial, sans-serif",
        fontSize: event.kind === "ammo" ? "25px" : "30px",
        fontStyle: "bold",
        color: event.kind === "ammo" ? "#ffd43b" : "#ffffff",
        stroke: "#4a1f0b",
        strokeThickness: 7
      })
      .setOrigin(0.5)
      .setDepth(90);
    this.tweens.add({
      targets: text,
      y: y - 82,
      alpha: 0,
      duration: 880,
      ease: "Quad.Out",
      onComplete: () => text.destroy()
    });
  }
}

export const hostGame = {
  id: flatterfluffManifest.id,
  displayName: flatterfluffManifest.displayName,
  sceneKey: flatterfluffManifest.hostView,
  scene: FlatterfluffHostScene
} as const;
