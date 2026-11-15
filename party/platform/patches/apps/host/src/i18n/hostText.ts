import {
  defaultLanguage,
  normalizeLanguage,
  type RoomLifecycle,
  type SupportedLanguage
} from "@open-party-lab/protocol";

const languagePreferenceKey = "open-party-lab.host-language";

export function readStoredHostLanguage(): SupportedLanguage {
  return defaultLanguage;
}

export function writeStoredHostLanguage(language: SupportedLanguage): void {
  if (typeof window === "undefined" || typeof window.localStorage === "undefined") {
    return;
  }
  window.localStorage.setItem(languagePreferenceKey, language);
}

export interface HostText {
  languageLabel: string;
  themeLabel: string;
  themeLight: string;
  themeDark: string;
  phoneController: string;
  showPhoneController: string;
  hide: string;
  fullscreen: string;
  exitFullscreen: string;
  fpsLabel: string;
  musicVolumeLabel: string;
  soundEffectsVolumeLabel: string;
  hostControlsTitle: string;
  hostControlsSubtitle: string;
  close: string;
  noGame: string;
  connected: string;
  waitingForRoom: string;
  offline: string;
  connectionMissing: string;
  players: string;
  playerRange: (min: number, max: number) => string;
  selected: string;
  lobby: string;
  connectedShort: string;
  noPlayersJoined: string;
  noPlayersConnected: string;
  roomListPending: string;
  moderationAllowed: string;
  moderationLocked: string;
  ready: string;
  waiting: string;
  reconnecting: string;
  notReady: string;
  kick: string;
  kickPlayer: (name: string) => string;
  roomCode: string;
  join: string;
  lobbyTitle: string;
  lobbyPlayersTitle: string;
  quickStartTitle: string;
  quickStartLines: string[];
  gameSelectionFallback: string;
  gameSelectRoundActiveSubtitle: string;
  gameSelectAutoReadySubtitle: string;
  gameSelectClassicSubtitle: string;
  playerStatusTitle: string;
  noActiveGameTitle: string;
  noActiveGameSelectLine: string;
  noActiveGameRoundActiveLine: string;
  noActiveGameStartLine: string;
  playersConnected: (count: number, max: number) => string;
  activeRoundLockedLine: string;
  setupChoicePendingLine: string;
  autoReadyLine: string;
  spaceStartLine: string;
  setupControlsLine: string;
  afterRoundSwitchLine: string;
  autoStartsWhenReadyLine: string;
  backToMenu: string;
  backToMenuShort: string;
  characterSelecting: string;
  errorLabel: string;
  gameLobbySetupTitle: string;
  gameLobbySubtitle: string;
  hostConnecting: string;
  hostCreatingRoom: string;
  hostPageHint: string;
  lifecycle: (phase: RoomLifecycle | string) => string;
  morePlayers: (count: number) => string;
  readyNextTitle: string;
  readyToStartTitle: string;
  readyVisibleLine: string;
  serverOffline: string;
  serverOnline: string;
  setupFollowsTitle: string;
  roomExpiryTitle: string;
  roomExpiryBody: (minutes: number) => string;
  roomExpiryExtend: string;
  roomExpiryExtending: string;
  roomExpiryExtended: string;
  roomExpiryCountdown: (time: string) => string;
  roundPausedTitle: string;
  roundPausedByPlayer: (name: string) => string;
  roundPausedByScreen: string;
  roundPausedHint: string;
  hostControlRequestTitle: string;
  hostControlRequestBody: (name: string) => string;
  hostControlRequestHint: string;
  hostControlAllow: string;
  hostControlDeny: string;
  hostControlDelegatedTitle: string;
  hostControlDelegatedBody: (name: string) => string;
  hostControlReclaim: string;
  shellKicker: string;
  shellTitle: string;
  shellSetupKicker: string;
  shellStartRound: string;
  shellRoundRunning: string;
  shellBackToCatalog: string;
  shellRosterEmpty: string;
  shellNeedsPlayers: (missing: number) => string;
  shellReadyCount: (ready: number, total: number) => string;
}

