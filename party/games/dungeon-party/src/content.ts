import type { DungeonCampAction, DungeonCard, DungeonEncounter, DungeonRoomRule } from "./protocol.js";

type Text = { de: string; en: string };
const text = (de: string, _en: string): Text => ({ de, en: de });
type CardTemplate = Omit<DungeonCard, "name" | "description"> & { name: Text; description: Text };
const gear = (id: string, name: Text, description: Text, attack = 1): CardTemplate => ({ id, name, description, attack, kind: "equipment" });
const spell = (id: string, name: Text, description: Text, effect: DungeonCard["effect"], extra: Partial<CardTemplate> = {}): CardTemplate => ({ id, name, description, attack: 0, kind: "effect", effect, ...extra });

const cards: CardTemplate[] = [
  gear("tin-crown", text("Corona de hojalata del mariscal de campo", "Tin field marshal's crown"), text("+1 poder de combate. Parece más heroico desde atrás.", "+1 power. Looks more heroic from behind.")),
  gear("soup-spoon", text("Cuchara de madera sospechosamente grande", "Suspiciously large wooden spoon"), text("+1 poder de combate. Cada oponente se parece un poco a la sopa.", "+1 power. Every enemy looks a little like soup.")),
  gear("boots", text("Botas de retirada táctica", "Boots of tactical retreat"), text("+1 de poder. Sorprendentemente silenciosas al huir.", "+1 power. Surprisingly quiet when running away.")),
  gear("ring", text("Anillo de invisibilidad casi total", "Ring of almost total invisibility"), text("+1 poder de combate. El grupo todavía te ignora.", "+1 power. The party still overlooks you.")),
  spell("petty-intrigue", text("Intriga de vanidad", "Intrigue of vanity"), text("Roba hasta 1 Gloria. Poder del grupo −2. Si pierdes, pierdes 2 glorias.", "Steal up to 1 fame. Party power −2. Lose 2 fame if the party fails."), "intrigue", { sabotagePenalty: 2 }),
  spell("fake-bill", text("Factura falsa", "Fake invoice"), text("Roba hasta 2 de oro de otra persona.", "Steal up to 2 gold from another hero."), "false_bill"),
  spell("rallying-song", text("Canción heroica con tonos falsos.", "Off-key heroic anthem"), text("+4 poder de grupo y +1 fama para ti.", "+4 party power and +1 fame for you."), "rally"),
  spell("padded-coat", text("Abrigo hecho con cortinas prestadas.", "Coat of borrowed curtains"), text("Proteger a una persona: 1 daño menos si es derrotado.", "Protect a hero: take 1 less damage on failure."), "ward"),
  spell("loose-floorboard", text("Tabla floja", "Loose floorboard"), text("Objetivo: −3 potencia. Si el grupo tiene éxito, +1 gloria para ti, si pierdes, −2.", "Target: −3 power. Gain 1 fame on party success; lose 2 on failure."), "jam", { sabotagePenalty: 2, sabotageReward: 1 }),
  spell("extra-paperwork", text("Papeleo adicional", "Additional paperwork"), text("Valor objetivo +5. Si el grupo tiene éxito, +2 de gloria para ti, si pierdes, −2.", "Target +5. Gain 2 fame on party success; lose 2 on failure."), "reinforce", { sabotagePenalty: 2, sabotageReward: 2 }),
  spell("healing-soup", text("Sopa de emergencia de la abuela", "Grandma's emergency soup"), text("Una persona recupera 2 vidas antes del combate. 8 vidas como máximo.", "Restore 2 health to a hero before combat, up to 8."), "heal"),
  spell("pocket-cleric", text("Santo de bolsillo", "Pocket saint"), text("Una persona recupera 2 vidas antes del combate. Incluso a ti mismo.", "Restore 2 health to a hero before combat. You may target yourself."), "heal"),
  spell("sleepy-tea", text("Té de sueño para monstruos", "Monster bedtime tea"), text("Reduce el valor objetivo de esta habitación en 4.", "Reduce this room's target by 4."), "weaken"),
  spell("union-break", text("Descanso sindical de monstruos", "Union-mandated monster break"), text("Reduce el valor objetivo de esta habitación en 4.", "Reduce this room's target by 4."), "weaken"),
  spell("lucky-sock", text("Calcetín de la suerte, sin lavar", "Lucky sock, unwashed"), text("Un objetivo vuelve a tirar una vez con un 1-3 y se queda con la mejor tirada.", "Un héroe que obtenga 1-3 vuelve a tirar una vez y se queda con el mejor dado."), "fortune"),
  spell("loaded-hope", text("Felicidad oficialmente sancionada", "Officially licensed luck"), text("Un objetivo vuelve a tirar una vez con un 1-3 y se queda con la mejor tirada.", "Un héroe que obtenga 1-3 vuelve a tirar una vez y se queda con el mejor dado."), "fortune"),
  spell("table-shield", text("La mesa tiene que venir contigo.", "Bring the whole table"), text("Si son derrotados, todos reciben 1 daño menos. La protección no se acumula.", "On failure everyone takes 1 less damage. Protection does not stack."), "barrier"),
  spell("bubble-wrap", text("Plástico burbuja mágico", "Enchanted bubble wrap"), text("Si son derrotados, todos reciben 1 daño menos. La protección no se acumula.", "On failure everyone takes 1 less damage. Protection does not stack."), "barrier"),
  gear("pan", text("Sartén de convicción", "Frying pan of persuasion"), text("+2 poder de combate. Un argumento convincente.", "+2 power. A compelling argument."), 2),
  gear("lantern", text("Linterna de trampas obvias", "Lantern of obvious traps"), text("+2 poder de combate. Al menos ahora puedes ver lo que te golpea.", "+2 power. At least you can see what hits you."), 2)
];

