/**
 * Reglas cortas en español para la pantalla de sala de Vision+ Party.
 *
 * La plataforma trae reglas por opción en varios idiomas, pero algunas
 * traducciones heredadas son demasiado literales. Esta es la explicación
 * curada que ve la tele antes de empezar: una sola pantalla, lenguaje directo
 * y los controles que de verdad se usan con el celular o la iPad.
 */
export const PARTY_RULES: Record<string, readonly string[]> = {
  banterbolt: [
    "Cada persona recibe dos prompts secretos y escribe una respuesta para cada uno.",
    "Las respuestas se mezclan y se votan a ciegas; no puedes votar por la tuya.",
    "Cada voto vale 100 puntos. Si todos votan por la misma respuesta, cae un ¡ZAP! de +50.",
    "La Ronda Relámpago cierra la partida: todos contestan el mismo prompt y votan por la mejor."
  ],
  "you-got-it": [
    "Dos equipos: Bajos y Agudos. En cada turno, una persona es el Oráculo.",
    "El Oráculo ve un objetivo secreto en el espectro y da una pista corta.",
    "Su equipo mueve el dial para acercarse. Mientras más cerca, más puntos.",
    "Si el otro equipo adivina el lado del error, puede hacer Undercut y robar un punto."
  ],
  merkissioner: [
    "La mayoría son Merkizens; una minoría son Merkites y una persona es El Comisionado Merk.",
    "Cada ronda se nomina a un comisionado y todos votan SÍ o NO en secreto.",
    "Si el gobierno pasa, se aprueba un decreto; los decretos Merkite desbloquean poderes.",
    "Los Merkizens ganan por decretos o expulsando al jefe; los Merkites, por decretos o eligiéndolo comisionado."
  ],
  pantomime: [
    "Una persona es el actor y ve un término secreto; el resto adivina.",
    "Solo se permiten gestos: nada de palabras, sonidos ni letras.",
    "Cada acierto suma. Puedes saltar un término, pero los saltos son limitados.",
    "Al terminar el tiempo se revisan los aciertos y se pasa al siguiente actor."
  ],
  "zeichnen-und-erraten": [
    "Una persona dibuja el término secreto en su celular.",
    "El resto mira la tele y escribe sus respuestas en el control.",
    "El primer acierto válido se lleva el punto de la ronda.",
    "Al acabar el tiempo se revela la palabra y se pasa al siguiente dibujante."
  ],
  schaetzorama: [
    "La tele muestra una pregunta de estimación, orden o clasificación.",
    "Cada persona responde desde su celular con números, orden o categorías.",
    "Mientras más cerca estés de la respuesta correcta, más puntos ganas.",
    "La tabla se actualiza al revelar y la siguiente pregunta empieza cuando todos están listos."
  ],
  imposter: [
    "Todos reciben la misma palabra secreta menos una persona: el Infiltrado.",
    "Por turnos dan una pista relacionada sin decir la palabra.",
    "Después discuten y votan en secreto por quién creen que es el Infiltrado.",
    "Si lo descubren, el Infiltrado tiene una última oportunidad para adivinar la palabra."
  ],
  buzzwort: [
    "Una persona explica el término de la tarjeta sin decir las palabras malditas.",
    "El resto escucha y presiona el timbre cuando cree que puede responder.",
    "Cada acierto suma; si dices una palabra maldita, pierdes la tarjeta.",
    "El guardián de la ronda vigila las trampas y el marcador se actualiza al instante."
  ],
  schattenjagd: [
    "Una persona es la Sombra y se mueve en secreto por la ciudad.",
    "El resto son detectives y cooperan para acorralarla.",
    "La Sombra revela el transporte que usa, no la estación exacta.",
    "Los detectives ganan si la atrapan antes de que se acaben los turnos de escape."
  ],
  flatterfluff: [
    "Apunta con tu celular y dispara a los malvaviscos alados.",
    "Cada color de mira pertenece a una persona; compitan por el marcador.",
    "Recarga cuando se acabe la munición y recoge cajas para no quedarte seco.",
    "Al acabar el tiempo gana quien tenga más puntos."
  ],
  "magic-arena": [
    "Cada persona controla una figura en una arena de hexágonos.",
    "Planeen sus movimientos y cartas en secreto y luego se resuelven juntos.",
    "Los hechizos dañan figuras, rompen terreno o mueven a los rivales.",
    "La arena se cierra; sobrevive y elimina a las figuras rivales para ganar."
  ],
  "card-table": [
    "La mesa es compartida; tu mano privada vive en el celular.",
    "Elige un juego de cartas y la mesa reparte las cartas automáticamente.",
    "Juega, roba o pasa desde tu control; la tele muestra la mesa y los turnos.",
    "Cada juego tiene su propio objetivo y su marcador aparece al terminar la mano."
  ],
  "dungeon-party": [
    "Todos cooperan para superar habitaciones, monstruos y el jefe final.",
    "Cada héroe elige una clase y una acción secreta en su celular.",
    "Las cartas de equipo pueden ayudar, meter cizaña o proteger a alguien.",
    "El sótano se gana en equipo, pero la fama y el botín tienen un ganador individual."
  ]
};

export function partyRulesFor(gameId: string): readonly string[] {
  return PARTY_RULES[gameId] ?? [];
}