const spanishHostText: HostText = {
  shellKicker: "Elige un juego",
  shellTitle: "¿Qué jugamos?",
  shellSetupKicker: "Configuración",
  shellStartRound: "Empezar ronda",
  shellRoundRunning: "Ronda en curso",
  shellBackToCatalog: "Volver a juegos",
  shellRosterEmpty: "Todavía no hay nadie. Escanea el código para unirte.",
  shellNeedsPlayers: (missing: number) =>
    missing === 1 ? "Falta 1 jugador." : `Faltan ${missing} jugadores.`,
  shellReadyCount: (ready: number, total: number) => `${ready} de ${total} listos`,
  roomExpiryTitle: "La sala cerrará pronto",
  roomExpiryBody: (minutes: number) => `Esta sala se cerrará en unos ${minutes} minutos.`,
  roomExpiryExtend: "Extender 1 hora",
  roomExpiryExtending: "Extendiendo…",
  roomExpiryExtended: "Sala extendida una hora.",
  roomExpiryCountdown: (time: string) => `Esta sala se cerrará en ${time}.`,
  roundPausedTitle: "Ronda en pausa",
  roundPausedByPlayer: (name: string) => `${name} abrió el menú del anfitrión.`,
  roundPausedByScreen: "El menú del anfitrión está abierto.",
  roundPausedHint: "Continúa cuando se cierre el menú.",
  hostControlRequestTitle: "¿Tomar el control?",
  hostControlRequestBody: (name: string) => `${name} quiere controlar el host.`,
  hostControlRequestHint: "La selección, el inicio y la gestión pasan al celular.",
  hostControlAllow: "Permitir",
  hostControlDeny: "Rechazar",
  hostControlDelegatedTitle: "El celular controla",
  hostControlDelegatedBody: (name: string) => `${name} controla el host.`,
  hostControlReclaim: "Recuperar",
  languageLabel: "Idioma",
  themeLabel: "Tema",
  themeLight: "Claro",
  themeDark: "Oscuro",
  phoneController: "Control del celular",
  showPhoneController: "Mostrar control del celular",
  hide: "Ocultar",
  fullscreen: "Pantalla completa",
  exitFullscreen: "Salir de pantalla completa",
  fpsLabel: "FPS",
  musicVolumeLabel: "Música",
  soundEffectsVolumeLabel: "Efectos",
  hostControlsTitle: "Controles del anfitrión",
  hostControlsSubtitle: "Audio, imagen, idioma, jugadores",
  close: "Cerrar",
  noGame: "Sin juego",
  connected: "Conectado",
  waitingForRoom: "Esperando sala",
  offline: "Sin conexión",
  connectionMissing: "Falta conexión",
  players: "Jugadores",
  playerRange: (min: number, max: number) => `${min}-${max} jugadores`,
  selected: "Elegido",
  lobby: "Sala",
  connectedShort: "conectados",
  noPlayersJoined: "Todavía no se unió nadie.",
  noPlayersConnected: "Todavía no hay jugadores conectados.",
  roomListPending: "Cuando exista una sala, la lista aparecerá aquí.",
  moderationAllowed: "Puedes expulsar jugadores en la sala o entre rondas.",
  moderationLocked: "No se puede expulsar durante una ronda activa.",
  ready: "listo",
  waiting: "esperando",
  reconnecting: "reconectando",
  notReady: "no listo",
  kick: "Expulsar",
  kickPlayer: (name: string) => `Expulsar a ${name}`,
  roomCode: "CÓDIGO",
  roomPrefix: "SALA",
  scanQr: "Escanea el código QR o abre el enlace en tu celular",
  join: "Unirse",
  lobbyTitle: "Sala de fiesta",
  lobbyPlayersTitle: "Jugadores en la sala",
  quickStartTitle: "Inicio rápido",
  quickStartLines: [
    "Haz clic o usa un número para elegir juego.",
    "Después inicia solo o con ESPACIO."
  ],
  gameSelectionFallback: "Elegir juego",
  playerStatusTitle: "Estado de jugadores",
  noActiveGameTitle: "Todavía no hay juego activo",
  noActiveGameSelectLine: "Elige una tarjeta de juego arriba.",
  noActiveGameRoundActiveLine:
    "La ronda actual sigue. Cuando termine, podrás elegir otro juego aquí.",
  noActiveGameStartLine:
    "Después, la ronda inicia sola con el listo de los celulares o con ESPACIO.",
  playersConnected: (count: number, max: number) => `Jugadores conectados: ${count}/${max}`,
  activeRoundLockedLine:
    "Ronda activa. La selección sigue visible, pero bloqueada hasta que termine.",
  setupChoicePendingLine: "Todos deben elegir su configuración primero.",
  autoReadyLine: "Todos deben marcarse listos en el celular.",
  spaceStartLine: "ESPACIO inicia la ronda cuando haya suficientes jugadores.",
  setupControlsLine: "Este juego tiene opciones de configuración aquí.",
  afterRoundSwitchLine: "Cuando termine la ronda, podrás cambiar de juego.",
  autoStartsWhenReadyLine: "En cuanto todos estén listos, la ronda empieza sola.",
  backToMenu: "Volver al menú principal",
  backToMenuShort: "Menú",
  characterSelecting: "Eligiendo personaje",
  errorLabel: "Error",
  gameLobbySetupTitle: "Configuración de la ronda",
  gameLobbySubtitle: "Configura la ronda y empieza junta.",
  gameSelectAutoReadySubtitle: "Cambia de juego con el mouse o los números. Las rondas por listo empiezan solas.",
  gameSelectClassicSubtitle: "Cambia de juego con el mouse o los números. Las rondas clásicas empiezan con ESPACIO.",
  gameSelectRoundActiveSubtitle: "La selección de juego está abierta. Mientras haya ronda activa, elegir e iniciar quedan bloqueados.",
  hostConnecting: "Conectando el host con el servidor…",
  hostCreatingRoom: "Creando la sala…",
  hostPageHint: "En el celular usa siempre el enlace o el código QR que se muestra.",
  lifecycle: (phase: any) => {
    switch (phase) {
      case "lobby": return "Sala";
      case "game_selected": return "Juego elegido";
      case "round_intro": return "Intro";
      case "countdown": return "Cuenta regresiva";
      case "playing": return "Jugando";
      case "locked": return "Bloqueado";
      case "result": return "Resultado";
      case "scoreboard": return "Puntos";
      case "finished": return "Terminado";
      default: return phase;
    }
  },
  morePlayers: (count: number) => `+${count} jugadores más`,
  readyNextTitle: "Listos para la siguiente ronda",
  readyToStartTitle: "Listos para empezar",
  readyVisibleLine: "El estado de listo e inicio sigue visible aquí.",
  serverOffline: "Servidor desconectado",
  serverOnline: "Servidor conectado",
  setupFollowsTitle: "Sigue la configuración",
};

export const hostText: HostText = spanishHostText;

export function getHostText(language: SupportedLanguage | null | undefined): HostText {
  return hostText;
}
PY