export function cardCatalog(language: "de" | "en"): DungeonCard[] {
  return cards.map((card) => ({ ...card, name: card.name[language === "en" ? "en" : "de"], description: card.description[language === "en" ? "en" : "de"] }));
}

export const campActions: Record<DungeonCampAction, { name: Text; description: Text; symbol: string; cost: number }> = {
  rest: { name: text("Descansa junto al fuego", "Descansa junto al fuego"), description: text("Gratis · +3 de vida, máximo 8.", "Gratis · restaurar 3 de salud, hasta 8."), symbol: "♥", cost: 0 },
  stock: { name: text("Reabastecer tarjetas", "Reabastecer tarjetas"), description: text("2 de oro · Roba 2 cartas de acción.", "2 de oro · roba 2 cartas de acción."), symbol: "▱", cost: 2 },
  upgrade: { name: text("Mejorar el equipo", "Actualizar equipo"), description: text("3 de oro · Permanente +1 poder de combate.", "3 permanentes de oro ganan +1 de poder."), symbol: "⚒", cost: 3 },
  scout: { name: text("Explora el camino", "Explorar adelante"), description: text("Gratis · +2 potencia de grupo en la habitación de al lado. Se suman varios exploradores.", "Gratis · +2 party power en la habitación contigua. Se apilan varios exploradores."), symbol: "⌖", cost: 0 }
};

