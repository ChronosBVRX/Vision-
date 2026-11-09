import Phaser from "phaser";
import type { SupportedLanguage } from "@open-party-lab/game-core";
import type { BuzzwortMode } from "../protocol.js";
import { font, ink, paint } from "./theme.js";

export interface BuzzwortHudCallbacks {
  onSelectMode: (mode: BuzzwortMode) => void;
  onToggleWatcher: (enabled: boolean) => void;
  onSelectDuration: (durationMs: number) => void;
}

export interface BuzzwortHudModel {
  roomCode: string;
  mode: BuzzwortMode;
  watcherEnabled: boolean;
  turnDurationMs: number;
  locked: boolean;
  language?: SupportedLanguage;
}

type AccentKey = "accent" | "danger" | "warning";

interface Chip {
  background: Phaser.GameObjects.Graphics;
  label: Phaser.GameObjects.Text;
  zone: Phaser.GameObjects.Zone;
  x: number;
  y: number;
  width: number;
  height: number;
  active: boolean;
  accent: AccentKey;
}

const CHIP_HEIGHT = 38;

export const BUZZWORT_HUD_HEIGHT = 132;

/**
 * Kopfzeile des Host-Screens mit den Raumeinstellungen.
 *
 * Die Objekte werden einmal erzeugt und danach nur noch umgefaerbt. Farben
 * kommen bei jedem Neuzeichnen frisch aus den Platform-Tokens, damit ein
 * Theme-Wechsel sofort durchschlaegt.
 */
export class BuzzwortHud {
  private readonly scene: Phaser.Scene;
  private readonly callbacks: BuzzwortHudCallbacks;
  private readonly container: Phaser.GameObjects.Container;
  private readonly background: Phaser.GameObjects.Graphics;
  private readonly roomLabel: Phaser.GameObjects.Text;
  private readonly title: Phaser.GameObjects.Text;
  private readonly hint: Phaser.GameObjects.Text;
  private readonly chips = new Map<string, Chip>();
  private width: number;

  constructor(scene: Phaser.Scene, callbacks: BuzzwortHudCallbacks) {
    this.scene = scene;
    this.callbacks = callbacks;
    this.width = scene.scale.width;
    this.container = scene.add.container(0, 0).setDepth(20);

    this.background = scene.add.graphics();
    this.container.add(this.background);

    this.roomLabel = scene.add.text(40, 26, "", {
      fontFamily: font.mono,
      fontSize: "18px",
      color: ink.muted
    });
    this.title = scene.add.text(40, 46, "PALABRA DE BUZZ", {
      fontFamily: font.title,
      fontSize: "40px",
      color: ink.text
    });
    this.hint = scene.add.text(40, 96, "", {
      fontFamily: font.body,
      fontSize: "14px",
      color: ink.muted
    });
    this.container.add([this.roomLabel, this.title, this.hint]);

    this.buildChips();
    this.layout();
  }

  private accentColor(key: AccentKey): number {
    return key === "danger" ? paint.danger : key === "warning" ? paint.warning : paint.accent;
  }

  private buildChips(): void {
    this.createChip("mode:duel", 168, "accent", () => this.callbacks.onSelectMode("duel"));
    this.createChip("mode:team", 128, "accent", () => this.callbacks.onSelectMode("team"));
    this.createChip("watcher", 150, "danger", () => this.callbacks.onToggleWatcher(true));
    this.createChip("time:45000", 62, "warning", () => this.callbacks.onSelectDuration(45_000));
    this.createChip("time:60000", 62, "warning", () => this.callbacks.onSelectDuration(60_000));
    this.createChip("time:90000", 62, "warning", () => this.callbacks.onSelectDuration(90_000));
  }

  private createChip(id: string, width: number, accent: AccentKey, onClick: () => void): void {
    const background = this.scene.add.graphics();
    const label = this.scene.add
      .text(0, 0, "", {
        fontFamily: font.body,
        fontSize: "14px",
        color: ink.text,
        align: "center"
      })
      .setOrigin(0.5);
    const zone = this.scene.add.zone(0, 0, width, CHIP_HEIGHT).setOrigin(0).setInteractive({ useHandCursor: true });

    zone.on("pointerdown", () => {
      onClick();
    });

    this.container.add([background, label, zone]);
    this.chips.set(id, { background, label, zone, x: 0, y: 0, width, height: CHIP_HEIGHT, active: false, accent });
  }

