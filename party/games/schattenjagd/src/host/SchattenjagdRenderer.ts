import Phaser from "phaser";
import { schattenjagdConfig, transportModes, type TransportMode } from "../config.js";
import type { SchattenjagdPublicState, StationNode, TransitMap } from "../protocol.js";
import { getSchattenjagdText, type SchattenjagdLanguage } from "../text.js";
import { modeTicketTextureKeys, modeVehicleTextureKeys } from "./assets.js";
import { buildCityNetwork, CITY_LANE_WIDTH, networkRoutePoints, roadKey, routeKey, sectionLane, type CityNetwork } from "../map/cityNetwork.js";
import { cityBoardStyles } from "./cityBoardStyle.js";
import { hostTheme, modeColors } from "./theme.js";

interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface Projection {
  toScreenX(mapX: number): number;
  toScreenY(mapY: number): number;
  scale: number;
  board: Rect;
}

interface ScreenPoint {
  x: number;
  y: number;
}

type StationShape = "circle" | "square" | "hexagon";

const BAR_HEIGHT = 94;
const GUTTER = 12;

function stageLabel(state: SchattenjagdPublicState, language: SchattenjagdLanguage): string {
  const text = getSchattenjagdText(language);

  switch (state.stage) {
    case "shadow_move":
      return text.stageShadow;
    case "detective_move":
      return state.detectives.find((entry) => entry.playerId === state.activeDetectivePlayerId)
        ? text.activeDetective(state.detectives.find((entry) => entry.playerId === state.activeDetectivePlayerId)!.name)
        : text.stageDetective;
    case "caught":
      return text.stageCaught;
    case "escaped":
      return text.stageEscaped;
    default:
      return text.title;
  }
}

function initials(name: string): string {
  const trimmed = (name ?? "").trim();

  if (!trimmed) {
    return "?";
  }

  const parts = trimmed.split(/\s+/);

  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }

  return `${parts[0][0] ?? ""}${parts[1][0] ?? ""}`.toUpperCase();
}

function parseColor(value: string, fallback = 0x38bdf8): number {
  const match = /^#?([0-9a-f]{6})$/i.exec(value ?? "");
  return match ? Number.parseInt(match[1], 16) : fallback;
}

function stationShape(station: StationNode): StationShape {
  if (station.modes.includes("metro")) {
    return "hexagon";
  }

  if (station.modes.includes("bus")) {
    return "square";
  }

  return "circle";
}