const rules: Array<Omit<DungeonRoomRule, "name" | "description"> & { name: Text; description: Text }> = [
  { id: "frontline", name: text("Flanco abierto", "Flanco abierto"), description: text("Pelear e interceptar otorgan +2 de poder por persona.", "Luchar y Guardia añaden +2 de poder por héroe."), symbol: "⚔" },
  { id: "echo", name: text("Salón de los ecos", "cámara de eco"), description: text("La Oración de Ayuda y Sanación otorga +3 de poder por persona.", "La oración de ayuda y curación añade +3 de poder por héroe."), symbol: "♫" },
  { id: "arcane", name: text("Tormenta mágica", "tormenta arcana"), description: text("Cada acción de clase proporciona +2 de poder.", "Cada demanda colectiva añade +2 de poder."), symbol: "✦" },
  { id: "riches", name: text("Fiebre del oro", "Fiebre del oro"), description: text("Cualquiera que consiga el botín recibirá 2 de oro adicionales si tiene éxito.", "El saqueo otorga 2 de oro extra si tiene éxito."), symbol: "¤" },
  { id: "danger", name: text("Emboscada", "Emboscada"), description: text("Derrota: todos +1 de daño. Éxito: todos +1 fama.", "Fallo: todos reciben +1 de daño. Éxito: todos ganan +1 de fama."), symbol: "⚡" }
];
export function roomRules(language: "de" | "en"): DungeonRoomRule[] {
  return rules.map((rule) => ({ ...rule, name: rule.name[language === "en" ? "en" : "de"], description: rule.description[language === "en" ? "en" : "de"] }));
}

type Room = { id: string; name: Text; flavor: Text; kind: DungeonEncounter["kind"]; difficulty: number; artKey: string };
export const roomPools: Record<"early" | "middle" | "late", Room[]> = {
  early: [
    { id: "toll-troll", name: text("El troll con cargos por servicio.", "El troll con cargos por servicio."), flavor: text("Exige peaje, con carácter retroactivo y en efectivo.", "Quiere su peaje retroactivamente, en efectivo."), kind: "battle", difficulty: 13, artKey: "toll-troll" },
    { id: "bridge", name: text("El puente con miedo a las alturas", "El puente tiene miedo a las alturas."), flavor: text("Las tablas crujen. El puente también.", "Los tablones crujen. Aquí no pertenece al puente."), kind: "challenge", difficulty: 15, artKey: "bridge" },
    { id: "mimic", name: text("El pecho con mal aliento.", "El pecho con mal aliento."), flavor: text("Promete un tesoro delicioso.", "Promete un tesoro delicioso."), kind: "battle", difficulty: 14, artKey: "mimic" },
    { id: "mushrooms", name: text("El conjunto de hongos", "El conjunto de hongos"), flavor: text("Nadie se marcha hasta que se apruebe el protocolo.", "Nadie se marcha hasta que se apruebe el acta."), kind: "event", difficulty: 14, artKey: "mushrooms" },
    { id: "slime", name: text("Budín posesivo", "Budín con derechos de propiedad"), flavor: text("Estás parado en su propiedad. Y en su postre.", "Estás en su propiedad. Y en su postre."), kind: "battle", difficulty: 15, artKey: "slime" }
  ],
  middle: [
    { id: "vault", name: text("Bóveda de malas ideas", "Bóveda de malas ideas"), flavor: text("Un cofre, tres cerraduras y una palanca sospechosa.", "Un cofre, tres cerraduras y una palanca sospechosa."), kind: "challenge", difficulty: 18, artKey: "vault" },
    { id: "skeletons", name: text("La orquesta esqueleto", "La orquesta esqueleto"), flavor: text("Solo tocan marchas fúnebres y aceptan pedidos.", "Sólo tocan marchas fúnebres. Se aceptan solicitudes."), kind: "battle", difficulty: 17, artKey: "skeletons" },
    { id: "gargoyle", name: text("Gárgola en huelga", "Gárgola en huelga"), flavor: text("No deja pasar a nadie sin paga por condiciones peligrosas.", "Sin paga por condiciones de vida peligrosas, sin pasaje."), kind: "battle", difficulty: 18, artKey: "gargoyle" },
    { id: "clockwork", name: text("El autómata cuidador", "El autómata cuidador"), flavor: text("Seguirás en el calabozo después de la hora de cierre.", "Todavía estás en el calabozo después de la hora de cierre."), kind: "battle", difficulty: 19, artKey: "clockwork" },
    { id: "mirror", name: text("El espejo del exceso de confianza", "Espejo de exceso de confianza"), flavor: text("Tus reflejos juran que lo harían mejor.", "Tus reflexiones insisten en que podrían hacerlo mejor."), kind: "challenge", difficulty: 18, artKey: "mirror" }
  ],
  late: [
    { id: "lost-treasury", name: text("Hacienda sin salida", "Hacienda sin salida"), flavor: text("El tesoro es real. Las señales no.", "El tesoro es real. Las señales no lo son."), kind: "challenge", difficulty: 20, artKey: "lost-treasury" },
    { id: "royal-mimic", name: text("La caja de mordida real", "El tesoro real que muerde"), flavor: text("El tesoro ahora también exige su parte.", "Incluso el tesoro exige su parte."), kind: "battle", difficulty: 21, artKey: "mimic" },
    { id: "grand-clockwork", name: text("El último control de seguridad", "El último control de seguridad"), flavor: text("Coloca todos los elementos mágicos en la banda.", "Todos los Coloca tienen objetos mágicos en el transportador."), kind: "challenge", difficulty: 20, artKey: "clockwork" }
  ]
};