  private drawChip(chip: Chip): void {
    const accent = this.accentColor(chip.accent);
    const fill = chip.active ? accent : paint.surface;
    const alpha = chip.active ? 0.28 : 0.75;

    chip.background.clear();
    chip.background.fillStyle(fill, alpha);
    chip.background.fillRoundedRect(chip.x, chip.y, chip.width, chip.height, 10);
    chip.background.lineStyle(1.5, chip.active ? accent : paint.line, chip.active ? 1 : 0.7);
    chip.background.strokeRoundedRect(chip.x, chip.y, chip.width, chip.height, 10);

    chip.label.setPosition(chip.x + chip.width / 2, chip.y + chip.height / 2);
    chip.label.setColor(chip.active ? ink.text : ink.muted);
    chip.zone.setPosition(chip.x, chip.y);
  }

  private positionChip(id: string, x: number, y: number): number {
    const chip = this.chips.get(id);

    if (!chip) {
      return x;
    }

    chip.x = x;
    chip.y = y;
    this.drawChip(chip);

    return x + chip.width + 8;
  }

  layout(): void {
    this.width = this.scene.scale.width;

    this.background.clear();
    this.background.fillStyle(paint.surface, 0.5);
    this.background.fillRect(0, 0, this.width, BUZZWORT_HUD_HEIGHT);
    this.background.lineStyle(1, paint.line, 0.9);
    this.background.lineBetween(0, BUZZWORT_HUD_HEIGHT, this.width, BUZZWORT_HUD_HEIGHT);

    this.roomLabel.setColor(ink.muted);
    this.title.setColor(ink.text);
    this.hint.setColor(ink.muted);

    const right = this.width - 40;
    const rowOneWidth =
      (this.chips.get("mode:duel")?.width ?? 0) +
      (this.chips.get("mode:team")?.width ?? 0) +
      (this.chips.get("watcher")?.width ?? 0) +
      16;
    let cursor = right - rowOneWidth;

    cursor = this.positionChip("mode:duel", cursor, 26);
    cursor = this.positionChip("mode:team", cursor, 26);
    this.positionChip("watcher", cursor, 26);

    const rowTwoWidth = 62 * 3 + 16;
    let timeCursor = right - rowTwoWidth;

    timeCursor = this.positionChip("time:45000", timeCursor, 74);
    timeCursor = this.positionChip("time:60000", timeCursor, 74);
    this.positionChip("time:90000", timeCursor, 74);
  }

  update(model: BuzzwortHudModel): void {
    const en = model.language === "en";
    this.title.setText(en ? "BUZZWORD" : "PALABRA DE BUZZ");

    this.roomLabel.setText(`${en ? "ROOM" : "SALA"} ${model.roomCode}`);
    this.hint.setText(
      model.locked
        ? en
          ? "Settings are locked while a round is running."
          : "Los ajustes se bloquean durante una ronda activa."
        : en
          ? "Pick mode, watcher and turn length before you start."
          : "Seleccione el modo, la guardia y la longitud de giro antes de comenzar."
    );

    this.setChip("mode:duel", en ? "Free-for-all" : "Todos contra todos", model.mode === "duel", model.locked);
    this.setChip("mode:team", en ? "Team mode" : "Modo equipo", model.mode === "team", model.locked);

    const watcherLocked = model.locked || model.mode === "team";
    this.setChip(
      "watcher",
      model.watcherEnabled
        ? en
          ? "Watcher: on"
          : "Guardián: encendido"
        : en
          ? "Watcher: off"
          : "Guardián: apagado",
      model.watcherEnabled,
      watcherLocked
    );

    const watcherChip = this.chips.get("watcher");

    if (watcherChip) {
      watcherChip.zone.removeAllListeners("pointerdown");
      watcherChip.zone.on("pointerdown", () => {
        if (watcherLocked) {
          return;
        }

        this.callbacks.onToggleWatcher(!model.watcherEnabled);
      });
    }

    for (const durationMs of [45_000, 60_000, 90_000]) {
      this.setChip(
        `time:${durationMs}`,
        `${durationMs / 1000}s`,
        model.turnDurationMs === durationMs,
        model.locked
      );
    }
  }

  private setChip(id: string, label: string, active: boolean, disabled: boolean): void {
    const chip = this.chips.get(id);

    if (!chip) {
      return;
    }

    chip.label.setText(label);
    chip.active = active;
    chip.background.setAlpha(disabled ? 0.45 : 1);
    chip.label.setAlpha(disabled ? 0.5 : 1);

    if (disabled) {
      chip.zone.disableInteractive();
    } else {
      chip.zone.setInteractive({ useHandCursor: true });
    }

    this.drawChip(chip);
  }

  destroy(): void {
    this.container.destroy(true);
  }
}
