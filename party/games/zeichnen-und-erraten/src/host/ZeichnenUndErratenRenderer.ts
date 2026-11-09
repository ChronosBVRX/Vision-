import Phaser from "phaser";
import { tokens, tokenColor } from "./platformTheme.js";

type SupportedLanguage = "de" | "en";

/**
 * This screen is chrome, not artwork.
 *
 * There is no painted background here — a drawing board and a guess
 * list, all of them surfaces. They used to be fixed dark slate while the text
 * on top already read the room's tokens, which in the light theme produced dark
 * ink on a dark panel. Surfaces and ink now come from the same place, so the
 * whole screen follows the room.
 */
const hostTheme = {
  titleFont: "Arial, Helvetica, sans-serif",
  bodyFont: "Arial, Helvetica, sans-serif"
} as const;

interface DrawingPoint {
  x: number;
  y: number;
}

interface DrawingStroke {
  id: string;
  color: string;
  points: DrawingPoint[];
}

interface GuessEntry {
  playerName: string;
  guess: string;
  correct: boolean;
}

export interface ZeichnenUndErratenRenderState {
  gameScores?: Array<{ playerId: string; playerName: string; total: number; delta: number }>;
  drawerName?: string;
  maskedWord?: string;
  finishAt?: number | null;
  canvasAspectRatio?: number;
  revealedWord?: string;
  strokes?: DrawingStroke[];
  guesses?: GuessEntry[];
  winnerName?: string;
}

/**
 * A stroke without a colour falls back to the board's own ink, so an older
 * stroke recorded before a theme switch still contrasts with the board.
 */
function parseStrokeColor(color: string | undefined): number {
  if (!color) {
    return tokenColor((theme) => theme.color.text);
  }

  return Phaser.Display.Color.HexStringToColor(color).color;
}