function hexagonPoints(x: number, y: number, radius: number): Phaser.Geom.Point[] {
  return Array.from({ length: 6 }, (_, index) => {
    const angle = -Math.PI / 2 + (Math.PI * 2 * index) / 6;
    return new Phaser.Geom.Point(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
  });
}

function fillStationShape(
  graphics: Phaser.GameObjects.Graphics,
  shape: StationShape,
  x: number,
  y: number,
  radius: number,
  color: number,
  alpha = 1
): void {
  graphics.fillStyle(color, alpha);

  if (shape === "circle") {
    graphics.fillCircle(x, y, radius);
  } else if (shape === "square") {
    graphics.fillRoundedRect(x - radius, y - radius, radius * 2, radius * 2, radius * 0.22);
  } else {
    graphics.fillPoints(hexagonPoints(x, y, radius), true);
  }
}

function strokeStationShape(
  graphics: Phaser.GameObjects.Graphics,
  shape: StationShape,
  x: number,
  y: number,
  radius: number,
  color: number,
  width: number,
  alpha = 1
): void {
  graphics.lineStyle(width, color, alpha);

  if (shape === "circle") {
    graphics.strokeCircle(x, y, radius);
  } else if (shape === "square") {
    graphics.strokeRoundedRect(x - radius, y - radius, radius * 2, radius * 2, radius * 0.22);
  } else {
    graphics.strokePoints(hexagonPoints(x, y, radius), true);
  }
}

/** Verkehrsmittel-Symbol: Taxi = Kreis, Bus = Quadrat, Metro = Sechseck. */
function drawModeSymbol(
  graphics: Phaser.GameObjects.Graphics,
  mode: TransportMode | null,
  x: number,
  y: number,
  size: number
): void {
  const color = mode ? modeColors[mode] : 0xf472b6;
  graphics.fillStyle(color, 1);

  if (mode === "taxi") {
    graphics.fillCircle(x, y, size);
    return;
  }

  if (mode === "bus") {
    graphics.fillRect(x - size, y - size, size * 2, size * 2);
    return;
  }

  if (mode === "metro") {
    graphics.fillPoints(hexagonPoints(x, y, size * 1.25), true);
    return;
  }

  // Unbekannt (Schleier)
  graphics.fillCircle(x, y, size * 0.55);
  graphics.lineStyle(2, color, 0.9);
  graphics.strokeCircle(x, y, size * 1.2);
}

export class SchattenjagdRenderer {
  private readonly root: Phaser.GameObjects.Container;
  private readonly fxLayer: Phaser.GameObjects.Container;
  private readonly activeTweens: Phaser.Tweens.Tween[] = [];
  private countdownText: Phaser.GameObjects.Text | null = null;
  private turnEndsAt: number | null = null;
  private lastStations = new Map<string, number>();
  private lastShadowSighting: string | null = null;
  private mapSignature = "";
  private networkMap: TransitMap | null = null;
  private network: CityNetwork | null = null;

  constructor(private readonly scene: Phaser.Scene) {
    this.root = scene.add.container(0, 0);
    this.fxLayer = scene.add.container(0, 0);
  }

  destroy(): void {
    this.stopTweens();
    this.fxLayer.destroy(true);
    this.root.destroy(true);
  }

  private stopTweens(): void {
    for (const tween of this.activeTweens.splice(0, this.activeTweens.length)) {
      tween.stop();
      tween.remove();
    }
  }

  updateCountdown(now: number): void {
    if (!this.countdownText) {
      return;
    }

    if (this.turnEndsAt === null) {
      this.countdownText.setText("");
      return;
    }

    const remaining = Math.max(0, Math.ceil((this.turnEndsAt - now) / 1000));
    this.countdownText.setText(`${remaining}s`);
    this.countdownText.setColor(remaining <= 8 ? hostTheme.danger : hostTheme.text);
  }

  render(state: SchattenjagdPublicState | null, language: SchattenjagdLanguage): void {
    this.stopTweens();
    this.root.removeAll(true);
    this.countdownText = null;
    this.turnEndsAt = state?.turnEndsAt ?? null;

    const width = this.scene.scale.width;
    const height = this.scene.scale.height;

    this.drawBackground(width, height);

    if (!state || !state.map || state.map.stations.length === 0) {
      const waiting = this.scene.add
        .text(width / 2, height / 2, getSchattenjagdText(language).preparing, {
          fontFamily: hostTheme.titleFont,
          fontSize: "30px",
          color: hostTheme.muted
        })
        .setOrigin(0.5);
      this.root.add(waiting);
      return;
    }

    const signature = `${state.mapVariant ?? "night"}:${state.map.stations.length}:${state.map.links.length}:${state.map.stations[0]?.x}:${state.map.stations[0]?.y}`;

    if (signature !== this.mapSignature) {
      this.mapSignature = signature;
      this.lastStations.clear();
      this.lastShadowSighting = null;
      this.fxLayer.removeAll(true);
    }

    const mapArea: Rect = {
      x: GUTTER,
      y: GUTTER,
      width: width - GUTTER * 2,
      height: height - BAR_HEIGHT - GUTTER * 2
    };

    const barArea: Rect = {
      x: GUTTER,
      y: height - BAR_HEIGHT - GUTTER * 0.5,
      width: width - GUTTER * 2,
      height: BAR_HEIGHT
    };

    const projection = this.drawMap(state, language, mapArea);
    this.drawBottomBar(state, language, barArea);
    this.playMovementEffects(state, projection);
  }

  private drawBackground(width: number, height: number): void {
    const graphics = this.scene.add.graphics();
    graphics.fillStyle(hostTheme.background, 1);
    graphics.fillRect(0, 0, width, height);

    graphics.fillStyle(hostTheme.backgroundGlow, 0.5);
    graphics.fillCircle(width * 0.26, height * 0.26, Math.max(width, height) * 0.44);
    graphics.fillStyle(0x1b1038, 0.36);
    graphics.fillCircle(width * 0.8, height * 0.74, Math.max(width, height) * 0.34);
    graphics.fillStyle(hostTheme.background, 0.42);
    graphics.fillRect(0, 0, width, height);

    this.root.add(graphics);
  }

  private panel(area: Rect, alpha = 0.8, radius = 16): void {
    const graphics = this.scene.add.graphics();
    graphics.fillStyle(hostTheme.panel, alpha);
    graphics.fillRoundedRect(area.x, area.y, area.width, area.height, radius);
    graphics.lineStyle(1.5, hostTheme.panelStroke, 0.85);
    graphics.strokeRoundedRect(area.x, area.y, area.width, area.height, radius);
    this.root.add(graphics);
  }

  private addModeTicket(
    mode: TransportMode,
    x: number,
    y: number,
    size: number,
    alpha = 1
  ): Phaser.GameObjects.Image | null {
    const textureKey = modeTicketTextureKeys[mode];

    if (!this.scene.textures.exists(textureKey)) {
      return null;
    }

    const ticket = this.scene.add
      .image(x, y, textureKey)
      .setDisplaySize(size, size)
      .setAlpha(alpha);
    this.root.add(ticket);
    return ticket;
  }

  private buildLinkPath(
    state: SchattenjagdPublicState,
    projection: Projection,
    stationById: Map<number, StationNode>,
    a: number,
    b: number,
    mode: TransportMode,
    reverse = false
  ): ScreenPoint[] {
    const from = stationById.get(a);
    const to = stationById.get(b);

    if (!from || !to) {
      return [];
    }

    const points = networkRoutePoints(this.getNetwork(state.map), { a, b, mode });
    const ordered = reverse ? points.reverse() : points;

    return ordered.map((point) => ({
      x: projection.toScreenX(point.x),
      y: projection.toScreenY(point.y)
    }));
  }

  private getNetwork(map: TransitMap): CityNetwork {
    if (this.networkMap !== map || !this.network) {
      this.networkMap = map;
      this.network = buildCityNetwork(map);
    }
    return this.network;
  }

  private strokePath(graphics: Phaser.GameObjects.Graphics, points: readonly ScreenPoint[]): void {
    if (points.length < 2) {
      return;
    }

    graphics.beginPath();
    graphics.moveTo(points[0].x, points[0].y);

    for (const point of points.slice(1)) {
      graphics.lineTo(point.x, point.y);
    }

    graphics.strokePath();
  }

  /** Projiziert das feste Spielbrett ohne Verzerrung deckungsgleich auf das Kartenbild. */
  private buildProjection(state: SchattenjagdPublicState, area: Rect): Projection {
    const inset = 5;
    const availableWidth = Math.max(1, area.width - inset * 2);
    const availableHeight = Math.max(1, area.height - inset * 2);
    const scale = Math.min(availableWidth / state.map.width, availableHeight / state.map.height);
    const board: Rect = {
      x: area.x + (area.width - state.map.width * scale) / 2,
      y: area.y + (area.height - state.map.height * scale) / 2,
      width: state.map.width * scale,
      height: state.map.height * scale
    };

    return {
      scale,
      board,
      toScreenX: (mapX: number) => board.x + mapX * scale,
      toScreenY: (mapY: number) => board.y + mapY * scale
    };
  }

  private drawMap(
    state: SchattenjagdPublicState,
    language: SchattenjagdLanguage,
    area: Rect
  ): Projection {
    this.panel(area, 0.9, 20);

    const projection = this.buildProjection(state, area);
    const stationById = new Map(state.map.stations.map((station) => [station.id, station]));
    const boardStyle = cityBoardStyles[state.mapVariant ?? "night"];
    const activeDetective = state.detectives.find((entry) => entry.playerId === state.activeDetectivePlayerId);
    const reachable = new Set((state.reachableOptions ?? []).map((option) => option.stationId));

    if (this.scene.textures.exists(boardStyle.textureKey)) {
      const mapImage = this.scene.add
        .image(
          projection.board.x + projection.board.width / 2,
          projection.board.y + projection.board.height / 2,
          boardStyle.textureKey
        )
        .setDisplaySize(projection.board.width, projection.board.height)
        .setAlpha(boardStyle.mapAlpha);
      this.root.add(mapImage);

      const mapShade = this.scene.add.graphics();
      mapShade.fillStyle(boardStyle.casing, Math.max(boardStyle.shade, 0.32));
      mapShade.fillRect(
        projection.board.x,
        projection.board.y,
        projection.board.width,
        projection.board.height
      );
      this.root.add(mapShade);
    }

    const network = this.getNetwork(state.map);
    const selectedLanes = new Set<string>();
    if (activeDetective) {
      for (const option of state.reachableOptions ?? []) {
        const route = network.routes.get(routeKey({
          a: activeDetective.stationId, b: option.stationId, mode: option.mode
        })) ?? [];
        for (let index = 1; index < route.length; index += 1) {
          selectedLanes.add(`${roadKey(route[index - 1], route[index])}:${option.mode}`);
        }
      }
    }
    const toScreen = (points: readonly ScreenPoint[]): ScreenPoint[] => points.map(point => ({
      x: projection.toScreenX(point.x), y: projection.toScreenY(point.y)
    }));
    const lanes = this.scene.add.graphics();
    const highlights = this.scene.add.graphics();
    const lineWidth = CITY_LANE_WIDTH * projection.scale;
    for (const section of network.sections) {
      // Just a fine outer outline, rather than a broad road bed. Lane width
      // equals lane spacing, so the coloured tracks touch at shared bends.
      for (const mode of transportModes) {
        if (!section.modes.has(mode)) continue;
        lanes.lineStyle(lineWidth + 0.8 * projection.scale, boardStyle.casing, 0.9);
        this.strokePath(lanes, toScreen(sectionLane(section, mode)));
      }
      for (const mode of transportModes) {
        if (!section.modes.has(mode)) continue;
        const path = toScreen(sectionLane(section, mode));
        lanes.lineStyle(lineWidth, boardStyle.mutedModeColors[mode], activeDetective ? 0.85 : 1);
        this.strokePath(lanes, path);
        if (selectedLanes.has(`${section.key}:${mode}`)) {
          highlights.lineStyle(lineWidth, boardStyle.modeColors[mode], 1);
          this.strokePath(highlights, path);
        }
      }
    }
    this.root.add([lanes, highlights]);

    const stationRadius = Math.max(12, 14 * projection.scale);
    const labelSize = Math.max(12, Math.round(stationRadius * 1.05));
    const stationLayer = this.scene.add.graphics();

    const detectivesByStation = new Map<number, SchattenjagdPublicState["detectives"]>();

    for (const detective of state.detectives) {
      const bucket = detectivesByStation.get(detective.stationId) ?? [];
      bucket.push(detective);
      detectivesByStation.set(detective.stationId, bucket);
    }

    for (const station of state.map.stations) {
      const centreX = projection.toScreenX(station.x);
      const centreY = projection.toScreenY(station.y);
      const shape = stationShape(station);
      const occupants = detectivesByStation.get(station.id) ?? [];
      if (reachable.has(station.id) || activeDetective?.stationId === station.id) {
        strokeStationShape(stationLayer, shape, centreX, centreY, stationRadius + 7,
          parseColor(activeDetective?.color ?? "#ffffff"), activeDetective?.stationId === station.id ? 4 : 2.5, 1);
      }
      const primaryMode = station.modes.includes("metro") ? "metro" : station.modes.includes("bus") ? "bus" : "taxi";

      fillStationShape(
        stationLayer,
        shape,
        centreX,
        centreY,
        stationRadius + 3.5,
        boardStyle.stationOuter,
        0.98
      );

      fillStationShape(stationLayer, shape, centreX, centreY, stationRadius,
        boardStyle.modeColors[primaryMode], 1);

      const coreRadius = stationRadius - 2.8;
      const occupantColor = occupants[0] ? parseColor(occupants[0].color) : boardStyle.casing;
      fillStationShape(
        stationLayer,
        shape,
        centreX,
        centreY,
        coreRadius,
        occupantColor,
        occupants[0]?.connected === false ? 0.5 : 0.98
      );
      strokeStationShape(
        stationLayer,
        shape,
        centreX,
        centreY,
        stationRadius + 3.5,
        boardStyle.stationOutline,
        Math.max(0.8, projection.scale),
        0.25
      );
    }

    this.root.add(stationLayer);

    // Spielerfarbe fuellt das Stationszentrum; Ring und Initialen bleiben als Fernwirkung erhalten.
    const ringLayer = this.scene.add.graphics();

    for (const [stationId, occupants] of detectivesByStation) {
      const station = stationById.get(stationId);

      if (!station) {
        continue;
      }

      const centreX = projection.toScreenX(station.x);
      const centreY = projection.toScreenY(station.y);

      occupants.forEach((detective, index) => {
        const ringRadius = stationRadius + 4 + index * 5;
        ringLayer.lineStyle(5.5, boardStyle.casing, 0.88);
        ringLayer.strokeCircle(centreX, centreY, ringRadius);
        ringLayer.lineStyle(3.5, parseColor(detective.color), detective.connected ? 1 : 0.4);
        ringLayer.strokeCircle(centreX, centreY, ringRadius);

        if (detective.isCapturer) {
          ringLayer.lineStyle(2.5, 0x4ade80, 0.95);
          ringLayer.strokeCircle(centreX, centreY, ringRadius + 5);
        }
      });

      const badgeY = centreY - stationRadius - 13;
      let badgeX = centreX - ((occupants.length - 1) * 26) / 2;

      for (const detective of occupants) {
        const badge = this.scene.add.graphics();
        badge.fillStyle(parseColor(detective.color), detective.connected ? 0.95 : 0.45);
        badge.fillRoundedRect(badgeX - 13, badgeY - 9, 26, 18, 6);
        this.root.add(badge);

        const badgeText = this.scene.add
          .text(badgeX, badgeY, initials(detective.name), {
            fontFamily: hostTheme.titleFont,
            fontSize: "12px",
            color: "#0b1220"
          })
          .setOrigin(0.5);
        this.root.add(badgeText);

        badgeX += 26;
      }
    }

    this.root.add(ringLayer);

    // Stationsnummern zuletzt zeichnen, damit sie immer oben liegen.
    for (const station of state.map.stations) {
      const label = this.scene.add
        .text(
          projection.toScreenX(station.x),
          projection.toScreenY(station.y),
          `${station.id}`,
          {
            fontFamily: hostTheme.titleFont,
            fontSize: `${labelSize}px`,
            color: "#ffffff",
            stroke: boardStyle.labelStroke,
            strokeThickness: 3
          }
        )
        .setOrigin(0.5);
      this.root.add(label);
    }

    this.drawShadowMarker(state, language, projection, stationById, stationRadius);

    return projection;
  }

  private drawShadowMarker(
    state: SchattenjagdPublicState,
    language: SchattenjagdLanguage,
    projection: Projection,
    stationById: Map<number, StationNode>,
    stationRadius: number
  ): void {
    const text = getSchattenjagdText(language);
    const revealedStationId = state.shadow.currentStationId ?? state.shadow.lastKnownStationId;

    if (revealedStationId === null) {
      return;
    }

    const station = stationById.get(revealedStationId);

    if (!station) {
      return;
    }

    const centreX = projection.toScreenX(station.x);
    const centreY = projection.toScreenY(station.y);
    const isLive = state.shadow.currentStationId !== null;

    const marker = this.scene.add.graphics();
    marker.lineStyle(3.5, isLive ? 0xfb7185 : 0xf472b6, 0.95);
    marker.strokeCircle(0, 0, stationRadius + 11);
    marker.lineStyle(1.5, 0xf9a8d4, 0.5);
    marker.strokeCircle(0, 0, stationRadius + 19);

    const container = this.scene.add.container(centreX, centreY, [marker]);
    this.root.add(container);

    this.activeTweens.push(
      this.scene.tweens.add({
        targets: container,
        scale: { from: 0.9, to: 1.12 },
        alpha: { from: 0.7, to: 1 },
        duration: 1100,
        yoyo: true,
        repeat: -1,
        ease: "Sine.easeInOut"
      })
    );

    const caption = this.scene.add
      .text(
        centreX,
        centreY + stationRadius + 22,
        isLive
          ? state.shadow.name
          : `${text.sightingLabel} ${text.turnLabel} ${state.shadow.lastRevealTurn ?? "?"}`,
        {
          fontFamily: hostTheme.bodyFont,
          fontSize: "14px",
          color: hostTheme.shadow,
          backgroundColor: "rgba(8, 12, 24, 0.78)",
          padding: { x: 6, y: 2 }
        }
      )
      .setOrigin(0.5);

    this.root.add(caption);
  }

  private drawLegend(language: SchattenjagdLanguage, area: Rect): void {
    const text = getSchattenjagdText(language);
    const legendY = area.y;
    let cursorX = area.x;

    for (const mode of transportModes) {
      const ticket = this.addModeTicket(mode, cursorX + 9, legendY, 28);

      if (!ticket) {
        const symbol = this.scene.add.graphics();
        drawModeSymbol(symbol, mode, cursorX + 9, legendY, 6);
        this.root.add(symbol);
      }

      const label = this.scene.add
        .text(cursorX + 27, legendY, text.modeLabels[mode], {
          fontFamily: hostTheme.bodyFont,
          fontSize: "14px",
          color: hostTheme.muted
        })
        .setOrigin(0, 0.5);

      this.root.add(label);
      cursorX += 27 + label.width + 20;
    }
  }

  private drawBottomBar(
    state: SchattenjagdPublicState,
    language: SchattenjagdLanguage,
    area: Rect
  ): void {
    const text = getSchattenjagdText(language);
    this.panel(area, 0.92, 16);

    const infoWidth = 250;
    const baseY = area.y + 6;

    const title = this.scene.add.text(area.x + 18, baseY, text.title, {
      fontFamily: hostTheme.titleFont,
      fontSize: "19px",
      color: hostTheme.text
    });
    this.root.add(title);

    const stage = this.scene.add.text(area.x + 18, baseY + 24, stageLabel(state, language), {
      fontFamily: hostTheme.bodyFont,
      fontSize: "14px",
      color: state.stage === "shadow_move" ? hostTheme.shadow : hostTheme.accent
    });
    this.root.add(stage);

    const turnInfo = this.scene.add.text(
      area.x + 18,
      baseY + 44,
      `${text.turnLabel} ${state.turn}/${state.totalTurns}`,
      {
        fontFamily: hostTheme.titleFont,
        fontSize: "17px",
        color: hostTheme.text
      }
    );
    this.root.add(turnInfo);

    this.countdownText = this.scene.add
      .text(area.x + 18 + turnInfo.width + 16, baseY + 46, "", {
        fontFamily: hostTheme.titleFont,
        fontSize: "17px",
        color: hostTheme.text
      })
      .setOrigin(0, 0);
    this.root.add(this.countdownText);
    this.updateCountdown(Date.now());

    if (state.outcome) {
      const outcomeText =
        state.outcome === "caught"
          ? `${text.stageCaught}${state.capturedByName ? ` - ${state.capturedByName}` : ""}`
          : text.stageEscaped;

      const outcomeLabel = this.scene.add
        .text(area.x + 18, baseY + 44, outcomeText, {
          fontFamily: hostTheme.titleFont,
          fontSize: "17px",
          color: state.outcome === "caught" ? hostTheme.positive : hostTheme.shadow,
          wordWrap: { width: infoWidth - 20 }
        })
        .setOrigin(0, 0);
      this.root.add(outcomeLabel);
      turnInfo.setVisible(false);
      this.countdownText.setVisible(false);
    }

    this.drawLegend(language, { x: area.x + 18, y: baseY + 76, width: infoWidth, height: 14 });

    this.drawTurnRail(state, language, {
      x: area.x + infoWidth,
      y: area.y,
      width: area.width - infoWidth - 26,
      height: area.height
    });
  }

  /**
   * Zeitstrahl der Runde: ein Punkt je Zug. Vergangene Zuege tragen das Symbol des
   * benutzten Verkehrsmittels, Enttarnungszuege zeigen zusaetzlich die Station.
   */
  private drawTurnRail(
    state: SchattenjagdPublicState,
    language: SchattenjagdLanguage,
    area: Rect
  ): void {
    const text = getSchattenjagdText(language);
    const railY = area.y + area.height * 0.56;
    const slotWidth = area.width / Math.max(1, state.totalTurns);
    const slotX = (turn: number) => area.x + (turn - 0.5) * slotWidth;

    const heading = this.scene.add
      .text(area.x, area.y + 12, text.travelLogTitle, {
        fontFamily: hostTheme.bodyFont,
        fontSize: "12px",
        color: hostTheme.muted
      })
      .setOrigin(0, 0.5);
    this.root.add(heading);

    const rail = this.scene.add.graphics();
    rail.lineStyle(3, 0x1e2f4d, 1);
    rail.beginPath();
    rail.moveTo(area.x + 4, railY);
    rail.lineTo(area.x + area.width - 4, railY);
    rail.strokePath();

    // Bereits gespielter Teil des Strahls.
    const playedX = slotX(Math.min(state.turn, state.totalTurns));
    rail.lineStyle(3, 0x3b5680, 1);
    rail.beginPath();
    rail.moveTo(area.x + 4, railY);
    rail.lineTo(playedX, railY);
    rail.strokePath();
    this.root.add(rail);

    // Letzter Eintrag je Zug bestimmt das angezeigte Symbol.
    const entryByTurn = new Map<number, SchattenjagdPublicState["travelLog"][number]>();

    for (const entry of state.travelLog) {
      entryByTurn.set(entry.turn, entry);
    }

    const doubleTurns = new Set(
      state.travelLog.filter((entry) => entry.doubleMove).map((entry) => entry.turn)
    );

    const markers = this.scene.add.graphics();
    const symbolSize = Math.max(4, Math.min(7, slotWidth * 0.16));

    for (let turn = 1; turn <= state.totalTurns; turn += 1) {
      const centreX = slotX(turn);
      const isReveal = state.revealTurns.includes(turn);
      const isCurrent = turn === state.turn && state.outcome === null;
      const entry = entryByTurn.get(turn);
      const radius = isReveal ? 12 : 9;

      markers.fillStyle(0x0b1526, 1);
      markers.fillCircle(centreX, railY, radius);

      if (entry) {
        const color = entry.mode ? modeColors[entry.mode] : 0xf472b6;
        markers.fillStyle(color, 0.2);
        markers.fillCircle(centreX, railY, radius);
        markers.lineStyle(2, color, 0.95);
        markers.strokeCircle(centreX, railY, radius);
        if (entry.mode && !this.addModeTicket(entry.mode, centreX, railY, Math.max(20, symbolSize * 3.4))) {
          drawModeSymbol(markers, entry.mode, centreX, railY, symbolSize);
        } else if (!entry.mode) {
          drawModeSymbol(markers, null, centreX, railY, symbolSize);
        }
      } else {
        markers.lineStyle(isReveal ? 2 : 1.5, isReveal ? 0x8b5cf6 : 0x30405f, isReveal ? 0.9 : 0.8);
        markers.strokeCircle(centreX, railY, radius);
      }

      if (isCurrent) {
        markers.lineStyle(2.5, 0xfde68a, 0.95);
        markers.strokeCircle(centreX, railY, radius + 5);
      }

      if (doubleTurns.has(turn)) {
        const badge = this.scene.add
          .text(centreX + radius + 1, railY - radius - 2, "x2", {
            fontFamily: hostTheme.bodyFont,
            fontSize: "10px",
            color: hostTheme.accent
          })
          .setOrigin(0.5, 0.5);
        this.root.add(badge);
      }

      if (isReveal) {
        const revealedStationId = entry?.revealedStationId ?? null;

        const label = this.scene.add
          .text(
            centreX,
            railY - radius - 12,
            revealedStationId !== null ? `${revealedStationId}` : `${text.sightingLabel[0]}`,
            {
              fontFamily: hostTheme.titleFont,
              fontSize: revealedStationId !== null ? "14px" : "11px",
              color: revealedStationId !== null ? hostTheme.shadow : hostTheme.muted
            }
          )
          .setOrigin(0.5, 1);
        this.root.add(label);
      }

      if (isReveal || isCurrent || turn === 1 || turn === state.totalTurns) {
        const turnLabel = this.scene.add
          .text(centreX, railY + radius + 4, `${turn}`, {
            fontFamily: hostTheme.bodyFont,
            fontSize: "11px",
            color: isCurrent ? hostTheme.accent : hostTheme.muted
          })
          .setOrigin(0.5, 0);
        this.root.add(turnLabel);
      }
    }

    this.root.add(markers);
  }


  /** Animiert Ermittlerbewegungen entlang der Strecke und markiert neue Sichtungen. */
  private playMovementEffects(state: SchattenjagdPublicState, projection: Projection): void {
    const stationById = new Map(state.map.stations.map((station) => [station.id, station]));

    for (const detective of state.detectives) {
      const previousStationId = this.lastStations.get(detective.playerId);
      this.lastStations.set(detective.playerId, detective.stationId);

      if (previousStationId === undefined || previousStationId === detective.stationId) {
        continue;
      }

      const from = stationById.get(previousStationId);
      const to = stationById.get(detective.stationId);

      if (!from || !to) {
        continue;
      }

      const mode = detective.lastMode;
      const link = mode
        ? state.map.links.find(
            (candidate) =>
              candidate.mode === mode &&
              ((candidate.a === previousStationId && candidate.b === detective.stationId) ||
                (candidate.b === previousStationId && candidate.a === detective.stationId))
          )
        : undefined;
      const route = link && mode
        ? this.buildLinkPath(
            state,
            projection,
            stationById,
            link.a,
            link.b,
            mode,
            link.b === previousStationId
          )
        : [
            { x: projection.toScreenX(from.x), y: projection.toScreenY(from.y) },
            { x: projection.toScreenX(to.x), y: projection.toScreenY(to.y) }
          ];

      this.spawnTravelEffect(route, parseColor(detective.color), mode);
    }

    const sightingKey =
      state.shadow.lastRevealTurn !== null && state.shadow.lastKnownStationId !== null
        ? `${state.shadow.lastRevealTurn}:${state.shadow.lastKnownStationId}`
        : null;

    if (sightingKey && sightingKey !== this.lastShadowSighting) {
      this.lastShadowSighting = sightingKey;
      const station = stationById.get(state.shadow.lastKnownStationId as number);

      if (station) {
        this.spawnPing(
          projection.toScreenX(station.x),
          projection.toScreenY(station.y),
          0xf472b6
        );
      }
    }
  }

  private spawnTravelEffect(
    route: readonly ScreenPoint[],
    color: number,
    mode: TransportMode | null
  ): void {
    if (route.length < 2) {
      return;
    }

    const from = route[0];
    const to = route[route.length - 1];
    const trail = this.scene.add.graphics();
    trail.lineStyle(6, color, 0.85);
    this.strokePath(trail, route);
    this.fxLayer.add(trail);

    const routeLength = route.slice(1).reduce(
      (total, point, index) => total + Math.hypot(point.x - route[index].x, point.y - route[index].y),
      0
    );
    const pixelsPerSecond = mode
      ? schattenjagdConfig.travelAnimation.pixelsPerSecond[mode]
      : schattenjagdConfig.travelAnimation.pixelsPerSecond.taxi;
    const travelDuration = Phaser.Math.Clamp(
      (routeLength / pixelsPerSecond) * 1_000,
      schattenjagdConfig.travelAnimation.minMs,
      schattenjagdConfig.travelAnimation.maxMs
    );

    this.activeTweens.push(this.scene.tweens.add({
      targets: trail,
      alpha: { from: 0.85, to: 0 },
      delay: travelDuration * 0.62,
      duration: travelDuration * 0.48,
      ease: "Quad.easeOut",
      onComplete: () => trail.destroy()
    }));

    const textureKey = mode ? modeVehicleTextureKeys[mode] : null;
    const token = textureKey && this.scene.textures.exists(textureKey)
      ? this.scene.add
          .image(from.x, from.y, textureKey)
          .setDisplaySize(mode === "taxi" ? 48 : mode === "bus" ? 54 : 58, mode === "taxi" ? 48 : mode === "bus" ? 54 : 58)
          .setDepth(20)
      : this.scene.add.graphics().setPosition(from.x, from.y);

    if (token instanceof Phaser.GameObjects.Graphics) {
      token.fillStyle(color, 1);
      token.fillCircle(0, 0, 9);
      token.lineStyle(2.5, 0xf8fafc, 0.95);
      token.strokeCircle(0, 0, 9);
    }

    this.fxLayer.add(token);

    const path = new Phaser.Curves.Path(from.x, from.y);

    for (const point of route.slice(1)) {
      path.lineTo(point.x, point.y);
    }

    const progress = { value: 0 };
    let lastPoint = from;
    this.activeTweens.push(this.scene.tweens.add({
      targets: progress,
      value: 1,
      duration: travelDuration,
      ease: "Sine.easeInOut",
      onUpdate: () => {
        const point = path.getPoint(progress.value);
        token.setPosition(point.x, point.y);

        if (token instanceof Phaser.GameObjects.Image) {
          token.setRotation(Phaser.Math.Angle.Between(lastPoint.x, lastPoint.y, point.x, point.y) + Math.PI / 2);
        }

        lastPoint = point;
      },
      onComplete: () => {
        token.destroy();
        this.spawnPing(to.x, to.y, color);
      }
    }));
  }

  private spawnPing(x: number, y: number, color: number): void {
    const ping = this.scene.add.graphics();
    ping.lineStyle(3, color, 0.9);
    ping.strokeCircle(0, 0, 12);
    ping.setPosition(x, y);
    this.fxLayer.add(ping);

    this.scene.tweens.add({
      targets: ping,
      scale: { from: 0.5, to: 2.1 },
      alpha: { from: 0.9, to: 0 },
      duration: 620,
      ease: "Quad.easeOut",
      onComplete: () => ping.destroy()
    });
  }
}
