/**
 * Guiones de la narradora de Vision+ para Banterbolt.
 *
 * Las líneas están pensadas para Qwen3-TTS (voz de Dalia) y para el tono
 * mexicano irreverente de Vision+ Party. El host muestra el texto y, si existe
 * el audio en `public/host/banterbolt/narrator/<id>.m4a`, lo reproduce.
 */
export const NARRATOR_LINES = {
  welcome: [
    "¡Bienvenidos a Banterbolt! Aquí se viene a escribir tonterías y a votar sin piedad.",
    "¡Ya llegó la banda! Saquen el celular, que esto se va a poner bueno.",
    "Banterbolt: el único lugar donde escribir mal te puede hacer ganar.",
  ],
  write: [
    "Escriban su respuesta. Si es mala, mejor; si es buena, sospechosa.",
    "Tienen su prompt. No piensen tanto, que se enfría el chiste.",
    "Saquen la pluma digital. La gracia no se va a escribir sola.",
  ],
  vote: [
    "Voten por la respuesta que más les hizo reír. No se vale votar por la propia.",
    "A ver, ¿cuál de estas dos joyas se lleva su voto?",
    "Voten con el corazón, pero sin ponerse de acuerdo.",
  ],
  reveal: [
    "A ver, ¿quién escribió esta joya?",
    "Momento de la verdad: destapen a los autores.",
    "Y el genio detrás de esto es…",
  ],
  zap: [
    "¡ZAP! Barrida total. Puntos extra y humillación pública.",
    "¡ZAP! Les gustó a todos. Qué miedo.",
    "Barrida unánime. Alguien escribió algo demasiado bueno.",
  ],
  jinx: [
    "¡Jinx! Escribieron lo mismo. Cero puntos y un abrazo.",
    "Se copiaron mentalmente. Qué vergüenza ajena.",
    "¡Jinx! Dos genios, una sola idea. No hay puntos.",
  ],
  scoreboard: [
    "Van los puntos. No se peleen, todavía.",
    "Así va la tabla. Todavía hay tiempo de remontar.",
    "Puntos parciales. El que se confíe pierde.",
  ],
  finale: [
    "Ronda relámpago: todos responden lo mismo. Aquí se define al campeón.",
    "Última ronda. Todo o nada. Saquen su mejor tontería.",
    "Se acabó el calentamiento: viene la ronda relámpago.",
  ],
  podium: [
    "¡Tenemos ganador! Que vaya por los tacos.",
    "¡Fin del juego! El campeón se lleva la gloria y la responsabilidad de invitar.",
    "¡Se acabó! Ya pueden presumir en el grupo de WhatsApp.",
  ],
  idle: [
    "¿Ya están pensando o se quedaron viendo el techo?",
    "El reloj corre y las ideas no llegan. Clásico.",
    "Concéntrense, que la ronda no se va a ganar sola.",
  ],
} as const;

export type NarratorMoment = keyof typeof NARRATOR_LINES;

export function narratorLine(moment: NarratorMoment, seed = 0): string {
  const lines = NARRATOR_LINES[moment];
  return lines[Math.abs(seed) % lines.length] ?? lines[0] ?? "";
}