export function renderZeichnenUndErratenState(
  scene: Phaser.Scene,
  state: ZeichnenUndErratenRenderState,
  _message: string,
  language?: SupportedLanguage
): void {
  const en = language === "en";
  const width = scene.scale.width;
  const height = scene.scale.height;
  const pad = 16;
  const gap = 16;
  const headerHeight = 54;
  const reveal = state.revealedWord !== undefined;
  const panelWidth = reveal ? Math.min(520, width * 0.4) : Math.min(324, width * 0.25);
  const availableWidth = Math.max(1, width - pad * 2 - gap - panelWidth);
  const boardAspect = Math.max(0.4, Math.min(2.5, state.canvasAspectRatio ?? 4 / 3));
  const availableHeight = Math.max(1, height - pad * 2 - headerHeight - gap);
  const boardWidth = Math.min(availableWidth, availableHeight * boardAspect);
  const boardHeight = boardWidth / boardAspect;
  const boardX = pad + (availableWidth - boardWidth) / 2;
  const boardY = pad + headerHeight + gap;
  const surface = tokenColor((theme) => theme.color.surface);
  const line = tokenColor((theme) => theme.color.lineStrong);

  // The board is the canvas people draw on, so it takes the room's brightest
  // surface: paper in the light theme, the raised slate in the dark one.
  scene.add
    .rectangle(
      boardX + boardWidth / 2,
      boardY + boardHeight / 2,
      boardWidth,
      boardHeight,
      tokenColor((theme) => theme.color.surfaceRaised),
      1
    )
    .setStrokeStyle(3, line, 0.9);

  const graphics = scene.add.graphics();

  for (const stroke of state.strokes ?? []) {
    const color = parseStrokeColor(stroke.color);

    if (stroke.points.length < 2) {
      const p = stroke.points[0];
      if (p) {
        graphics.fillStyle(color, 1);
        graphics.fillCircle(boardX + p.x * boardWidth, boardY + p.y * boardHeight, boardWidth * 0.004);
      }
      continue;
    }

    graphics.lineStyle(boardWidth * 0.008, color, 1);
    graphics.beginPath();
    stroke.points.forEach((point, index) => {
      const x = boardX + point.x * boardWidth;
      const y = boardY + point.y * boardHeight;
      if (index === 0) {
        graphics.moveTo(x, y);
      } else {
        graphics.lineTo(x, y);
      }
    });
    graphics.strokePath();
  }

  const panelX = width - pad - panelWidth;
  const panelHeight = reveal ? availableHeight : boardHeight;
  const guessCount = Math.max(1, Math.min(9, Math.floor((boardHeight - 62) / 58)));
  const guesses = (state.guesses ?? []).slice(-guessCount).reverse();

  const remainingSeconds = state.finishAt === null || state.finishAt === undefined
    ? null
    : Math.max(0, Math.ceil((state.finishAt - Date.now()) / 1_000));
  const timerText = remainingSeconds === null
    ? null
    : `${Math.floor(remainingSeconds / 60)}:${String(remainingSeconds % 60).padStart(2, "0")}`;
  scene.add.text(pad, pad + 6, `${en ? "Drawing" : "Dibujando"}: ${state.drawerName ?? "-"}`, {
    fontFamily: hostTheme.titleFont,
    fontSize: "28px",
    color: tokens().color.text
  });
  scene.add.text(width * 0.53, pad + 8, reveal ? (en ? "Round over" : "redondear") : `${en ? "Word" : "palabra"}: ${state.maskedWord ?? ""}`, {
    fontFamily: hostTheme.bodyFont,
    fontSize: "22px",
    color: tokens().color.textSoft
  });
  if (!reveal && timerText !== null) {
    scene.add.text(width - pad - 150, pad + 2, timerText, {
      fontFamily: hostTheme.titleFont,
      fontSize: "38px",
      color: remainingSeconds !== null && remainingSeconds <= 10 ? tokens().color.danger : tokens().color.text,
      align: "right",
      fixedWidth: 150
    });
  }

  scene.add
    .rectangle(panelX + panelWidth / 2, boardY + panelHeight / 2, panelWidth, panelHeight, surface, 0.92)
    .setStrokeStyle(2, line, 0.9);

  if (reveal) {
    scene.add.text(panelX + 16, boardY + 12, en ? "LA PALABRA ERA" : "LA PALABRA ERA", {
      fontFamily: hostTheme.bodyFont, fontSize: "14px", color: tokens().color.textSoft
    });
    const word = scene.add.text(panelX + 16, boardY + 36, state.revealedWord ?? "", {
      fontFamily: hostTheme.titleFont, fontSize: "36px", fontStyle: "bold", color: tokens().color.success,
      wordWrap: { width: panelWidth - 32 }
    });
    let wordFontSize = 36;
    while ((word.height > 84 || word.width > panelWidth - 32) && wordFontSize > 20) {
      wordFontSize -= 2;
      word.setFontSize(wordFontSize);
    }
    scene.add.text(panelX + 16, boardY + 132, en ? "Draw & Guess · Points" : "Dibujar y adivinar · Puntos", {
      fontFamily: hostTheme.bodyFont, fontSize: "18px", color: tokens().color.text
    });
    const scores = [...(state.gameScores ?? [])].sort((a, b) => b.total - a.total);
    const columns = scores.length > 10 ? 2 : 1;
    const rows = Math.max(1, Math.ceil(scores.length / columns));
    const columnGap = columns === 2 ? 18 : 0;
    const columnWidth = (panelWidth - 32 - columnGap) / columns;
    // Leave the lower corner clear for the platform's fullscreen/host controls.
    const rowHeight = Math.min(44, (panelHeight - 286) / rows);
    const fontSize = Math.max(12, Math.min(21, rowHeight - 10));
    scores.forEach((entry, index) => {
      const x = panelX + 16 + Math.floor(index / rows) * (columnWidth + columnGap);
      const y = boardY + 166 + (index % rows) * rowHeight;
      scene.add.text(x, y, entry.playerName, {
        fontFamily: hostTheme.bodyFont, fontSize: `${fontSize}px`,
        color: entry.delta > 0 ? tokens().color.success : tokens().color.text,
        wordWrap: { width: columnWidth - 88 }, maxLines: 1
      });
      scene.add.text(x + columnWidth - 80, y, `${entry.total}${entry.delta > 0 ? ` (+${entry.delta})` : ""}`, {
        fontFamily: hostTheme.titleFont, fontSize: `${Math.max(12, fontSize - 2)}px`, color: tokens().color.text,
        fixedWidth: 76, align: "right"
      });
    });
    return;
  }

  scene.add.text(panelX + 14, boardY + 12, en ? "Guesses" : "Consejos", {
    fontFamily: hostTheme.titleFont,
    fontSize: "26px",
    color: tokens().color.text
  });

  guesses.forEach((entry, index) => {
    scene.add.text(panelX + 14, boardY + 50 + index * 58, `${entry.playerName}: ${entry.guess}`, {
      fontFamily: hostTheme.bodyFont,
      fontSize: "21px",
      wordWrap: { width: panelWidth - 28 },
      maxLines: 2,
      color: entry.correct ? tokens().color.success : tokens().color.textSoft
    });
  });

}