export function localizedRoom(room: Room, language: "de" | "en", scale: number): DungeonEncounter {
  return { id: room.id, name: room.name[language === "en" ? "en" : "de"], flavor: room.flavor[language === "en" ? "en" : "de"], artKey: room.artKey, kind: room.kind, difficulty: room.difficulty + scale, health: room.difficulty + scale, maxHealth: room.difficulty + scale, cleared: false };
}

export const routePools: Room[][] = [[
  { id: "merchant", name: text("Distribuidor con los mejores precios.", "Mercader de siete precios"), flavor: text("Si tiene éxito: todos +1 de oro.", "En caso de éxito: todos ganan 1 oro."), kind: "event", difficulty: 14, artKey: "merchant" },
  { id: "crypt", name: text("Cripta de los ancestros ruidosos", "Cripta de los ancestros ruidosos"), flavor: text("Es significativo: el aporte es superior a +2 fama. Los empates comparten el bono.", "En el caso original: los solicitantes tienen más de 2 de fama cada uno, incluidos los empates."), kind: "battle", difficulty: 16, artKey: "crypt" },
  { id: "library", name: text("Biblioteca de libros ruidosos", "Biblioteca de libros ruidosos"), flavor: text("Si tiene éxito: Ayuda y oración de curación otorgan +2 de fama.", "En caso de éxito: la oración de ayuda y curación gana 2 famas adicionales."), kind: "challenge", difficulty: 15, artKey: "mirror" },
  { id: "well", name: text("Fuente de buenas excusas", "Bien de buenas excusas"), flavor: text("Si tiene éxito: todos +1 de vida, máximo 8.", "En caso de éxito: todos recuperan 1 de salud, hasta 8."), kind: "challenge", difficulty: 15, artKey: "shrine" }
], [
  { id: "shrine", name: text("Santuario de las Segundas Oportunidades", "Santuario de las segundas oportunidades"), flavor: text("Si tiene éxito: todos +1 de vida, máximo 8.", "En caso de éxito: todos recuperan 1 de salud, hasta 8."), kind: "challenge", difficulty: 16, artKey: "shrine" },
  { id: "cursed-armory", name: text("Armería con vida propia", "Armería con vida propia"), flavor: text("Si tiene éxito: Secure Loot roba una segunda carta.", "Si tiene éxito: Loot roba una segunda carta extra."), kind: "battle", difficulty: 19, artKey: "cursed-armory" },
  { id: "forge", name: text("Forja de ofertas calientes", "Forja de ofertas especiales"), flavor: text("Si tiene éxito: todos reciben un poder de combate permanente de +1.", "En caso de éxito: todos obtienen permanentemente +1 de poder."), kind: "challenge", difficulty: 20, artKey: "clockwork" },
  { id: "arena", name: text("Arena de malos perdedores", "Arena de malos perdedores"), flavor: text("Si tiene éxito: todos +2 a la fama.", "En caso de éxito: todos ganan 2 de fama."), kind: "battle", difficulty: 21, artKey: "gargoyle" }
]];
