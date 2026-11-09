import type { SchaetzoramaRoundContent } from "../protocol.js";

/**
 * Banco original de Cifras salvajes para Vision+.
 *
 * Las preguntas están escritas para una mesa mexicana: México, comida,
 * transporte, deporte, cultura, tradiciones, naturaleza, tecnología y vida
 * diaria. No se conservan preguntas alemanas ni traducciones heredadas.
 */
export const schaetzoramaRounds: SchaetzoramaRoundContent[] = [
  {
    roundIndex: 1,
    roundLabel: "México en números",
    questions: {
      number: {
        id: "mx-estados",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 50",
        shortLabel: "Número",
        prompt: "Sin contar a tu tío que jura que son 31: ¿cuántos estados tiene México?",
        min: 1,
        max: 50,
        answer: 32,
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      percent: {
        id: "mx-poblacion-urbana",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de México vive en ciudad, con tráfico, vecinos y todo?",
        min: 0,
        max: 100,
        answer: 79,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "mx-poblacion-entidades",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda a estos estados por población, del más lleno al más vacío.",
        directionLabel: "la más poblada primero",
        items: [
          { id: "edomex", label: "Estado de México" },
          { id: "cdmx", label: "Ciudad de México" },
          { id: "jalisco", label: "Jalisco" }
        ],
        answerOrder: ["edomex", "cdmx", "jalisco"],
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "mx-estados-costa",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa a los estados playeros de los que solo ven el mar en fotos.",
        leftLabel: "Con costa",
        rightLabel: "Sin costa",
        terms: [
          { id: "jalisco", label: "Jalisco" },
          { id: "puebla", label: "Puebla" },
          { id: "quintana-roo", label: "Quintana Roo" },
          { id: "nuevo-leon", label: "Nuevo León" },
          { id: "sinaloa", label: "Sinaloa" }
        ],
        answers: {
          jalisco: "left",
          puebla: "right",
          "quintana-roo": "left",
          "nuevo-leon": "right",
          sinaloa: "left"
        },
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      }
    }
  },
  {
    roundIndex: 2,
    roundLabel: "Cocina mexicana",
    questions: {
      number: {
        id: "mx-maices",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 100",
        shortLabel: "Número",
        prompt: "Si tu abuela te presume su nixtamal, ¿cuántas razas de maíz nativo hay en México?",
        min: 1,
        max: 100,
        answer: 64,
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      },
      percent: {
        id: "aguacate-grasa",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "Un aguacate Hass: ¿qué porcentaje de su cuerpo es grasa de la buena?",
        min: 0,
        max: 100,
        answer: 15,
        unitLabel: "%",
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      },
      rank: {
        id: "chiles-picor",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Ordena estos chiles de menos a más “ay, güey”.",
        directionLabel: "de menor a mayor picor",
        items: [
          { id: "jalapeno", label: "Jalapeño" },
          { id: "serrano", label: "Serrano" },
          { id: "habanero", label: "Habanero" }
        ],
        answerOrder: ["jalapeno", "serrano", "habanero"],
        source: { label: "Secretaría de Agricultura", url: "https://www.gob.mx/agricultura" }
      },
      assign: {
        id: "ingredientes-origen",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los ingredientes que ya estaban aquí de los que llegaron en el barco.",
        leftLabel: "Originario de México",
        rightLabel: "Introducido",
        terms: [
          { id: "maiz", label: "Maíz" },
          { id: "frijol", label: "Frijol" },
          { id: "chile", label: "Chile" },
          { id: "trigo", label: "Trigo" },
          { id: "cerdo", label: "Cerdo" },
          { id: "arroz", label: "Arroz" }
        ],
        answers: {
          maiz: "left",
          frijol: "left",
          chile: "left",
          trigo: "right",
          cerdo: "right",
          arroz: "right"
        },
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      }
    }
  },
  {
    roundIndex: 3,
    roundLabel: "Transporte y calles",
    questions: {
      number: {
        id: "metro-lineas",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 20",
        shortLabel: "Número",
        prompt: "¿Cuántas líneas del Metro hay que dominar para presumir que ya te sabes la CDMX?",
        min: 1,
        max: 20,
        answer: 12,
        source: { label: "Metro CDMX", url: "https://www.metro.cdmx.gob.mx/" }
      },
      percent: {
        id: "transporte-emisiones",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué tanto del humo que le echamos al planeta viene de camiones, autos y tráfico?",
        min: 0,
        max: 100,
        answer: 25,
        unitLabel: "%",
        source: { label: "INECC", url: "https://www.gob.mx/inecc" }
      },
      rank: {
        id: "metro-pasajeros",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos transportes chilangos por pasaje anual, del más lleno al más vacío.",
        directionLabel: "de mayor a menor",
        items: [
          { id: "metro", label: "Metro" },
          { id: "metrobus", label: "Metrobús" },
          { id: "tren-ligero", label: "Tren Ligero" }
        ],
        answerOrder: ["metro", "metrobus", "tren-ligero"],
        source: { label: "SEMOVI CDMX", url: "https://www.semovi.cdmx.gob.mx/" }
      },
      assign: {
        id: "transporte-masivo",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los transportes de multitud de los de “voy solo en mi burbuja”.",
        leftLabel: "Masivo",
        rightLabel: "Individual",
        terms: [
          { id: "metro", label: "Metro" },
          { id: "metrobus", label: "Metrobús" },
          { id: "bicicleta", label: "Bicicleta" },
          { id: "auto", label: "Automóvil" },
          { id: "moto", label: "Motocicleta" }
        ],
        answers: {
          metro: "left",
          metrobus: "left",
          bicicleta: "right",
          auto: "right",
          moto: "right"
        },
        source: { label: "SEMOVI CDMX", url: "https://www.semovi.cdmx.gob.mx/" }
      }
    }
  },
  {
    roundIndex: 4,
    roundLabel: "Fútbol y deporte",
    questions: {
      number: {
        id: "mexico-oros-olimpicos",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 30",
        shortLabel: "Número",
        prompt: "¿Cuántos oros olímpicos tiene México? Suficientes para presumir, no tantos para callar a Brasil.",
        min: 0,
        max: 30,
        answer: 13,
        source: { label: "COI", url: "https://olympics.com/" }
      },
      percent: {
        id: "mexicanos-deporte",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de mexicanos de verdad se mueve, y no solo cambia de canal?",
        min: 0,
        max: 100,
        answer: 40,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "futbol-jugadores",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Ordena estas versiones del fútbol de menos a más jugadores en la cancha.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "futbol-5", label: "Fútbol 5" },
          { id: "futbol-7", label: "Fútbol 7" },
          { id: "futbol-11", label: "Fútbol 11" }
        ],
        answerOrder: ["futbol-5", "futbol-7", "futbol-11"],
        source: { label: "FMF", url: "https://www.fmf.mx/" }
      },
      assign: {
        id: "deportes-balon",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los deportes de balón de los que se juegan sin pelota.",
        leftLabel: "Con balón",
        rightLabel: "Sin balón",
        terms: [
          { id: "futbol", label: "Fútbol" },
          { id: "basquetbol", label: "Basquetbol" },
          { id: "voleibol", label: "Voleibol" },
          { id: "natacion", label: "Natación" },
          { id: "atletismo", label: "Atletismo" },
          { id: "boxeo", label: "Boxeo" }
        ],
        answers: {
          futbol: "left",
          basquetbol: "left",
          voleibol: "left",
          natacion: "right",
          atletismo: "right",
          boxeo: "right"
        },
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      }
    }
  },
  {
    roundIndex: 5,
    roundLabel: "Cine y cultura",
    questions: {
      number: {
        id: "mexico-nobel",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 10",
        shortLabel: "Número",
        prompt: "¿Cuántos Nobel se ha llevado México? No, el de la paz no se inventa.",
        min: 0,
        max: 10,
        answer: 3,
        source: { label: "Premio Nobel", url: "https://www.nobelprize.org/" }
      },
      percent: {
        id: "internet-mexico",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los hogares mexicanos vive con internet y no solo con datos prestados?",
        min: 0,
        max: 100,
        answer: 71,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "estrenos-cine",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas películas de la más vieja a la más nueva.",
        directionLabel: "del más antiguo al más reciente",
        items: [
          { id: "laberinto", label: "El laberinto del fauno" },
          { id: "coco", label: "Coco" },
          { id: "roma", label: "Roma" }
        ],
        answerOrder: ["laberinto", "coco", "roma"],
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      assign: {
        id: "cine-mexicano",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa el cine hecho en México del que llegó doblado.",
        leftLabel: "Mexicana",
        rightLabel: "Extranjera",
        terms: [
          { id: "roma", label: "Roma" },
          { id: "amores-perros", label: "Amores perros" },
          { id: "coco", label: "Coco" },
          { id: "interestelar", label: "Interestelar" },
          { id: "padrino", label: "El Padrino" }
        ],
        answers: {
          roma: "left",
          "amores-perros": "left",
          coco: "right",
          interestelar: "right",
          padrino: "right"
        },
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      }
    }
  },
  {
    roundIndex: 6,
    roundLabel: "Fiestas y tradiciones",
    questions: {
      number: {
        id: "guelaguetza-lunes",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 7",
        shortLabel: "Número",
        prompt: "¿Cuántos lunes de Guelaguetza se arma el desmadre en Oaxaca?",
        min: 1,
        max: 7,
        answer: 2,
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      percent: {
        id: "catolicos-mexico",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de México se persigna y qué porcentaje se hace el sueco?",
        min: 0,
        max: 100,
        answer: 78,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "calendario-fiestas",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas fiestas en el calendario, de enero a diciembre.",
        directionLabel: "de enero a diciembre",
        items: [
          { id: "reyes", label: "Día de Reyes" },
          { id: "guelaguetza", label: "Guelaguetza" },
          { id: "muertos", label: "Día de Muertos" }
        ],
        answerOrder: ["reyes", "guelaguetza", "muertos"],
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      assign: {
        id: "tradiciones-mexico",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa las tradiciones de aquí de las que nos llegaron del extranjero.",
        leftLabel: "México",
        rightLabel: "Otro país",
        terms: [
          { id: "muertos", label: "Día de Muertos" },
          { id: "posadas", label: "Posadas" },
          { id: "guelaguetza", label: "Guelaguetza" },
          { id: "thanksgiving", label: "Día de Acción de Gracias" },
          { id: "oktoberfest", label: "Oktoberfest" },
          { id: "halloween", label: "Halloween" }
        ],
        answers: {
          muertos: "left",
          posadas: "left",
          guelaguetza: "left",
          thanksgiving: "right",
          oktoberfest: "right",
          halloween: "right"
        },
        source: { label: "INAH", url: "https://www.inah.gob.mx/" }
      }
    }
  },
  {
    roundIndex: 7,
    roundLabel: "Naturaleza mexicana",
    questions: {
      number: {
        id: "estados-costa",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 32",
        shortLabel: "Número",
        prompt: "¿Cuántos estados de México pueden presumir playa de verdad?",
        min: 1,
        max: 32,
        answer: 17,
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      percent: {
        id: "area-protegida",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de México está protegido para que no se lo acabe el desarrollo?",
        min: 0,
        max: 100,
        answer: 15,
        unitLabel: "%",
        source: { label: "CONANP", url: "https://www.gob.mx/conanp" }
      },
      rank: {
        id: "estados-superficie",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos estados por tamaño, del más grandote al más chiquito.",
        directionLabel: "el más grande primero",
        items: [
          { id: "chihuahua", label: "Chihuahua" },
          { id: "sonora", label: "Sonora" },
          { id: "coahuila", label: "Coahuila" }
        ],
        answerOrder: ["chihuahua", "sonora", "coahuila"],
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "fauna-mexico",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa a los animales que sí son de aquí de los que solo salen en documentales.",
        leftLabel: "Vive en México",
        rightLabel: "No vive en México",
        terms: [
          { id: "jaguar", label: "Jaguar" },
          { id: "ajolote", label: "Ajolote" },
          { id: "vaquita", label: "Vaquita marina" },
          { id: "panda", label: "Panda" },
          { id: "canguro", label: "Canguro" },
          { id: "oso-polar", label: "Oso polar" }
        ],
        answers: {
          jaguar: "left",
          ajolote: "left",
          vaquita: "left",
          panda: "right",
          canguro: "right",
          "oso-polar": "right"
        },
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      }
    }
  },
  {
    roundIndex: 8,
    roundLabel: "Tecnología cotidiana",
    questions: {
      number: {
        id: "bits-byte",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 16",
        shortLabel: "Número",
        prompt: "¿Cuántos bits se necesitan para armar un byte? El clásico que todos dicen saber.",
        min: 1,
        max: 16,
        answer: 8,
        source: { label: "Datos técnicos", url: "https://www.inegi.org.mx/" }
      },
      percent: {
        id: "hogares-internet",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de las casas mexicanas tiene internet sin robarse la señal del vecino?",
        min: 0,
        max: 100,
        answer: 71,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "apps-lanzamiento",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas apps de la más vieja a la más nueva. Sin buscar en la tienda.",
        directionLabel: "del más antiguo al más reciente",
        items: [
          { id: "facebook", label: "Facebook" },
          { id: "whatsapp", label: "WhatsApp" },
          { id: "tiktok", label: "TikTok" }
        ],
        answerOrder: ["facebook", "whatsapp", "tiktok"],
        source: { label: "Datos públicos", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "apps-mensajeria-video",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa estas apps: mensajería, video o las dos cosas.",
        leftLabel: "Mensajería",
        rightLabel: "Video",
        terms: [
          { id: "whatsapp", label: "WhatsApp" },
          { id: "telegram", label: "Telegram" },
          { id: "youtube", label: "YouTube" },
          { id: "tiktok", label: "TikTok" },
          { id: "instagram", label: "Instagram" }
        ],
        answers: {
          whatsapp: "left",
          telegram: "left",
          youtube: "right",
          tiktok: "right",
          instagram: "both"
        },
        source: { label: "Datos públicos", url: "https://www.inegi.org.mx/" }
      }
    }
  },
  {
    roundIndex: 9,
    roundLabel: "Vida diaria",
    questions: {
      number: {
        id: "jornada-laboral",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 12",
        shortLabel: "Número",
        prompt: "¿Cuántas horas tiene una jornada laboral completa en México? Sí, la de verdad, no la que te quedas.",
        min: 1,
        max: 12,
        answer: 8,
        source: { label: "STPS", url: "https://www.gob.mx/stps" }
      },
      percent: {
        id: "ingreso-alimentos",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje del sueldo mexicano se va en comida? Y no, los tacos no son gasto hormiga.",
        min: 0,
        max: 100,
        answer: 30,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "ciudades-poblacion",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas ciudades por población, de la más llena a la más tranquila.",
        directionLabel: "la más poblada primero",
        items: [
          { id: "cdmx", label: "Ciudad de México" },
          { id: "guadalajara", label: "Guadalajara" },
          { id: "monterrey", label: "Monterrey" }
        ],
        answerOrder: ["cdmx", "guadalajara", "monterrey"],
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "gastos-hogar",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los gastos que sí son básicos de los que son puro gusto.",
        leftLabel: "Servicio básico",
        rightLabel: "Entretenimiento",
        terms: [
          { id: "renta", label: "Renta" },
          { id: "luz", label: "Luz" },
          { id: "agua", label: "Agua" },
          { id: "streaming", label: "Streaming" },
          { id: "cine", label: "Cine" },
          { id: "concierto", label: "Concierto" }
        ],
        answers: {
          renta: "left",
          luz: "left",
          agua: "left",
          streaming: "right",
          cine: "right",
          concierto: "right"
        },
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      }
    }
  },
  {
    roundIndex: 10,
    roundLabel: "Récords y curiosidades",
    questions: {
      number: {
        id: "patrimonio-mexico",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 50",
        shortLabel: "Número",
        prompt: "¿Cuántos lugares de México tienen la calcomanía de Patrimonio Mundial?",
        min: 0,
        max: 50,
        answer: 35,
        source: { label: "UNESCO", url: "https://whc.unesco.org/" }
      },
      percent: {
        id: "cactaceas-mexico",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los cactus del mundo son mexicanos de corazón?",
        min: 0,
        max: 100,
        answer: 70,
        unitLabel: "%",
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      },
      rank: {
        id: "volcanes-altura",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos volcanes por altura, del más alto al más chaparro.",
        directionLabel: "el más alto primero",
        items: [
          { id: "orizaba", label: "Pico de Orizaba" },
          { id: "popocatepetl", label: "Popocatépetl" },
          { id: "iztaccihuatl", label: "Iztaccíhuatl" }
        ],
        answerOrder: ["orizaba", "popocatepetl", "iztaccihuatl"],
        source: { label: "CONANP", url: "https://www.gob.mx/conanp" }
      },
      assign: {
        id: "patrimonio-mexico-fuera",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los patrimonios que están en México de los que hay que sacar pasaporte.",
        leftLabel: "En México",
        rightLabel: "Fuera de México",
        terms: [
          { id: "chichen", label: "Chichén Itzá" },
          { id: "teotihuacan", label: "Teotihuacán" },
          { id: "centro-cdmx", label: "Centro Histórico de la CDMX" },
          { id: "machu-picchu", label: "Machu Picchu" },
          { id: "coliseo", label: "Coliseo" },
          { id: "torre-eiffel", label: "Torre Eiffel" }
        ],
        answers: {
          chichen: "left",
          teotihuacan: "left",
          "centro-cdmx": "left",
          "machu-picchu": "right",
          coliseo: "right",
          "torre-eiffel": "right"
        },
        source: { label: "UNESCO", url: "https://whc.unesco.org/" }
      }
    }
  }
];

/*
 * Paquete 2: otras diez rondas para que una segunda sesión no repita el
 * mismo banco. La selección sigue siendo aleatoria y sin reutilizar preguntas
 * dentro de una misma partida.
 */
schaetzoramaRounds.push(
  {
    roundIndex: 11,
    roundLabel: "Animales de México",
    questions: {
      number: {
        id: "mx-primates",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 10",
        shortLabel: "Número",
        prompt: "¿Cuántas especies de monos y primates aguantan el calor mexicano?",
        min: 0,
        max: 10,
        answer: 2,
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      },
      percent: {
        id: "mx-bosques",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de México sigue lleno de árboles y no de concreto?",
        min: 0,
        max: 100,
        answer: 33,
        unitLabel: "%",
        source: { label: "CONAFOR", url: "https://www.gob.mx/conafor" }
      },
      rank: {
        id: "animales-tamano",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Ordena estos animales del más chiquito al más imponente.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "ajolote", label: "Ajolote" },
          { id: "vaquita", label: "Vaquita marina" },
          { id: "jaguar", label: "Jaguar" }
        ],
        answerOrder: ["ajolote", "vaquita", "jaguar"],
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      },
      assign: {
        id: "animales-vertebrados",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa a los animales con huesitos de los que son puro blando.",
        leftLabel: "Vertebrado",
        rightLabel: "Invertebrado",
        terms: [
          { id: "jaguar", label: "Jaguar" },
          { id: "ajolote", label: "Ajolote" },
          { id: "vaquita", label: "Vaquita marina" },
          { id: "monarca", label: "Mariposa monarca" },
          { id: "tarantula", label: "Tarántula" },
          { id: "pulpo", label: "Pulpo" }
        ],
        answers: {
          jaguar: "left",
          ajolote: "left",
          vaquita: "left",
          monarca: "right",
          tarantula: "right",
          pulpo: "right"
        },
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      }
    }
  },
  {
    roundIndex: 12,
    roundLabel: "Música y baile",
    questions: {
      number: {
        id: "guitarra-cuerdas",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 12",
        shortLabel: "Número",
        prompt: "¿Cuántas cuerdas tiene una guitarra clásica? La de las serenatas, no la del videojuego.",
        min: 0,
        max: 12,
        answer: 6,
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      percent: {
        id: "orquesta-cuerdas",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de una orquesta sinfónica es puro violín y compañía?",
        min: 0,
        max: 100,
        answer: 60,
        unitLabel: "%",
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      rank: {
        id: "instrumentos-cuerdas",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos instrumentos por número de cuerdas, de menos a más.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "bajo", label: "Bajo eléctrico" },
          { id: "guitarra", label: "Guitarra" },
          { id: "arpa", label: "Arpa" }
        ],
        answerOrder: ["bajo", "guitarra", "arpa"],
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      assign: {
        id: "instrumentos-familia",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los instrumentos de cuerda de los de viento. Sin soplarte la respuesta.",
        leftLabel: "Cuerda",
        rightLabel: "Viento",
        terms: [
          { id: "guitarra", label: "Guitarra" },
          { id: "violin", label: "Violín" },
          { id: "trompeta", label: "Trompeta" },
          { id: "flauta", label: "Flauta" },
          { id: "bajo", label: "Bajo" },
          { id: "saxofon", label: "Saxofón" }
        ],
        answers: {
          guitarra: "left",
          violin: "left",
          trompeta: "right",
          flauta: "right",
          bajo: "left",
          saxofon: "right"
        },
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      }
    }
  },
  {
    roundIndex: 13,
    roundLabel: "Ciudades y regiones",
    questions: {
      number: {
        id: "cdmx-alcaldias",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 20",
        shortLabel: "Número",
        prompt: "¿Cuántas alcaldías tiene la CDMX? Sí, más que las que visitas.",
        min: 0,
        max: 20,
        answer: 16,
        source: { label: "Gobierno CDMX", url: "https://www.cdmx.gob.mx/" }
      },
      percent: {
        id: "poblacion-cdmx-edomex",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de México vive en la CDMX y Edomex? Puro tráfico concentrado.",
        min: 0,
        max: 100,
        answer: 25,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "ciudades-altitud",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas ciudades por altura, de la que te deja sin aire a la que no.",
        directionLabel: "la más alta primero",
        items: [
          { id: "cdmx", label: "Ciudad de México" },
          { id: "guadalajara", label: "Guadalajara" },
          { id: "monterrey", label: "Monterrey" }
        ],
        answerOrder: ["cdmx", "guadalajara", "monterrey"],
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "ciudades-region",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa estas ciudades: centro del país o norte. Sin ofender a nadie.",
        leftLabel: "Centro",
        rightLabel: "Norte",
        terms: [
          { id: "cdmx", label: "Ciudad de México" },
          { id: "guadalajara", label: "Guadalajara" },
          { id: "puebla", label: "Puebla" },
          { id: "monterrey", label: "Monterrey" },
          { id: "tijuana", label: "Tijuana" },
          { id: "chihuahua", label: "Chihuahua" }
        ],
        answers: {
          cdmx: "left",
          guadalajara: "left",
          puebla: "left",
          monterrey: "right",
          tijuana: "right",
          chihuahua: "right"
        },
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      }
    }
  },
  {
    roundIndex: 14,
    roundLabel: "Comida y bebida",
    questions: {
      number: {
        id: "queso-leche",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 20",
        shortLabel: "Número",
        prompt: "¿Cuánta leche se necesita para un kilo de queso? La respuesta te va a doler.",
        min: 1,
        max: 20,
        answer: 10,
        source: { label: "Secretaría de Agricultura", url: "https://www.gob.mx/agricultura" }
      },
      percent: {
        id: "aguacate-mundial",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje del aguacate mundial es mexicano? El oro verde.",
        min: 0,
        max: 100,
        answer: 30,
        unitLabel: "%",
        source: { label: "Secretaría de Agricultura", url: "https://www.gob.mx/agricultura" }
      },
      rank: {
        id: "frutas-azucar",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas frutas por azúcar, de la más inocente a la más tramposa.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "limon", label: "Limón" },
          { id: "naranja", label: "Naranja" },
          { id: "mango", label: "Mango" }
        ],
        answerOrder: ["limon", "naranja", "mango"],
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      },
      assign: {
        id: "bebidas-alcohol",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa las bebidas con alcohol de las que sí te dejan manejar.",
        leftLabel: "Con alcohol",
        rightLabel: "Sin alcohol",
        terms: [
          { id: "tequila", label: "Tequila" },
          { id: "mezcal", label: "Mezcal" },
          { id: "cerveza", label: "Cerveza" },
          { id: "horchata", label: "Horchata" },
          { id: "agua-fresca", label: "Agua fresca" },
          { id: "cafe", label: "Café" }
        ],
        answers: {
          tequila: "left",
          mezcal: "left",
          cerveza: "left",
          horchata: "right",
          "agua-fresca": "right",
          cafe: "right"
        },
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      }
    }
  },
  {
    roundIndex: 15,
    roundLabel: "Historia y patrimonio",
    questions: {
      number: {
        id: "revolucion-anios",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 20",
        shortLabel: "Número",
        prompt: "¿Cuántos años duró la Revolución Mexicana? Y no, no empezó con tu tío peleando en la comida.",
        min: 0,
        max: 20,
        answer: 10,
        source: { label: "INAH", url: "https://www.inah.gob.mx/" }
      },
      percent: {
        id: "lenguas-indigenas",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de México habla alguna lengua indígena? No, el náhuatl de tu abuela no cuenta como grosería.",
        min: 0,
        max: 100,
        answer: 6,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "construcciones-antiguedad",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas construcciones de la más vieja a la más nueva.",
        directionLabel: "de la más antigua a la más reciente",
        items: [
          { id: "teotihuacan", label: "Teotihuacán" },
          { id: "chichen", label: "Chichén Itzá" },
          { id: "bellas-artes", label: "Palacio de Bellas Artes" }
        ],
        answerOrder: ["teotihuacan", "chichen", "bellas-artes"],
        source: { label: "INAH", url: "https://www.inah.gob.mx/" }
      },
      assign: {
        id: "construcciones-epoca",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa lo prehispánico de lo que llegó después de la Conquista.",
        leftLabel: "Prehispánica",
        rightLabel: "Posterior",
        terms: [
          { id: "teotihuacan", label: "Teotihuacán" },
          { id: "chichen", label: "Chichén Itzá" },
          { id: "monte-alban", label: "Monte Albán" },
          { id: "catedral", label: "Catedral Metropolitana" },
          { id: "bellas-artes", label: "Palacio de Bellas Artes" },
          { id: "torre-latino", label: "Torre Latinoamericana" }
        ],
        answers: {
          teotihuacan: "left",
          chichen: "left",
          "monte-alban": "left",
          catedral: "right",
          "bellas-artes": "right",
          "torre-latino": "right"
        },
        source: { label: "INAH", url: "https://www.inah.gob.mx/" }
      }
    }
  },
  {
    roundIndex: 16,
    roundLabel: "Ciencia y cuerpo",
    questions: {
      number: {
        id: "huesos-adulto",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 300",
        shortLabel: "Número",
        prompt: "¿Cuántos huesos tenemos los adultos? Sí, el de la risa no cuenta.",
        min: 0,
        max: 300,
        answer: 206,
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      },
      percent: {
        id: "cuerpo-agua",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de tu cuerpo es agua? No, las chelas no reemplazan.",
        min: 0,
        max: 100,
        answer: 60,
        unitLabel: "%",
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      },
      rank: {
        id: "escala-biologica",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Ordena estas estructuras del tamaño más mínimo al más grotesco.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "atomo", label: "Átomo" },
          { id: "celula", label: "Célula" },
          { id: "organo", label: "Órgano" }
        ],
        answerOrder: ["atomo", "celula", "organo"],
        source: { label: "UNAM", url: "https://www.unam.mx/" }
      },
      assign: {
        id: "organos-sistema",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los órganos de la digestión de los de la respiración.",
        leftLabel: "Digestivo",
        rightLabel: "Respiratorio",
        terms: [
          { id: "estomago", label: "Estómago" },
          { id: "intestino", label: "Intestino" },
          { id: "higado", label: "Hígado" },
          { id: "pulmon", label: "Pulmón" },
          { id: "traquea", label: "Tráquea" },
          { id: "nariz", label: "Nariz" }
        ],
        answers: {
          estomago: "left",
          intestino: "left",
          higado: "left",
          pulmon: "right",
          traquea: "right",
          nariz: "right"
        },
        source: { label: "UNAM", url: "https://www.unam.mx/" }
      }
    }
  },
  {
    roundIndex: 17,
    roundLabel: "Deportes y récords",
    questions: {
      number: {
        id: "voleibol-jugadores",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 12",
        shortLabel: "Número",
        prompt: "¿Cuántos jugadores de voleibol hay en cancha? Ni uno más, ni uno menos.",
        min: 0,
        max: 12,
        answer: 6,
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      },
      percent: {
        id: "paises-medalla-olimpica",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de países del mundo presume al menos una medalla olímpica?",
        min: 0,
        max: 100,
        answer: 25,
        unitLabel: "%",
        source: { label: "COI", url: "https://olympics.com/" }
      },
      rank: {
        id: "pruebas-duracion",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas pruebas por duración, de la más rápida a la más eterna.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "cien-metros", label: "100 metros planos" },
          { id: "futbol", label: "Partido de fútbol" },
          { id: "maraton", label: "Maratón" }
        ],
        answerOrder: ["cien-metros", "futbol", "maraton"],
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      },
      assign: {
        id: "deportes-olimpicos",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los deportes olímpicos de los que solo salen en la tele local.",
        leftLabel: "Olímpico",
        rightLabel: "No olímpico",
        terms: [
          { id: "futbol", label: "Fútbol" },
          { id: "atletismo", label: "Atletismo" },
          { id: "natacion", label: "Natación" },
          { id: "charreria", label: "Charrería" },
          { id: "frontenis", label: "Frontenis" },
          { id: "padel", label: "Pádel" }
        ],
        answers: {
          futbol: "left",
          atletismo: "left",
          natacion: "left",
          charreria: "right",
          frontenis: "right",
          padel: "right"
        },
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      }
    }
  },
  {
    roundIndex: 18,
    roundLabel: "Cine, tele y streaming",
    questions: {
      number: {
        id: "padrino-peliculas",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 6",
        shortLabel: "Número",
        prompt: "¿Cuántas películas forman la trilogía original de El Padrino? No, la tercera no se borra.",
        min: 0,
        max: 6,
        answer: 3,
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      percent: {
        id: "hogares-tv-paga",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los hogares mexicanos paga tele de paga y no solo usa la del compa?",
        min: 0,
        max: 100,
        answer: 30,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "series-estreno",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas producciones de la más vieja a la más nueva.",
        directionLabel: "del más antiguo al más reciente",
        items: [
          { id: "chavo", label: "El Chavo del 8" },
          { id: "rebelde", label: "Rebelde" },
          { id: "casa-flores", label: "La casa de las flores" }
        ],
        answerOrder: ["chavo", "rebelde", "casa-flores"],
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      assign: {
        id: "producciones-mexicanas",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa las producciones hechas en México de las que llegaron dobladas.",
        leftLabel: "Mexicana",
        rightLabel: "Extranjera",
        terms: [
          { id: "chavo", label: "El Chavo del 8" },
          { id: "rebelde", label: "Rebelde" },
          { id: "casa-flores", label: "La casa de las flores" },
          { id: "stranger", label: "Stranger Things" },
          { id: "got", label: "Game of Thrones" },
          { id: "friends", label: "Friends" }
        ],
        answers: {
          chavo: "left",
          rebelde: "left",
          "casa-flores": "left",
          stranger: "right",
          got: "right",
          friends: "right"
        },
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      }
    }
  },
  {
    roundIndex: 19,
    roundLabel: "Trabajo y dinero",
    questions: {
      number: {
        id: "vacaciones-ley",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 30",
        shortLabel: "Número",
        prompt: "¿Cuántos días de vacaciones te tocan por ley? Sí, los que probablemente no te tomas.",
        min: 0,
        max: 30,
        answer: 12,
        source: { label: "STPS", url: "https://www.gob.mx/stps" }
      },
      percent: {
        id: "renta-ingreso-cdmx",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje del sueldo chilango se va en renta? Llora con números.",
        min: 0,
        max: 100,
        answer: 35,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "billetes-valor",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos billetes de menor a mayor. Sin contarlos con la mirada.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "veinte", label: "20 pesos" },
          { id: "cincuenta", label: "50 pesos" },
          { id: "cien", label: "100 pesos" }
        ],
        answerOrder: ["veinte", "cincuenta", "cien"],
        source: { label: "Banxico", url: "https://www.banxico.org.mx/" }
      },
      assign: {
        id: "gastos-fijos",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los gastos fijos de los que se te van en un fin de semana.",
        leftLabel: "Fijo",
        rightLabel: "Variable",
        terms: [
          { id: "renta", label: "Renta" },
          { id: "luz", label: "Luz" },
          { id: "internet", label: "Internet" },
          { id: "comida", label: "Comida" },
          { id: "salidas", label: "Salidas" },
          { id: "transporte", label: "Transporte" }
        ],
        answers: {
          renta: "left",
          luz: "left",
          internet: "left",
          comida: "right",
          salidas: "right",
          transporte: "right"
        },
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      }
    }
  },
  {
    roundIndex: 20,
    roundLabel: "Tecnología y redes",
    questions: {
      number: {
        id: "primer-tuit",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 300",
        shortLabel: "Número",
        prompt: "¿Cuántos caracteres cabían en el primer tuit? La época en que escribir era un deporte.",
        min: 0,
        max: 300,
        answer: 140,
        source: { label: "Datos técnicos", url: "https://www.inegi.org.mx/" }
      },
      percent: {
        id: "mexicanos-smartphone",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de mexicanos tiene smartphone? El resto todavía dice “mándame un mensajito”.",
        min: 0,
        max: 100,
        answer: 80,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "tecnologia-lanzamiento",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos lanzamientos tecnológicos del más viejo al más nuevo.",
        directionLabel: "del más antiguo al más reciente",
        items: [
          { id: "iphone", label: "iPhone" },
          { id: "android", label: "Android" },
          { id: "chatgpt", label: "ChatGPT" }
        ],
        answerOrder: ["iphone", "android", "chatgpt"],
        source: { label: "Datos técnicos", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "plataformas-social",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa las plataformas: redes, mensajería o las dos caras.",
        leftLabel: "Red social",
        rightLabel: "Mensajería",
        terms: [
          { id: "facebook", label: "Facebook" },
          { id: "tiktok", label: "TikTok" },
          { id: "whatsapp", label: "WhatsApp" },
          { id: "telegram", label: "Telegram" },
          { id: "instagram", label: "Instagram" }
        ],
        answers: {
          facebook: "left",
          tiktok: "left",
          whatsapp: "right",
          telegram: "right",
          instagram: "both"
        },
        source: { label: "Datos técnicos", url: "https://www.inegi.org.mx/" }
      }
    }
  }
);

/* Paquete 3: diez rondas más para alargar la rotación del banco. */
schaetzoramaRounds.push(
  {
    roundIndex: 21,
    roundLabel: "Fiestas y religión",
    questions: {
      number: {
        id: "sacramentos-catolicos",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 12",
        shortLabel: "Número",
        prompt: "¿Cuántos sacramentos tiene la Iglesia católica? No, el de la carne asada no cuenta.",
        min: 0,
        max: 12,
        answer: 7,
        source: { label: "INAH", url: "https://www.inah.gob.mx/" }
      },
      percent: {
        id: "hogares-ofrenda",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de las casas mexicanas pone ofrenda en Día de Muertos?",
        min: 0,
        max: 100,
        answer: 80,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "fiestas-civicas",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas fechas cívicas de enero a diciembre. Sin faltar al respeto.",
        directionLabel: "de enero a diciembre",
        items: [
          { id: "bandera", label: "Día de la Bandera" },
          { id: "independencia", label: "Día de la Independencia" },
          { id: "revolucion", label: "Día de la Revolución" }
        ],
        answerOrder: ["bandera", "independencia", "revolucion"],
        source: { label: "Gobierno de México", url: "https://www.gob.mx/" }
      },
      assign: {
        id: "celebraciones-tipo",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa las celebraciones religiosas de las cívicas.",
        leftLabel: "Religiosa",
        rightLabel: "Cívica",
        terms: [
          { id: "navidad", label: "Navidad" },
          { id: "semana-santa", label: "Semana Santa" },
          { id: "guadalupe", label: "Día de la Virgen de Guadalupe" },
          { id: "independencia", label: "Día de la Independencia" },
          { id: "revolucion", label: "Día de la Revolución" },
          { id: "bandera", label: "Día de la Bandera" }
        ],
        answers: {
          navidad: "left",
          "semana-santa": "left",
          guadalupe: "left",
          independencia: "right",
          revolucion: "right",
          bandera: "right"
        },
        source: { label: "INAH", url: "https://www.inah.gob.mx/" }
      }
    }
  },
  {
    roundIndex: 22,
    roundLabel: "Lugares turísticos",
    questions: {
      number: {
        id: "pueblos-magicos",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 200",
        shortLabel: "Número",
        prompt: "¿Cuántos Pueblos Mágicos tiene México? Sí, más de los que caben en un fin de semana.",
        min: 0,
        max: 200,
        answer: 177,
        source: { label: "SECTUR", url: "https://www.gob.mx/sectur" }
      },
      percent: {
        id: "turismo-pib",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje del PIB mexicano se mueve con turistas y maletas?",
        min: 0,
        max: 100,
        answer: 9,
        unitLabel: "%",
        source: { label: "SECTUR", url: "https://www.gob.mx/sectur" }
      },
      rank: {
        id: "destinos-altitud",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos destinos por altura, del playero al que te deja sin aire.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "acapulco", label: "Acapulco" },
          { id: "guadalajara", label: "Guadalajara" },
          { id: "cdmx", label: "Ciudad de México" }
        ],
        answerOrder: ["acapulco", "guadalajara", "cdmx"],
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "destinos-playa-ciudad",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los destinos de playa de los de ciudad. Sin quemarte.",
        leftLabel: "Playa",
        rightLabel: "Ciudad",
        terms: [
          { id: "cancun", label: "Cancún" },
          { id: "tulum", label: "Tulum" },
          { id: "huatulco", label: "Huatulco" },
          { id: "cdmx", label: "Ciudad de México" },
          { id: "guanajuato", label: "Guanajuato" },
          { id: "san-miguel", label: "San Miguel de Allende" }
        ],
        answers: {
          cancun: "left",
          tulum: "left",
          huatulco: "left",
          cdmx: "right",
          guanajuato: "right",
          "san-miguel": "right"
        },
        source: { label: "SECTUR", url: "https://www.gob.mx/sectur" }
      }
    }
  },
  {
    roundIndex: 23,
    roundLabel: "Escuela y oficios",
    questions: {
      number: {
        id: "primaria-anios",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 12",
        shortLabel: "Número",
        prompt: "¿Cuántos años dura la primaria en México? Los que te tocaron y los que repetiste.",
        min: 0,
        max: 12,
        answer: 6,
        source: { label: "SEP", url: "https://www.gob.mx/sep" }
      },
      percent: {
        id: "secundaria-terminada",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los mexicanos mayores de 15 años terminó la secundaria?",
        min: 0,
        max: 100,
        answer: 60,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "niveles-educativos",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos niveles educativos de menor a mayor.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "primaria", label: "Primaria" },
          { id: "secundaria", label: "Secundaria" },
          { id: "universidad", label: "Universidad" }
        ],
        answerOrder: ["primaria", "secundaria", "universidad"],
        source: { label: "SEP", url: "https://www.gob.mx/sep" }
      },
      assign: {
        id: "oficios-titulo",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los oficios que piden título de los que se aprenden en la calle.",
        leftLabel: "Con título",
        rightLabel: "Sin título",
        terms: [
          { id: "medico", label: "Médico" },
          { id: "abogado", label: "Abogado" },
          { id: "ingeniero", label: "Ingeniero" },
          { id: "plomero", label: "Plomero" },
          { id: "carpintero", label: "Carpintero" },
          { id: "mecanico", label: "Mecánico" }
        ],
        answers: {
          medico: "left",
          abogado: "left",
          ingeniero: "left",
          plomero: "right",
          carpintero: "right",
          mecanico: "right"
        },
        source: { label: "SEP", url: "https://www.gob.mx/sep" }
      }
    }
  },
  {
    roundIndex: 24,
    roundLabel: "Transporte y movilidad",
    questions: {
      number: {
        id: "metrobus-lineas",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 15",
        shortLabel: "Número",
        prompt: "¿Cuántas líneas tiene el Metrobús chilango? Las que sí te llevan lejos.",
        min: 0,
        max: 15,
        answer: 7,
        source: { label: "Metrobús CDMX", url: "https://www.metrobus.cdmx.gob.mx/" }
      },
      percent: {
        id: "viajes-transporte-publico",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los viajes chilangos se hacen en transporte público?",
        min: 0,
        max: 100,
        answer: 60,
        unitLabel: "%",
        source: { label: "SEMOVI CDMX", url: "https://www.semovi.cdmx.gob.mx/" }
      },
      rank: {
        id: "capacidad-transporte",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos transportes por capacidad, del más íntimo al más lleno.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "bici", label: "Bicicleta" },
          { id: "auto", label: "Automóvil" },
          { id: "autobus", label: "Autobús" }
        ],
        answerOrder: ["bici", "auto", "autobus"],
        source: { label: "SEMOVI CDMX", url: "https://www.semovi.cdmx.gob.mx/" }
      },
      assign: {
        id: "transporte-rieles",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los transportes con rieles de los que van sobre el asfalto.",
        leftLabel: "Con rieles",
        rightLabel: "Sin rieles",
        terms: [
          { id: "metro", label: "Metro" },
          { id: "tren-ligero", label: "Tren Ligero" },
          { id: "suburbano", label: "Tren Suburbano" },
          { id: "metrobus", label: "Metrobús" },
          { id: "autobus", label: "Autobús" },
          { id: "taxi", label: "Taxi" }
        ],
        answers: {
          metro: "left",
          "tren-ligero": "left",
          suburbano: "left",
          metrobus: "right",
          autobus: "right",
          taxi: "right"
        },
        source: { label: "SEMOVI CDMX", url: "https://www.semovi.cdmx.gob.mx/" }
      }
    }
  },
  {
    roundIndex: 25,
    roundLabel: "Animales y naturaleza II",
    questions: {
      number: {
        id: "tortugas-mexico",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 10",
        shortLabel: "Número",
        prompt: "¿Cuántas especies de tortugas marinas llegan a México a poner sus huevos?",
        min: 0,
        max: 10,
        answer: 6,
        source: { label: "CONANP", url: "https://www.gob.mx/conanp" }
      },
      percent: {
        id: "tortugas-mundo-mexico",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de las tortugas marinas del mundo se da una vuelta por México?",
        min: 0,
        max: 100,
        answer: 86,
        unitLabel: "%",
        source: { label: "CONANP", url: "https://www.gob.mx/conanp" }
      },
      rank: {
        id: "tortugas-tamano",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estas tortugas marinas de la más chiquita a la más imponente.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "golfina", label: "Tortuga golfina" },
          { id: "verde", label: "Tortuga verde" },
          { id: "laud", label: "Tortuga laúd" }
        ],
        answerOrder: ["golfina", "verde", "laud"],
        source: { label: "CONANP", url: "https://www.gob.mx/conanp" }
      },
      assign: {
        id: "animales-mar-tierra",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa a los animales marinos de los terrestres.",
        leftLabel: "Mar",
        rightLabel: "Tierra",
        terms: [
          { id: "tortuga-marina", label: "Tortuga marina" },
          { id: "delfin", label: "Delfín" },
          { id: "tiburon", label: "Tiburón" },
          { id: "jaguar", label: "Jaguar" },
          { id: "venado", label: "Venado" },
          { id: "conejo", label: "Conejo" }
        ],
        answers: {
          "tortuga-marina": "left",
          delfin: "left",
          tiburon: "left",
          jaguar: "right",
          venado: "right",
          conejo: "right"
        },
        source: { label: "CONABIO", url: "https://www.gob.mx/conabio" }
      }
    }
  },
  {
    roundIndex: 26,
    roundLabel: "Ciencia y espacio",
    questions: {
      number: {
        id: "planetas-sistema-solar",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 20",
        shortLabel: "Número",
        prompt: "¿Cuántos planetas tiene el sistema solar? Plutón ya no cuenta, aunque te duela.",
        min: 0,
        max: 20,
        answer: 8,
        source: { label: "UNAM", url: "https://www.unam.mx/" }
      },
      percent: {
        id: "masa-sol",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de la masa del sistema solar se la lleva el Sol? Casi todo.",
        min: 0,
        max: 100,
        answer: 99,
        unitLabel: "%",
        source: { label: "UNAM", url: "https://www.unam.mx/" }
      },
      rank: {
        id: "planetas-distancia",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos planetas por cercanía al Sol, del más quemado al más lejano.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "mercurio", label: "Mercurio" },
          { id: "tierra", label: "Tierra" },
          { id: "jupiter", label: "Júpiter" }
        ],
        answerOrder: ["mercurio", "tierra", "jupiter"],
        source: { label: "UNAM", url: "https://www.unam.mx/" }
      },
      assign: {
        id: "planetas-tipo",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los planetas rocosos de los gaseosos.",
        leftLabel: "Rocoso",
        rightLabel: "Gaseoso",
        terms: [
          { id: "mercurio", label: "Mercurio" },
          { id: "venus", label: "Venus" },
          { id: "tierra", label: "Tierra" },
          { id: "marte", label: "Marte" },
          { id: "jupiter", label: "Júpiter" },
          { id: "saturno", label: "Saturno" }
        ],
        answers: {
          mercurio: "left",
          venus: "left",
          tierra: "left",
          marte: "left",
          jupiter: "right",
          saturno: "right"
        },
        source: { label: "UNAM", url: "https://www.unam.mx/" }
      }
    }
  },
  {
    roundIndex: 27,
    roundLabel: "Comida y antojos II",
    questions: {
      number: {
        id: "calorias-taco",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 500",
        shortLabel: "Número",
        prompt: "¿Cuántas calorías tiene un taco al pastor? La respuesta no te va a gustar.",
        min: 0,
        max: 500,
        answer: 250,
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      },
      percent: {
        id: "maiz-consumo-humano",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje del maíz mexicano se lo come la gente y no el ganado?",
        min: 0,
        max: 100,
        answer: 40,
        unitLabel: "%",
        source: { label: "Secretaría de Agricultura", url: "https://www.gob.mx/agricultura" }
      },
      rank: {
        id: "alimentos-grasa",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos alimentos por grasa, del más inocente al más peligroso.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "nopal", label: "Nopal" },
          { id: "aguacate", label: "Aguacate" },
          { id: "chicharron", label: "Chicharrón" }
        ],
        answerOrder: ["nopal", "aguacate", "chicharron"],
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      },
      assign: {
        id: "antojitos-dulce-salado",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los antojitos dulces de los salados. Sin trampas.",
        leftLabel: "Dulce",
        rightLabel: "Salado",
        terms: [
          { id: "flan", label: "Flan" },
          { id: "arroz-leche", label: "Arroz con leche" },
          { id: "churros", label: "Churros" },
          { id: "tamal", label: "Tamal" },
          { id: "taco", label: "Taco" },
          { id: "pozole", label: "Pozole" }
        ],
        answers: {
          flan: "left",
          "arroz-leche": "left",
          churros: "left",
          tamal: "right",
          taco: "right",
          pozole: "right"
        },
        source: { label: "Secretaría de Salud", url: "https://www.gob.mx/salud" }
      }
    }
  },
  {
    roundIndex: 28,
    roundLabel: "Deportes II",
    questions: {
      number: {
        id: "basquetbol-tiempos",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 8",
        shortLabel: "Número",
        prompt: "¿Cuántos tiempos tiene un partido de basquetbol de la NBA?",
        min: 0,
        max: 8,
        answer: 4,
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      },
      percent: {
        id: "mexicanos-ven-deporte",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los mexicanos ve deportes por tele? Aunque sea de fondo.",
        min: 0,
        max: 100,
        answer: 60,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "deportes-jugadores",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos deportes por número de jugadores, del más solitario al más lleno.",
        directionLabel: "de menor a mayor",
        items: [
          { id: "tenis", label: "Tenis individual" },
          { id: "voleibol", label: "Voleibol" },
          { id: "futbol", label: "Fútbol" }
        ],
        answerOrder: ["tenis", "voleibol", "futbol"],
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      },
      assign: {
        id: "deportes-individual-equipo",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los deportes individuales de los de equipo.",
        leftLabel: "Individual",
        rightLabel: "Equipo",
        terms: [
          { id: "tenis", label: "Tenis individual" },
          { id: "boxeo", label: "Boxeo" },
          { id: "atletismo", label: "Atletismo" },
          { id: "futbol", label: "Fútbol" },
          { id: "voleibol", label: "Voleibol" },
          { id: "basquetbol", label: "Basquetbol" }
        ],
        answers: {
          tenis: "left",
          boxeo: "left",
          atletismo: "left",
          futbol: "right",
          voleibol: "right",
          basquetbol: "right"
        },
        source: { label: "CONADE", url: "https://www.gob.mx/conade" }
      }
    }
  },
  {
    roundIndex: 29,
    roundLabel: "Música y entretenimiento II",
    questions: {
      number: {
        id: "violin-cuerdas",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 10",
        shortLabel: "Número",
        prompt: "¿Cuántas cuerdas tiene un violín? La respuesta no incluye la del arco.",
        min: 0,
        max: 10,
        answer: 4,
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      percent: {
        id: "mexicanos-youtube",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje de los mexicanos entra a YouTube? El resto dice que solo por el tutorial.",
        min: 0,
        max: 100,
        answer: 80,
        unitLabel: "%",
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      rank: {
        id: "peliculas-estreno-2",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Ordena estas películas del estreno más antiguo al más reciente.",
        directionLabel: "del más antiguo al más reciente",
        items: [
          { id: "coco", label: "Coco" },
          { id: "roma", label: "Roma" },
          { id: "spiderman", label: "Spider-Man: No Way Home" }
        ],
        answerOrder: ["coco", "roma", "spiderman"],
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      },
      assign: {
        id: "artistas-mexicanos",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa a los artistas mexicanos de los que nos llegaron importados.",
        leftLabel: "Mexicano",
        rightLabel: "Extranjero",
        terms: [
          { id: "pedro-infante", label: "Pedro Infante" },
          { id: "frida", label: "Frida Kahlo" },
          { id: "cantinflas", label: "Cantinflas" },
          { id: "shakira", label: "Shakira" },
          { id: "bad-bunny", label: "Bad Bunny" },
          { id: "taylor", label: "Taylor Swift" }
        ],
        answers: {
          "pedro-infante": "left",
          frida: "left",
          cantinflas: "left",
          shakira: "right",
          "bad-bunny": "right",
          taylor: "right"
        },
        source: { label: "Secretaría de Cultura", url: "https://www.gob.mx/cultura" }
      }
    }
  },
  {
    roundIndex: 30,
    roundLabel: "México y el mundo",
    questions: {
      number: {
        id: "fronteras-mexico",
        categoryId: "number",
        kind: "number",
        title: "De 0 a 10",
        shortLabel: "Número",
        prompt: "¿Con cuántos países comparte México frontera terrestre? Y no, el mar no cuenta.",
        min: 0,
        max: 10,
        answer: 3,
        source: { label: "SRE", url: "https://www.gob.mx/sre" }
      },
      percent: {
        id: "comercio-eeuu",
        categoryId: "percent",
        kind: "percent",
        title: "Porcentaje",
        shortLabel: "Porcentaje",
        prompt: "¿Qué porcentaje del comercio mexicano se va con Estados Unidos? Casi todo.",
        min: 0,
        max: 100,
        answer: 80,
        unitLabel: "%",
        source: { label: "Secretaría de Economía", url: "https://www.gob.mx/se" }
      },
      rank: {
        id: "poblacion-norteamerica",
        categoryId: "rank",
        kind: "rank",
        title: "En orden",
        shortLabel: "Orden",
        prompt: "Acomoda estos países por población, del más lleno al más tranquilo.",
        directionLabel: "el más poblado primero",
        items: [
          { id: "eeuu", label: "Estados Unidos" },
          { id: "mexico", label: "México" },
          { id: "canada", label: "Canadá" }
        ],
        answerOrder: ["eeuu", "mexico", "canada"],
        source: { label: "INEGI", url: "https://www.inegi.org.mx/" }
      },
      assign: {
        id: "paises-norteamerica",
        categoryId: "assign",
        kind: "assign",
        title: "Intersección",
        shortLabel: "Clasifica",
        prompt: "Separa los países de América del Norte de los que quedan más abajo.",
        leftLabel: "América del Norte",
        rightLabel: "Fuera",
        terms: [
          { id: "mexico", label: "México" },
          { id: "eeuu", label: "Estados Unidos" },
          { id: "canada", label: "Canadá" },
          { id: "brasil", label: "Brasil" },
          { id: "argentina", label: "Argentina" },
          { id: "chile", label: "Chile" }
        ],
        answers: {
          mexico: "left",
          eeuu: "left",
          canada: "left",
          brasil: "right",
          argentina: "right",
          chile: "right"
        },
        source: { label: "SRE", url: "https://www.gob.mx/sre" }
      }
    }
  }
);


/* Paquete 4: rondas de sobremesa con respuestas de la casa. */
const casaSource = { label: "La casa dice", url: "https://party.visionplusv2.duckdns.org/" };
schaetzoramaRounds.push(
  {
    roundIndex: 31,
    roundLabel: "La taquiza",
    questions: {
      number: { id: "casa-taquiza-tacos", categoryId: "number", kind: "number", title: "De 0 a 30", shortLabel: "Número", prompt: "La casa dice: ¿cuántos tacos se necesitan para que una taquiza se considere exitosa?", min: 0, max: 30, answer: 18, source: casaSource },
      percent: { id: "casa-taquiza-salsa", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje de la taquiza se va en salsa y limones?", min: 0, max: 100, answer: 25, unitLabel: "%", source: casaSource },
      rank: { id: "casa-taquiza-desmadre", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena estos tacos del más desmadroso al más tranquilo.", directionLabel: "del más desmadroso al más tranquilo", items: [{ id: "pastor", label: "Al pastor" }, { id: "suadero", label: "Suadero" }, { id: "canasta", label: "De canasta" }], answerOrder: ["pastor", "suadero", "canasta"], source: casaSource },
      assign: { id: "casa-taquiza-dentro", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos ingredientes: van dentro del taco o van aparte.", leftLabel: "Dentro", rightLabel: "Aparte", terms: [{ id: "pina", label: "Piña" }, { id: "cebolla", label: "Cebolla" }, { id: "cilantro", label: "Cilantro" }, { id: "limon", label: "Limón" }, { id: "salsa", label: "Salsa" }], answers: { pina: "left", cebolla: "left", cilantro: "left", limon: "right", salsa: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 32,
    roundLabel: "La familia",
    questions: {
      number: { id: "casa-familia-novia", categoryId: "number", kind: "number", title: "De 0 a 20", shortLabel: "Número", prompt: "La casa dice: ¿cuántas veces te preguntaron “y la novia?” en una comida familiar?", min: 0, max: 20, answer: 4, source: casaSource },
      percent: { id: "casa-familia-chisme", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje de la sobremesa familiar es chisme?", min: 0, max: 100, answer: 80, unitLabel: "%", source: casaSource },
      rank: { id: "casa-familia-peligro", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por nivel de peligro: tía política, suegra y primo que vende herbalife.", directionLabel: "del más peligroso al menos", items: [{ id: "tia", label: "Tía política" }, { id: "suegra", label: "Suegra" }, { id: "primo", label: "Primo de Herbalife" }], answerOrder: ["tia", "suegra", "primo"], source: casaSource },
      assign: { id: "casa-familia-temas", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos temas: se hablan en la mesa o se evitan.", leftLabel: "Se habla", rightLabel: "Se evita", terms: [{ id: "politica", label: "Política" }, { id: "futbol", label: "Fútbol" }, { id: "salud", label: "Salud" }, { id: "ex", label: "La ex" }, { id: "dinero", label: "Dinero" }], answers: { politica: "left", futbol: "left", salud: "left", ex: "right", dinero: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 33,
    roundLabel: "La oficina",
    questions: {
      number: { id: "casa-oficina-juntas", categoryId: "number", kind: "number", title: "De 0 a 10", shortLabel: "Número", prompt: "La casa dice: ¿cuántas juntas necesitas para decidir la siguiente junta?", min: 0, max: 10, answer: 3, source: casaSource },
      percent: { id: "casa-oficina-fantasma", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje de la oficina está en modo fantasma los viernes?", min: 0, max: 100, answer: 70, unitLabel: "%", source: casaSource },
      rank: { id: "casa-oficina-sueno", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por nivel de sueño: comida pesada, junta de las 8 y café.", directionLabel: "del más somnífero al menos", items: [{ id: "comida", label: "Comida pesada" }, { id: "junta", label: "Junta de las 8" }, { id: "cafe", label: "Café" }], answerOrder: ["comida", "junta", "cafe"], source: casaSource },
      assign: { id: "casa-oficina-correos", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos correos: son urgentes o pueden esperar.", leftLabel: "Urgente", rightLabel: "Puede esperar", terms: [{ id: "jefe", label: "Correo del jefe" }, { id: "cliente", label: "Queja de cliente" }, { id: "spam", label: "Spam" }, { id: "cumple", label: "Cumpleaños" }, { id: "meme", label: "Meme del grupo" }], answers: { jefe: "left", cliente: "left", spam: "right", cumple: "right", meme: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 34,
    roundLabel: "El antro",
    questions: {
      number: { id: "casa-antro-canciones", categoryId: "number", kind: "number", title: "De 0 a 20", shortLabel: "Número", prompt: "La casa dice: ¿cuántas canciones de reguetón aguantas antes de pedir cumbia?", min: 0, max: 20, answer: 3, source: casaSource },
      percent: { id: "casa-antro-bano", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje del antro está en el baño en algún momento?", min: 0, max: 100, answer: 20, unitLabel: "%", source: casaSource },
      rank: { id: "casa-antro-caos", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por nivel de caos: entrada, barra y pista.", directionLabel: "de menos a más caos", items: [{ id: "entrada", label: "Entrada" }, { id: "barra", label: "Barra" }, { id: "pista", label: "Pista" }], answerOrder: ["entrada", "barra", "pista"], source: casaSource },
      assign: { id: "casa-antro-canta", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos géneros: se cantan o se bailan.", leftLabel: "Se canta", rightLabel: "Se baila", terms: [{ id: "karaoke", label: "Karaoke" }, { id: "cumbia", label: "Cumbia" }, { id: "rock", label: "Rock" }, { id: "balada", label: "Balada" }, { id: "reggaeton", label: "Reguetón" }], answers: { karaoke: "left", cumbia: "right", rock: "left", balada: "left", reggaeton: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 35,
    roundLabel: "El mercado",
    questions: {
      number: { id: "casa-mercado-regateo", categoryId: "number", kind: "number", title: "De 0 a 100", shortLabel: "Número", prompt: "La casa dice: ¿cuántos pesos regateas en el tianguis?", min: 0, max: 100, answer: 20, source: casaSource },
      percent: { id: "casa-mercado-antojos", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje del mandado se va en antojos?", min: 0, max: 100, answer: 30, unitLabel: "%", source: casaSource },
      rank: { id: "casa-mercado-aguacate", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena el precio del aguacate: barato, justo y “está caro”.", directionLabel: "de menos a más caro", items: [{ id: "barato", label: "Barato" }, { id: "justo", label: "Justo" }, { id: "caro", label: "Está caro" }], answerOrder: ["barato", "justo", "caro"], source: casaSource },
      assign: { id: "casa-mercado-donde", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estas compras: van al mercado o al súper.", leftLabel: "Mercado", rightLabel: "Súper", terms: [{ id: "aguacate", label: "Aguacate" }, { id: "jitomate", label: "Jitomate" }, { id: "cilantro", label: "Cilantro" }, { id: "cereal", label: "Cereal" }, { id: "papel", label: "Papel higiénico" }], answers: { aguacate: "left", jitomate: "left", cilantro: "left", cereal: "right", papel: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 36,
    roundLabel: "La escuela",
    questions: {
      number: { id: "casa-escuela-tarea", categoryId: "number", kind: "number", title: "De 0 a 20", shortLabel: "Número", prompt: "La casa dice: ¿cuántas veces copiaste la tarea en la escuela?", min: 0, max: 20, answer: 5, source: casaSource },
      percent: { id: "casa-escuela-entiende", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje de la clase de verdad entiende al profe?", min: 0, max: 100, answer: 30, unitLabel: "%", source: casaSource },
      rank: { id: "casa-escuela-dificil", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por dificultad: matemáticas, historia y deportes.", directionLabel: "de más difícil a más fácil", items: [{ id: "mate", label: "Matemáticas" }, { id: "historia", label: "Historia" }, { id: "deportes", label: "Deportes" }], answerOrder: ["mate", "historia", "deportes"], source: casaSource },
      assign: { id: "casa-escuela-lugar", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos objetos: se usan en la escuela o en la casa.", leftLabel: "Escuela", rightLabel: "Casa", terms: [{ id: "cuaderno", label: "Cuaderno" }, { id: "lapiz", label: "Lápiz" }, { id: "mochila", label: "Mochila" }, { id: "cama", label: "Cama" }, { id: "refri", label: "Refrigerador" }], answers: { cuaderno: "left", lapiz: "left", mochila: "left", cama: "right", refri: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 37,
    roundLabel: "La televisión",
    questions: {
      number: { id: "casa-tv-maraton", categoryId: "number", kind: "number", title: "De 0 a 20", shortLabel: "Número", prompt: "La casa dice: ¿cuántos capítulos ves en un maratón?", min: 0, max: 20, answer: 6, source: casaSource },
      percent: { id: "casa-tv-celular", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje del maratón terminas viendo en el celular?", min: 0, max: 100, answer: 40, unitLabel: "%", source: casaSource },
      rank: { id: "casa-tv-duracion", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por duración: comercial, capítulo y película.", directionLabel: "de menos a más", items: [{ id: "comercial", label: "Comercial" }, { id: "capitulo", label: "Capítulo" }, { id: "pelicula", label: "Película" }], answerOrder: ["comercial", "capitulo", "pelicula"], source: casaSource },
      assign: { id: "casa-tv-pantalla", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos contenidos: se ven en la tele o en el celular.", leftLabel: "Tele", rightLabel: "Celular", terms: [{ id: "novela", label: "Telenovela" }, { id: "noticiero", label: "Noticiero" }, { id: "partido", label: "Partido" }, { id: "tiktok", label: "TikTok" }, { id: "youtube", label: "YouTube" }], answers: { novela: "left", noticiero: "left", partido: "left", tiktok: "right", youtube: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 38,
    roundLabel: "El deporte",
    questions: {
      number: { id: "casa-deporte-chelas", categoryId: "number", kind: "number", title: "De 0 a 12", shortLabel: "Número", prompt: "La casa dice: ¿cuántas chelas te tomas viendo el partido?", min: 0, max: 12, answer: 3, source: casaSource },
      percent: { id: "casa-deporte-drama", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje del partido es puro drama?", min: 0, max: 100, answer: 80, unitLabel: "%", source: casaSource },
      rank: { id: "casa-deporte-gritos", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por nivel de grito: tarjeta, penal y gol.", directionLabel: "de menos a más grito", items: [{ id: "tarjeta", label: "Tarjeta" }, { id: "penal", label: "Penal" }, { id: "gol", label: "Gol" }], answerOrder: ["tarjeta", "penal", "gol"], source: casaSource },
      assign: { id: "casa-deporte-grita", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estas jugadas: se gritan o se sufren.", leftLabel: "Se grita", rightLabel: "Se sufre", terms: [{ id: "gol", label: "Gol" }, { id: "penal", label: "Penal" }, { id: "fuera", label: "Fuera de lugar" }, { id: "lesion", label: "Lesión" }, { id: "var", label: "VAR" }], answers: { gol: "left", penal: "left", fuera: "left", lesion: "right", var: "right" }, source: casaSource }
    }
  },
  {
    roundIndex: 39,
    roundLabel: "La casa",
    questions: {
      number: { id: "casa-hogar-focos", categoryId: "number", kind: "number", title: "De 0 a 20", shortLabel: "Número", prompt: "La casa dice: ¿cuántos focos fundidos hay en tu casa?", min: 0, max: 20, answer: 3, source: casaSource },
      percent: { id: "casa-hogar-abuela", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje del refrigerador es comida de la abuela?", min: 0, max: 100, answer: 30, unitLabel: "%", source: casaSource },
      rank: { id: "casa-hogar-peligro", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por peligro: perro, piso mojado y escalera.", directionLabel: "de menos a más peligro", items: [{ id: "perro", label: "Perro" }, { id: "piso", label: "Piso mojado" }, { id: "escalera", label: "Escalera" }], answerOrder: ["perro", "piso", "escalera"], source: casaSource },
      assign: { id: "casa-hogar-cuarto", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos objetos: viven en la sala o en la cocina.", leftLabel: "Sala", rightLabel: "Cocina", terms: [{ id: "sofa", label: "Sofá" }, { id: "tele", label: "Televisión" }, { id: "sarten", label: "Sartén" }, { id: "refri", label: "Refrigerador" }, { id: "cojin", label: "Cojín" }], answers: { sofa: "left", tele: "left", sarten: "right", refri: "right", cojin: "left" }, source: casaSource }
    }
  },
  {
    roundIndex: 40,
    roundLabel: "El transporte",
    questions: {
      number: { id: "casa-transporte-extra", categoryId: "number", kind: "number", title: "De 0 a 60", shortLabel: "Número", prompt: "La casa dice: ¿cuántos minutos extra calculas para llegar?", min: 0, max: 60, answer: 15, source: casaSource },
      percent: { id: "casa-transporte-celular", categoryId: "percent", kind: "percent", title: "Porcentaje", shortLabel: "Porcentaje", prompt: "¿Qué porcentaje del trayecto vas viendo el celular?", min: 0, max: 100, answer: 50, unitLabel: "%", source: casaSource },
      rank: { id: "casa-transporte-estres", categoryId: "rank", kind: "rank", title: "En orden", shortLabel: "Orden", prompt: "Ordena por estrés: bici, metro y camión.", directionLabel: "de menos a más estrés", items: [{ id: "bici", label: "Bici" }, { id: "metro", label: "Metro" }, { id: "camion", label: "Camión" }], answerOrder: ["bici", "metro", "camion"], source: casaSource },
      assign: { id: "casa-transporte-via", categoryId: "assign", kind: "assign", title: "Intersección", shortLabel: "Clasifica", prompt: "Clasifica estos transportes: van en rieles o en asfalto.", leftLabel: "Rieles", rightLabel: "Asfalto", terms: [{ id: "metro", label: "Metro" }, { id: "tren", label: "Tren ligero" }, { id: "tranvia", label: "Tranvía" }, { id: "camion", label: "Camión" }, { id: "taxi", label: "Taxi" }], answers: { metro: "left", tren: "left", tranvia: "left", camion: "right", taxi: "right" }, source: casaSource }
    }
  }
);

/* Paquete 5: 60 rondas de sobremesa con respuestas de la casa. */
schaetzoramaRounds.push(
...([
  {
    "roundIndex": 41,
    "roundLabel": "La boda",
    "questions": {
      "number": {
        "id": "casa-la-boda-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos invitados de más aparecen sin avisar?",
        "min": 0,
        "max": 30,
        "answer": 6,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-boda-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la boda es drama familiar?",
        "min": 0,
        "max": 100,
        "answer": 35,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-boda-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por nivel de llanto: novia, tía y DJ.",
        "directionLabel": "de más a menos llanto",
        "items": [
          {
            "id": "novia",
            "label": "Novia"
          },
          {
            "id": "tia",
            "label": "Tía"
          },
          {
            "id": "dj",
            "label": "DJ"
          }
        ],
        "answerOrder": [
          "novia",
          "tia",
          "dj"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-boda-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se usa en la boda o se evita.",
        "leftLabel": "En la boda",
        "rightLabel": "Se evita",
        "terms": [
          {
            "id": "ramo",
            "label": "Ramo"
          },
          {
            "id": "vals",
            "label": "Vals"
          },
          {
            "id": "pastel",
            "label": "Pastel"
          },
          {
            "id": "ex",
            "label": "Ex"
          },
          {
            "id": "chisme",
            "label": "Chisme"
          }
        ],
        "answers": {
          "ramo": "left",
          "vals": "left",
          "pastel": "left",
          "ex": "right",
          "chisme": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 42,
    "roundLabel": "El cumpleaños",
    "questions": {
      "number": {
        "id": "casa-el-cumpleanos-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas velas se vuelven a encender?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-cumpleanos-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del pastel se desperdicia?",
        "min": 0,
        "max": 100,
        "answer": 20,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-cumpleanos-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por emoción: pastel, regalos y mañanitas.",
        "directionLabel": "de más a menos emoción",
        "items": [
          {
            "id": "pastel",
            "label": "Pastel"
          },
          {
            "id": "regalos",
            "label": "Regalos"
          },
          {
            "id": "mananitas",
            "label": "Mañanitas"
          }
        ],
        "answerOrder": [
          "pastel",
          "regalos",
          "mananitas"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-cumpleanos-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es cumpleaños de niño o de adulto.",
        "leftLabel": "De niño",
        "rightLabel": "De adulto",
        "terms": [
          {
            "id": "pinata",
            "label": "Piñata"
          },
          {
            "id": "gelatina",
            "label": "Gelatina"
          },
          {
            "id": "botellas",
            "label": "Botellas"
          },
          {
            "id": "corbata",
            "label": "Corbata"
          },
          {
            "id": "discurso",
            "label": "Discurso"
          }
        ],
        "answers": {
          "pinata": "left",
          "gelatina": "left",
          "botellas": "right",
          "corbata": "right",
          "discurso": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 43,
    "roundLabel": "La posada",
    "questions": {
      "number": {
        "id": "casa-la-posada-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 15",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas posadas aguantas en diciembre?",
        "min": 0,
        "max": 15,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-posada-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la posada es ponche?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-posada-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por frío: ponche, abrazo y piñata.",
        "directionLabel": "de más a menos frío",
        "items": [
          {
            "id": "ponche",
            "label": "Ponche"
          },
          {
            "id": "abrazo",
            "label": "Abrazo"
          },
          {
            "id": "pinata",
            "label": "Piñata"
          }
        ],
        "answerOrder": [
          "ponche",
          "pinata",
          "abrazo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-posada-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en la posada o no.",
        "leftLabel": "Va en la posada",
        "rightLabel": "No va",
        "terms": [
          {
            "id": "pinata",
            "label": "Piñata"
          },
          {
            "id": "ponche",
            "label": "Ponche"
          },
          {
            "id": "aguinaldo",
            "label": "Aguinaldo"
          },
          {
            "id": "ofrenda",
            "label": "Ofrenda"
          },
          {
            "id": "huevo",
            "label": "Huevo"
          }
        ],
        "answers": {
          "pinata": "left",
          "ponche": "left",
          "aguinaldo": "left",
          "ofrenda": "right",
          "huevo": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 44,
    "roundLabel": "Día de Muertos",
    "questions": {
      "number": {
        "id": "casa-dia-de-muertos-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 10",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos panes de muerto te comes?",
        "min": 0,
        "max": 10,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-dia-de-muertos-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la ofrenda es comida?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-dia-de-muertos-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por dulzura: calaverita, pan de muerto y chocolate.",
        "directionLabel": "de más a menos dulce",
        "items": [
          {
            "id": "calaverita",
            "label": "Calaverita"
          },
          {
            "id": "pan-de-muerto",
            "label": "Pan de muerto"
          },
          {
            "id": "chocolate",
            "label": "Chocolate"
          }
        ],
        "answerOrder": [
          "calaverita",
          "pan-de-muerto",
          "chocolate"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-dia-de-muertos-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en la ofrenda o no.",
        "leftLabel": "En la ofrenda",
        "rightLabel": "Fuera",
        "terms": [
          {
            "id": "cempasuchil",
            "label": "Cempasúchil"
          },
          {
            "id": "calaverita",
            "label": "Calaverita"
          },
          {
            "id": "pan-de-muerto",
            "label": "Pan de muerto"
          },
          {
            "id": "pinata",
            "label": "Piñata"
          },
          {
            "id": "arbol-navideno",
            "label": "Árbol navideño"
          }
        ],
        "answers": {
          "cempasuchil": "left",
          "calaverita": "left",
          "pan-de-muerto": "left",
          "pinata": "right",
          "arbol-navideno": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 45,
    "roundLabel": "La playa",
    "questions": {
      "number": {
        "id": "casa-la-playa-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 12",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas aguantas sin sombra?",
        "min": 0,
        "max": 12,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-playa-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del viaje estás en el agua?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-playa-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por peligro: sol, coco y arena.",
        "directionLabel": "de más a menos peligro",
        "items": [
          {
            "id": "sol",
            "label": "Sol"
          },
          {
            "id": "coco",
            "label": "Coco"
          },
          {
            "id": "arena",
            "label": "Arena"
          }
        ],
        "answerOrder": [
          "sol",
          "coco",
          "arena"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-playa-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va a la playa o no.",
        "leftLabel": "A la playa",
        "rightLabel": "No va",
        "terms": [
          {
            "id": "bloqueador",
            "label": "Bloqueador"
          },
          {
            "id": "toalla",
            "label": "Toalla"
          },
          {
            "id": "hielera",
            "label": "Hielera"
          },
          {
            "id": "sueter",
            "label": "Suéter"
          },
          {
            "id": "corbata",
            "label": "Corbata"
          }
        ],
        "answers": {
          "bloqueador": "left",
          "toalla": "left",
          "hielera": "left",
          "sueter": "right",
          "corbata": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 46,
    "roundLabel": "El tianguis",
    "questions": {
      "number": {
        "id": "casa-el-tianguis-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 100",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos pesos regateas?",
        "min": 0,
        "max": 100,
        "answer": 20,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-tianguis-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de compras no necesitabas?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-tianguis-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por regateo: electrónico, ropa y fruta.",
        "directionLabel": "de más a menos regateo",
        "items": [
          {
            "id": "electronico",
            "label": "Electrónico"
          },
          {
            "id": "ropa",
            "label": "Ropa"
          },
          {
            "id": "fruta",
            "label": "Fruta"
          }
        ],
        "answerOrder": [
          "electronico",
          "ropa",
          "fruta"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-tianguis-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se compra en tianguis o súper.",
        "leftLabel": "Tianguis",
        "rightLabel": "Súper",
        "terms": [
          {
            "id": "aguacate",
            "label": "Aguacate"
          },
          {
            "id": "ropa",
            "label": "Ropa"
          },
          {
            "id": "tortillas",
            "label": "Tortillas"
          },
          {
            "id": "cereal",
            "label": "Cereal"
          },
          {
            "id": "papel",
            "label": "Papel"
          }
        ],
        "answers": {
          "aguacate": "left",
          "ropa": "left",
          "tortillas": "left",
          "cereal": "right",
          "papel": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 47,
    "roundLabel": "El súper",
    "questions": {
      "number": {
        "id": "casa-el-super-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos antojos caen en el súper?",
        "min": 0,
        "max": 20,
        "answer": 5,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-super-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la compra es botana?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-super-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por peligro: hijo, carrito y caja.",
        "directionLabel": "de más a menos peligro",
        "items": [
          {
            "id": "hijo",
            "label": "Hijo"
          },
          {
            "id": "carrito",
            "label": "Carrito"
          },
          {
            "id": "caja",
            "label": "Caja"
          }
        ],
        "answerOrder": [
          "hijo",
          "carrito",
          "caja"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-super-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en la lista o es antojo.",
        "leftLabel": "Lista",
        "rightLabel": "Antojo",
        "terms": [
          {
            "id": "leche",
            "label": "Leche"
          },
          {
            "id": "huevos",
            "label": "Huevos"
          },
          {
            "id": "pan",
            "label": "Pan"
          },
          {
            "id": "botana",
            "label": "Botana"
          },
          {
            "id": "helado",
            "label": "Helado"
          }
        ],
        "answers": {
          "leche": "left",
          "huevos": "left",
          "pan": "left",
          "botana": "right",
          "helado": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 48,
    "roundLabel": "La cocina",
    "questions": {
      "number": {
        "id": "casa-la-cocina-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 1 a 15",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos trastes usas para un huevo?",
        "min": 1,
        "max": 15,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-cocina-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la cocina queda sucia?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-cocina-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por dificultad: mole, arroz y huevo.",
        "directionLabel": "de más a menos difícil",
        "items": [
          {
            "id": "mole",
            "label": "Mole"
          },
          {
            "id": "arroz",
            "label": "Arroz"
          },
          {
            "id": "huevo",
            "label": "Huevo"
          }
        ],
        "answerOrder": [
          "mole",
          "arroz",
          "huevo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-cocina-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se lava o se remoja.",
        "leftLabel": "Se lava",
        "rightLabel": "Se remoja",
        "terms": [
          {
            "id": "plato",
            "label": "Plato"
          },
          {
            "id": "vaso",
            "label": "Vaso"
          },
          {
            "id": "cuchara",
            "label": "Cuchara"
          },
          {
            "id": "olla",
            "label": "Olla"
          },
          {
            "id": "charola",
            "label": "Charola"
          }
        ],
        "answers": {
          "plato": "left",
          "vaso": "left",
          "cuchara": "left",
          "olla": "right",
          "charola": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 49,
    "roundLabel": "El café",
    "questions": {
      "number": {
        "id": "casa-el-cafe-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 10",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos cafés al día?",
        "min": 0,
        "max": 10,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-cafe-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del café es azúcar?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-cafe-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por energía: espresso, americano y descafeinado.",
        "directionLabel": "de más a menos energía",
        "items": [
          {
            "id": "espresso",
            "label": "Espresso"
          },
          {
            "id": "americano",
            "label": "Americano"
          },
          {
            "id": "descafeinado",
            "label": "Descafeinado"
          }
        ],
        "answerOrder": [
          "espresso",
          "americano",
          "descafeinado"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-cafe-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se toma con café o solo.",
        "leftLabel": "Con café",
        "rightLabel": "Solo",
        "terms": [
          {
            "id": "pan",
            "label": "Pan"
          },
          {
            "id": "galleta",
            "label": "Galleta"
          },
          {
            "id": "platica",
            "label": "Plática"
          },
          {
            "id": "agua",
            "label": "Agua"
          },
          {
            "id": "te",
            "label": "Té"
          }
        ],
        "answers": {
          "pan": "left",
          "galleta": "left",
          "platica": "left",
          "agua": "right",
          "te": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 50,
    "roundLabel": "La panadería",
    "questions": {
      "number": {
        "id": "casa-la-panaderia-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas conchas compras?",
        "min": 0,
        "max": 20,
        "answer": 4,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-panaderia-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la charola es concha?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-panaderia-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por dulzura: concha, cuernito y bolillo.",
        "directionLabel": "de más a menos dulce",
        "items": [
          {
            "id": "concha",
            "label": "Concha"
          },
          {
            "id": "cuernito",
            "label": "Cuernito"
          },
          {
            "id": "bolillo",
            "label": "Bolillo"
          }
        ],
        "answerOrder": [
          "concha",
          "cuernito",
          "bolillo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-panaderia-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es pan dulce o salado.",
        "leftLabel": "Dulce",
        "rightLabel": "Salado",
        "terms": [
          {
            "id": "concha",
            "label": "Concha"
          },
          {
            "id": "cuernito",
            "label": "Cuernito"
          },
          {
            "id": "bolillo",
            "label": "Bolillo"
          },
          {
            "id": "telera",
            "label": "Telera"
          },
          {
            "id": "baguette",
            "label": "Baguette"
          }
        ],
        "answers": {
          "concha": "left",
          "cuernito": "left",
          "bolillo": "right",
          "telera": "right",
          "baguette": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 51,
    "roundLabel": "El gym",
    "questions": {
      "number": {
        "id": "casa-el-gym-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos días vas de verdad?",
        "min": 0,
        "max": 30,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-gym-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del gym es selfie?",
        "min": 0,
        "max": 100,
        "answer": 20,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-gym-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por esfuerzo: pesas, cardio y estiramiento.",
        "directionLabel": "de más a menos esfuerzo",
        "items": [
          {
            "id": "pesas",
            "label": "Pesas"
          },
          {
            "id": "cardio",
            "label": "Cardio"
          },
          {
            "id": "estiramiento",
            "label": "Estiramiento"
          }
        ],
        "answerOrder": [
          "pesas",
          "cardio",
          "estiramiento"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-gym-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va al gym o no.",
        "leftLabel": "Al gym",
        "rightLabel": "No va",
        "terms": [
          {
            "id": "mancuerna",
            "label": "Mancuerna"
          },
          {
            "id": "tenis",
            "label": "Tenis"
          },
          {
            "id": "botella",
            "label": "Botella"
          },
          {
            "id": "sofa",
            "label": "Sofá"
          },
          {
            "id": "control",
            "label": "Control"
          }
        ],
        "answers": {
          "mancuerna": "left",
          "tenis": "left",
          "botella": "left",
          "sofa": "right",
          "control": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 52,
    "roundLabel": "El metro",
    "questions": {
      "number": {
        "id": "casa-el-metro-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos esperas el metro?",
        "min": 0,
        "max": 30,
        "answer": 5,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-metro-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del vagón va apretado?",
        "min": 0,
        "max": 100,
        "answer": 80,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-metro-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por caos: hora pico, lluvia y domingo.",
        "directionLabel": "de más a menos caos",
        "items": [
          {
            "id": "hora-pico",
            "label": "Hora pico"
          },
          {
            "id": "lluvia",
            "label": "Lluvia"
          },
          {
            "id": "domingo",
            "label": "Domingo"
          }
        ],
        "answerOrder": [
          "hora-pico",
          "lluvia",
          "domingo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-metro-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se usa en el metro o no.",
        "leftLabel": "En el metro",
        "rightLabel": "No",
        "terms": [
          {
            "id": "tarjeta",
            "label": "Tarjeta"
          },
          {
            "id": "mochila",
            "label": "Mochila"
          },
          {
            "id": "audifonos",
            "label": "Audífonos"
          },
          {
            "id": "coche",
            "label": "Coche"
          },
          {
            "id": "gasolina",
            "label": "Gasolina"
          }
        ],
        "answers": {
          "tarjeta": "left",
          "mochila": "left",
          "audifonos": "left",
          "coche": "right",
          "gasolina": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 53,
    "roundLabel": "El tráfico",
    "questions": {
      "number": {
        "id": "casa-el-trafico-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 120",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos extra calculas?",
        "min": 0,
        "max": 120,
        "answer": 30,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-trafico-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del tráfico es culpa del de adelante?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-trafico-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por estrés: tráfico, bache y semáforo.",
        "directionLabel": "de más a menos estrés",
        "items": [
          {
            "id": "trafico",
            "label": "Tráfico"
          },
          {
            "id": "bache",
            "label": "Bache"
          },
          {
            "id": "semaforo",
            "label": "Semáforo"
          }
        ],
        "answerOrder": [
          "trafico",
          "bache",
          "semaforo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-trafico-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: te hace perder tiempo o no.",
        "leftLabel": "Pierdes tiempo",
        "rightLabel": "No",
        "terms": [
          {
            "id": "trafico",
            "label": "Tráfico"
          },
          {
            "id": "obra",
            "label": "Obra"
          },
          {
            "id": "manifestacion",
            "label": "Manifestación"
          },
          {
            "id": "semaforo",
            "label": "Semáforo"
          },
          {
            "id": "radio",
            "label": "Radio"
          }
        ],
        "answers": {
          "trafico": "left",
          "obra": "left",
          "manifestacion": "left",
          "semaforo": "right",
          "radio": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 54,
    "roundLabel": "El aeropuerto",
    "questions": {
      "number": {
        "id": "casa-el-aeropuerto-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 6",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas antes llegas?",
        "min": 0,
        "max": 6,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-aeropuerto-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del aeropuerto es fila?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-aeropuerto-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por paciencia: migración, check-in y avión.",
        "directionLabel": "de más a menos paciencia",
        "items": [
          {
            "id": "migracion",
            "label": "Migración"
          },
          {
            "id": "check-in",
            "label": "Check-in"
          },
          {
            "id": "avion",
            "label": "Avión"
          }
        ],
        "answerOrder": [
          "migracion",
          "check-in",
          "avion"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-aeropuerto-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en equipaje de mano o documentado.",
        "leftLabel": "Mano",
        "rightLabel": "Documentado",
        "terms": [
          {
            "id": "pasaporte",
            "label": "Pasaporte"
          },
          {
            "id": "celular",
            "label": "Celular"
          },
          {
            "id": "cargador",
            "label": "Cargador"
          },
          {
            "id": "silla",
            "label": "Silla"
          },
          {
            "id": "sombrilla",
            "label": "Sombrilla"
          }
        ],
        "answers": {
          "pasaporte": "left",
          "celular": "left",
          "cargador": "left",
          "silla": "right",
          "sombrilla": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 55,
    "roundLabel": "La carretera",
    "questions": {
      "number": {
        "id": "casa-la-carretera-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas paradas haces?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-carretera-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del viaje es paisaje?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-carretera-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por peligro: tráiler, curva y bache.",
        "directionLabel": "de más a menos peligro",
        "items": [
          {
            "id": "trailer",
            "label": "Tráiler"
          },
          {
            "id": "curva",
            "label": "Curva"
          },
          {
            "id": "bache",
            "label": "Bache"
          }
        ],
        "answerOrder": [
          "trailer",
          "curva",
          "bache"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-carretera-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se lleva en carretera o no.",
        "leftLabel": "Se lleva",
        "rightLabel": "No",
        "terms": [
          {
            "id": "agua",
            "label": "Agua"
          },
          {
            "id": "botana",
            "label": "Botana"
          },
          {
            "id": "musica",
            "label": "Música"
          },
          {
            "id": "paraguas",
            "label": "Paraguas"
          },
          {
            "id": "corbata",
            "label": "Corbata"
          }
        ],
        "answers": {
          "agua": "left",
          "botana": "left",
          "musica": "left",
          "paraguas": "right",
          "corbata": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 56,
    "roundLabel": "El hotel",
    "questions": {
      "number": {
        "id": "casa-el-hotel-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 10",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas toallas usas?",
        "min": 0,
        "max": 10,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-hotel-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del hotel es cama?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-hotel-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por lujo: suite, doble y sencilla.",
        "directionLabel": "de más a menos lujo",
        "items": [
          {
            "id": "suite",
            "label": "Suite"
          },
          {
            "id": "doble",
            "label": "Doble"
          },
          {
            "id": "sencilla",
            "label": "Sencilla"
          }
        ],
        "answerOrder": [
          "suite",
          "doble",
          "sencilla"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-hotel-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se queda en el hotel o se lleva.",
        "leftLabel": "Se queda",
        "rightLabel": "Se lleva",
        "terms": [
          {
            "id": "toalla",
            "label": "Toalla"
          },
          {
            "id": "control",
            "label": "Control"
          },
          {
            "id": "almohada",
            "label": "Almohada"
          },
          {
            "id": "shampoo",
            "label": "Shampoo"
          },
          {
            "id": "cafe",
            "label": "Café"
          }
        ],
        "answers": {
          "toalla": "left",
          "control": "left",
          "almohada": "left",
          "shampoo": "right",
          "cafe": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 57,
    "roundLabel": "Home office",
    "questions": {
      "number": {
        "id": "casa-home-office-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 15",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas juntas tienes al día?",
        "min": 0,
        "max": 15,
        "answer": 4,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-home-office-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del día estás en pijama?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-home-office-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por caos: internet, junta y café.",
        "directionLabel": "de más a menos caos",
        "items": [
          {
            "id": "internet",
            "label": "Internet"
          },
          {
            "id": "junta",
            "label": "Junta"
          },
          {
            "id": "cafe",
            "label": "Café"
          }
        ],
        "answerOrder": [
          "internet",
          "junta",
          "cafe"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-home-office-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es de oficina o de casa.",
        "leftLabel": "Oficina",
        "rightLabel": "Casa",
        "terms": [
          {
            "id": "laptop",
            "label": "Laptop"
          },
          {
            "id": "pijama",
            "label": "Pijama"
          },
          {
            "id": "cafe",
            "label": "Café"
          },
          {
            "id": "cama",
            "label": "Cama"
          },
          {
            "id": "silla",
            "label": "Silla"
          }
        ],
        "answers": {
          "laptop": "left",
          "pijama": "right",
          "cafe": "left",
          "cama": "right",
          "silla": "left"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 58,
    "roundLabel": "La videollamada",
    "questions": {
      "number": {
        "id": "casa-la-videollamada-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos esperas a que entren?",
        "min": 0,
        "max": 30,
        "answer": 5,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-videollamada-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la llamada estás muteado?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-videollamada-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por vergüenza: cámara, mute y fondo.",
        "directionLabel": "de más a menos vergüenza",
        "items": [
          {
            "id": "camara",
            "label": "Cámara"
          },
          {
            "id": "mute",
            "label": "Mute"
          },
          {
            "id": "fondo",
            "label": "Fondo"
          }
        ],
        "answerOrder": [
          "camara",
          "mute",
          "fondo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-videollamada-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se hace en videollamada o no.",
        "leftLabel": "En llamada",
        "rightLabel": "No",
        "terms": [
          {
            "id": "compartir-pantalla",
            "label": "Compartir pantalla"
          },
          {
            "id": "mute",
            "label": "Mute"
          },
          {
            "id": "fondo-virtual",
            "label": "Fondo virtual"
          },
          {
            "id": "aplauso",
            "label": "Aplauso"
          },
          {
            "id": "abrazo",
            "label": "Abrazo"
          }
        ],
        "answers": {
          "compartir-pantalla": "left",
          "mute": "left",
          "fondo-virtual": "left",
          "aplauso": "right",
          "abrazo": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 59,
    "roundLabel": "El grupo de WhatsApp",
    "questions": {
      "number": {
        "id": "casa-el-grupo-de-whatsapp-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 999",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos mensajes sin leer tienes?",
        "min": 0,
        "max": 999,
        "answer": 99,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-grupo-de-whatsapp-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del grupo son cadenas?",
        "min": 0,
        "max": 100,
        "answer": 70,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-grupo-de-whatsapp-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por ruido: familia, vecinos y trabajo.",
        "directionLabel": "de más a menos ruido",
        "items": [
          {
            "id": "familia",
            "label": "Familia"
          },
          {
            "id": "vecinos",
            "label": "Vecinos"
          },
          {
            "id": "trabajo",
            "label": "Trabajo"
          }
        ],
        "answerOrder": [
          "familia",
          "vecinos",
          "trabajo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-grupo-de-whatsapp-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se manda al grupo o no.",
        "leftLabel": "Al grupo",
        "rightLabel": "No",
        "terms": [
          {
            "id": "meme",
            "label": "Meme"
          },
          {
            "id": "cadena",
            "label": "Cadena"
          },
          {
            "id": "audio",
            "label": "Audio"
          },
          {
            "id": "carta",
            "label": "Carta"
          },
          {
            "id": "telegrama",
            "label": "Telegrama"
          }
        ],
        "answers": {
          "meme": "left",
          "cadena": "left",
          "audio": "left",
          "carta": "right",
          "telegrama": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 60,
    "roundLabel": "Las redes",
    "questions": {
      "number": {
        "id": "casa-las-redes-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 600",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos al día en redes?",
        "min": 0,
        "max": 600,
        "answer": 180,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-las-redes-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del feed es basura?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-las-redes-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por adicción: TikTok, Instagram y LinkedIn.",
        "directionLabel": "de más a menos adicción",
        "items": [
          {
            "id": "tiktok",
            "label": "TikTok"
          },
          {
            "id": "instagram",
            "label": "Instagram"
          },
          {
            "id": "linkedin",
            "label": "LinkedIn"
          }
        ],
        "answerOrder": [
          "tiktok",
          "instagram",
          "linkedin"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-las-redes-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se publica o se guarda.",
        "leftLabel": "Se publica",
        "rightLabel": "Se guarda",
        "terms": [
          {
            "id": "selfie",
            "label": "Selfie"
          },
          {
            "id": "comida",
            "label": "Comida"
          },
          {
            "id": "meme",
            "label": "Meme"
          },
          {
            "id": "contrasena",
            "label": "Contraseña"
          },
          {
            "id": "terapeuta",
            "label": "Terapeuta"
          }
        ],
        "answers": {
          "selfie": "left",
          "comida": "left",
          "meme": "left",
          "contrasena": "right",
          "terapeuta": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 61,
    "roundLabel": "El celular",
    "questions": {
      "number": {
        "id": "casa-el-celular-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas apps abres al despertar?",
        "min": 0,
        "max": 20,
        "answer": 4,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-celular-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de batería se va en redes?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-celular-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por batería: juego, video y mapa.",
        "directionLabel": "de más a menos batería",
        "items": [
          {
            "id": "juego",
            "label": "Juego"
          },
          {
            "id": "video",
            "label": "Video"
          },
          {
            "id": "mapa",
            "label": "Mapa"
          }
        ],
        "answerOrder": [
          "juego",
          "video",
          "mapa"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-celular-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: usa batería o no.",
        "leftLabel": "Usa batería",
        "rightLabel": "No",
        "terms": [
          {
            "id": "wifi",
            "label": "Wifi"
          },
          {
            "id": "bluetooth",
            "label": "Bluetooth"
          },
          {
            "id": "gps",
            "label": "GPS"
          },
          {
            "id": "papel",
            "label": "Papel"
          },
          {
            "id": "lapiz",
            "label": "Lápiz"
          }
        ],
        "answers": {
          "wifi": "left",
          "bluetooth": "left",
          "gps": "left",
          "papel": "right",
          "lapiz": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 62,
    "roundLabel": "La batería",
    "questions": {
      "number": {
        "id": "casa-la-bateria-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 100",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿a qué porcentaje entras en pánico?",
        "min": 0,
        "max": 100,
        "answer": 15,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-bateria-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de carga dura el 1%?",
        "min": 0,
        "max": 100,
        "answer": 2,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-bateria-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por duración: 100%, 20% y 1%.",
        "directionLabel": "de más a menos duración",
        "items": [
          {
            "id": "100",
            "label": "100%"
          },
          {
            "id": "20",
            "label": "20%"
          },
          {
            "id": "1",
            "label": "1%"
          }
        ],
        "answerOrder": [
          "100",
          "20",
          "1"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-bateria-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: gasta batería o no.",
        "leftLabel": "Gasta",
        "rightLabel": "No",
        "terms": [
          {
            "id": "brillo",
            "label": "Brillo"
          },
          {
            "id": "gps",
            "label": "GPS"
          },
          {
            "id": "video",
            "label": "Video"
          },
          {
            "id": "modo-avion",
            "label": "Modo avión"
          },
          {
            "id": "cargar",
            "label": "Cargar"
          }
        ],
        "answers": {
          "brillo": "left",
          "gps": "left",
          "video": "left",
          "modo-avion": "right",
          "cargar": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 63,
    "roundLabel": "El streaming",
    "questions": {
      "number": {
        "id": "casa-el-streaming-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos capítulos ves en un maratón?",
        "min": 0,
        "max": 30,
        "answer": 6,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-streaming-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del catálogo ya viste?",
        "min": 0,
        "max": 100,
        "answer": 20,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-streaming-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por duración: comercial, capítulo y película.",
        "directionLabel": "de menos a más duración",
        "items": [
          {
            "id": "comercial",
            "label": "Comercial"
          },
          {
            "id": "capitulo",
            "label": "Capítulo"
          },
          {
            "id": "pelicula",
            "label": "Película"
          }
        ],
        "answerOrder": [
          "comercial",
          "capitulo",
          "pelicula"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-streaming-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se ve en streaming o no.",
        "leftLabel": "Streaming",
        "rightLabel": "No",
        "terms": [
          {
            "id": "serie",
            "label": "Serie"
          },
          {
            "id": "pelicula",
            "label": "Película"
          },
          {
            "id": "documental",
            "label": "Documental"
          },
          {
            "id": "cine",
            "label": "Cine"
          },
          {
            "id": "palomitas",
            "label": "Palomitas"
          }
        ],
        "answers": {
          "serie": "left",
          "pelicula": "left",
          "documental": "left",
          "cine": "right",
          "palomitas": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 64,
    "roundLabel": "La música",
    "questions": {
      "number": {
        "id": "casa-la-musica-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas canciones repites?",
        "min": 0,
        "max": 20,
        "answer": 4,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-musica-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del playlist saltas?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-musica-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por volumen: banda, cumbia y balada.",
        "directionLabel": "de más a menos volumen",
        "items": [
          {
            "id": "banda",
            "label": "Banda"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          },
          {
            "id": "balada",
            "label": "Balada"
          }
        ],
        "answerOrder": [
          "banda",
          "cumbia",
          "balada"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-musica-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se canta o se baila.",
        "leftLabel": "Se canta",
        "rightLabel": "Se baila",
        "terms": [
          {
            "id": "balada",
            "label": "Balada"
          },
          {
            "id": "rock",
            "label": "Rock"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          },
          {
            "id": "regueton",
            "label": "Reguetón"
          },
          {
            "id": "karaoke",
            "label": "Karaoke"
          }
        ],
        "answers": {
          "balada": "left",
          "rock": "left",
          "cumbia": "right",
          "regueton": "right",
          "karaoke": "left"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 65,
    "roundLabel": "El karaoke",
    "questions": {
      "number": {
        "id": "casa-el-karaoke-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas canciones pides?",
        "min": 0,
        "max": 20,
        "answer": 5,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-karaoke-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del karaoke es desafinado?",
        "min": 0,
        "max": 100,
        "answer": 80,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-karaoke-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por vergüenza: balada, rock y cumbia.",
        "directionLabel": "de más a menos vergüenza",
        "items": [
          {
            "id": "balada",
            "label": "Balada"
          },
          {
            "id": "rock",
            "label": "Rock"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          }
        ],
        "answerOrder": [
          "balada",
          "rock",
          "cumbia"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-karaoke-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: canción de karaoke o no.",
        "leftLabel": "Karaoke",
        "rightLabel": "No",
        "terms": [
          {
            "id": "bolero",
            "label": "Bolero"
          },
          {
            "id": "rock",
            "label": "Rock"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          },
          {
            "id": "himno",
            "label": "Himno"
          },
          {
            "id": "comercial",
            "label": "Comercial"
          }
        ],
        "answers": {
          "bolero": "left",
          "rock": "left",
          "cumbia": "left",
          "himno": "right",
          "comercial": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 66,
    "roundLabel": "La banda",
    "questions": {
      "number": {
        "id": "casa-la-banda-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos músicos trae la banda?",
        "min": 0,
        "max": 30,
        "answer": 12,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-banda-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del volumen es tuba?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-banda-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por volumen: tuba, trompeta y clarinete.",
        "directionLabel": "de más a menos volumen",
        "items": [
          {
            "id": "tuba",
            "label": "Tuba"
          },
          {
            "id": "trompeta",
            "label": "Trompeta"
          },
          {
            "id": "clarinete",
            "label": "Clarinete"
          }
        ],
        "answerOrder": [
          "tuba",
          "trompeta",
          "clarinete"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-banda-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: instrumento de banda o no.",
        "leftLabel": "De banda",
        "rightLabel": "No",
        "terms": [
          {
            "id": "tuba",
            "label": "Tuba"
          },
          {
            "id": "trompeta",
            "label": "Trompeta"
          },
          {
            "id": "tambora",
            "label": "Tambora"
          },
          {
            "id": "violin",
            "label": "Violín"
          },
          {
            "id": "piano",
            "label": "Piano"
          }
        ],
        "answers": {
          "tuba": "left",
          "trompeta": "left",
          "tambora": "left",
          "violin": "right",
          "piano": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 67,
    "roundLabel": "El baile",
    "questions": {
      "number": {
        "id": "casa-el-baile-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas canciones aguantas bailando?",
        "min": 0,
        "max": 20,
        "answer": 5,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-baile-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del baile es improvisación?",
        "min": 0,
        "max": 100,
        "answer": 70,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-baile-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por dificultad: ballet, salsa y cumbia.",
        "directionLabel": "de más a menos difícil",
        "items": [
          {
            "id": "ballet",
            "label": "Ballet"
          },
          {
            "id": "salsa",
            "label": "Salsa"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          }
        ],
        "answerOrder": [
          "ballet",
          "salsa",
          "cumbia"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-baile-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se baila o no.",
        "leftLabel": "Se baila",
        "rightLabel": "No",
        "terms": [
          {
            "id": "salsa",
            "label": "Salsa"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          },
          {
            "id": "regueton",
            "label": "Reguetón"
          },
          {
            "id": "llorar",
            "label": "Llorar"
          },
          {
            "id": "dormir",
            "label": "Dormir"
          }
        ],
        "answers": {
          "salsa": "left",
          "cumbia": "left",
          "regueton": "left",
          "llorar": "right",
          "dormir": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 68,
    "roundLabel": "La fiesta",
    "questions": {
      "number": {
        "id": "casa-la-fiesta-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 12",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas duras en la fiesta?",
        "min": 0,
        "max": 12,
        "answer": 4,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-fiesta-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la fiesta es comida?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-fiesta-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por energía: inicio, brindis y final.",
        "directionLabel": "de más a menos energía",
        "items": [
          {
            "id": "inicio",
            "label": "Inicio"
          },
          {
            "id": "brindis",
            "label": "Brindis"
          },
          {
            "id": "final",
            "label": "Final"
          }
        ],
        "answerOrder": [
          "inicio",
          "brindis",
          "final"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-fiesta-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en fiesta o no.",
        "leftLabel": "Fiesta",
        "rightLabel": "No",
        "terms": [
          {
            "id": "musica",
            "label": "Música"
          },
          {
            "id": "botana",
            "label": "Botana"
          },
          {
            "id": "hielo",
            "label": "Hielo"
          },
          {
            "id": "oficina",
            "label": "Oficina"
          },
          {
            "id": "alarma",
            "label": "Alarma"
          }
        ],
        "answers": {
          "musica": "left",
          "botana": "left",
          "hielo": "left",
          "oficina": "right",
          "alarma": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 69,
    "roundLabel": "El bar",
    "questions": {
      "number": {
        "id": "casa-el-bar-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas rondas pides?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-bar-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del bar es chisme?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-bar-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por precio: cerveza, coctel y botella.",
        "directionLabel": "de menos a más precio",
        "items": [
          {
            "id": "cerveza",
            "label": "Cerveza"
          },
          {
            "id": "coctel",
            "label": "Coctel"
          },
          {
            "id": "botella",
            "label": "Botella"
          }
        ],
        "answerOrder": [
          "cerveza",
          "coctel",
          "botella"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-bar-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se pide en bar o no.",
        "leftLabel": "En bar",
        "rightLabel": "No",
        "terms": [
          {
            "id": "cerveza",
            "label": "Cerveza"
          },
          {
            "id": "coctel",
            "label": "Coctel"
          },
          {
            "id": "botana",
            "label": "Botana"
          },
          {
            "id": "cafe",
            "label": "Café"
          },
          {
            "id": "leche",
            "label": "Leche"
          }
        ],
        "answers": {
          "cerveza": "left",
          "coctel": "left",
          "botana": "left",
          "cafe": "right",
          "leche": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 70,
    "roundLabel": "La cantina",
    "questions": {
      "number": {
        "id": "casa-la-cantina-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas botanas gratis te dan?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-cantina-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la cantina es humo?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-cantina-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por decibelios: cantina, bar y café.",
        "directionLabel": "de más a menos ruido",
        "items": [
          {
            "id": "cantina",
            "label": "Cantina"
          },
          {
            "id": "bar",
            "label": "Bar"
          },
          {
            "id": "cafe",
            "label": "Café"
          }
        ],
        "answerOrder": [
          "cantina",
          "bar",
          "cafe"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-cantina-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se hace en cantina o no.",
        "leftLabel": "Cantina",
        "rightLabel": "No",
        "terms": [
          {
            "id": "domino",
            "label": "Dominó"
          },
          {
            "id": "cerveza",
            "label": "Cerveza"
          },
          {
            "id": "botana",
            "label": "Botana"
          },
          {
            "id": "yoga",
            "label": "Yoga"
          },
          {
            "id": "biblioteca",
            "label": "Biblioteca"
          }
        ],
        "answers": {
          "domino": "left",
          "cerveza": "left",
          "botana": "left",
          "yoga": "right",
          "biblioteca": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 71,
    "roundLabel": "El mariachi",
    "questions": {
      "number": {
        "id": "casa-el-mariachi-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas canciones pides al mariachi?",
        "min": 0,
        "max": 20,
        "answer": 4,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-mariachi-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del mariachi es sentimiento?",
        "min": 0,
        "max": 100,
        "answer": 80,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-mariachi-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por drama: bolero, ranchera y cumbia.",
        "directionLabel": "de más a menos drama",
        "items": [
          {
            "id": "bolero",
            "label": "Bolero"
          },
          {
            "id": "ranchera",
            "label": "Ranchera"
          },
          {
            "id": "cumbia",
            "label": "Cumbia"
          }
        ],
        "answerOrder": [
          "bolero",
          "ranchera",
          "cumbia"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-mariachi-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: instrumento de mariachi o no.",
        "leftLabel": "Mariachi",
        "rightLabel": "No",
        "terms": [
          {
            "id": "violin",
            "label": "Violín"
          },
          {
            "id": "trompeta",
            "label": "Trompeta"
          },
          {
            "id": "guitarra",
            "label": "Guitarra"
          },
          {
            "id": "piano",
            "label": "Piano"
          },
          {
            "id": "bateria",
            "label": "Batería"
          }
        ],
        "answers": {
          "violin": "left",
          "trompeta": "left",
          "guitarra": "left",
          "piano": "right",
          "bateria": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 72,
    "roundLabel": "La lotería",
    "questions": {
      "number": {
        "id": "casa-la-loteria-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 54",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas cartas cantan antes de ganar?",
        "min": 0,
        "max": 54,
        "answer": 20,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-loteria-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje es suerte?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-loteria-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por suerte: casino, lotería y raspadito.",
        "directionLabel": "de menos a más suerte",
        "items": [
          {
            "id": "casino",
            "label": "Casino"
          },
          {
            "id": "loteria",
            "label": "Lotería"
          },
          {
            "id": "raspadito",
            "label": "Raspadito"
          }
        ],
        "answerOrder": [
          "casino",
          "loteria",
          "raspadito"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-loteria-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se usa en lotería o no.",
        "leftLabel": "Lotería",
        "rightLabel": "No",
        "terms": [
          {
            "id": "tabla",
            "label": "Tabla"
          },
          {
            "id": "frijoles",
            "label": "Frijoles"
          },
          {
            "id": "cartas",
            "label": "Cartas"
          },
          {
            "id": "ruleta",
            "label": "Ruleta"
          },
          {
            "id": "dado",
            "label": "Dado"
          }
        ],
        "answers": {
          "tabla": "left",
          "frijoles": "left",
          "cartas": "left",
          "ruleta": "right",
          "dado": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 73,
    "roundLabel": "El casino",
    "questions": {
      "number": {
        "id": "casa-el-casino-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 1000",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuánto pierdes en el casino?",
        "min": 0,
        "max": 1000,
        "answer": 200,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-casino-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje es suerte?",
        "min": 0,
        "max": 100,
        "answer": 20,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-casino-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por riesgo: ruleta, tragamonedas y blackjack.",
        "directionLabel": "de más a menos riesgo",
        "items": [
          {
            "id": "ruleta",
            "label": "Ruleta"
          },
          {
            "id": "tragamonedas",
            "label": "Tragamonedas"
          },
          {
            "id": "blackjack",
            "label": "Blackjack"
          }
        ],
        "answerOrder": [
          "ruleta",
          "tragamonedas",
          "blackjack"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-casino-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en casino o no.",
        "leftLabel": "Casino",
        "rightLabel": "No",
        "terms": [
          {
            "id": "ficha",
            "label": "Ficha"
          },
          {
            "id": "carta",
            "label": "Carta"
          },
          {
            "id": "dado",
            "label": "Dado"
          },
          {
            "id": "bolsa",
            "label": "Bolsa"
          },
          {
            "id": "mandado",
            "label": "Mandado"
          }
        ],
        "answers": {
          "ficha": "left",
          "carta": "left",
          "dado": "left",
          "bolsa": "right",
          "mandado": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 74,
    "roundLabel": "El fútbol",
    "questions": {
      "number": {
        "id": "casa-el-futbol-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 90",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos del partido ves?",
        "min": 0,
        "max": 90,
        "answer": 60,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-futbol-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del partido es drama?",
        "min": 0,
        "max": 100,
        "answer": 80,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-futbol-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por emoción: gol, penal y saque.",
        "directionLabel": "de más a menos emoción",
        "items": [
          {
            "id": "gol",
            "label": "Gol"
          },
          {
            "id": "penal",
            "label": "Penal"
          },
          {
            "id": "saque",
            "label": "Saque"
          }
        ],
        "answerOrder": [
          "gol",
          "penal",
          "saque"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-futbol-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se grita o se sufre.",
        "leftLabel": "Se grita",
        "rightLabel": "Se sufre",
        "terms": [
          {
            "id": "gol",
            "label": "Gol"
          },
          {
            "id": "penal",
            "label": "Penal"
          },
          {
            "id": "fuera-de-lugar",
            "label": "Fuera de lugar"
          },
          {
            "id": "lesion",
            "label": "Lesión"
          },
          {
            "id": "var",
            "label": "VAR"
          }
        ],
        "answers": {
          "gol": "left",
          "penal": "left",
          "fuera-de-lugar": "left",
          "lesion": "right",
          "var": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 75,
    "roundLabel": "La lucha libre",
    "questions": {
      "number": {
        "id": "casa-la-lucha-libre-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas llaves conoces?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-lucha-libre-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del ring es teatro?",
        "min": 0,
        "max": 100,
        "answer": 90,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-lucha-libre-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por drama: máscara, reto y llave.",
        "directionLabel": "de más a menos drama",
        "items": [
          {
            "id": "mascara",
            "label": "Máscara"
          },
          {
            "id": "reto",
            "label": "Reto"
          },
          {
            "id": "llave",
            "label": "Llave"
          }
        ],
        "answerOrder": [
          "mascara",
          "reto",
          "llave"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-lucha-libre-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es de lucha libre o no.",
        "leftLabel": "Lucha libre",
        "rightLabel": "No",
        "terms": [
          {
            "id": "mascara",
            "label": "Máscara"
          },
          {
            "id": "ring",
            "label": "Ring"
          },
          {
            "id": "llave",
            "label": "Llave"
          },
          {
            "id": "guantes",
            "label": "Guantes"
          },
          {
            "id": "balon",
            "label": "Balón"
          }
        ],
        "answers": {
          "mascara": "left",
          "ring": "left",
          "llave": "left",
          "guantes": "right",
          "balon": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 76,
    "roundLabel": "El boxeo",
    "questions": {
      "number": {
        "id": "casa-el-boxeo-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 15",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos asaltos aguantas viendo?",
        "min": 0,
        "max": 15,
        "answer": 12,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-boxeo-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del boxeo es estrategia?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-boxeo-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por dolor: uppercut, gancho y jab.",
        "directionLabel": "de más a menos dolor",
        "items": [
          {
            "id": "uppercut",
            "label": "Uppercut"
          },
          {
            "id": "gancho",
            "label": "Gancho"
          },
          {
            "id": "jab",
            "label": "Jab"
          }
        ],
        "answerOrder": [
          "uppercut",
          "gancho",
          "jab"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-boxeo-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en boxeo o no.",
        "leftLabel": "Boxeo",
        "rightLabel": "No",
        "terms": [
          {
            "id": "guantes",
            "label": "Guantes"
          },
          {
            "id": "ring",
            "label": "Ring"
          },
          {
            "id": "campana",
            "label": "Campana"
          },
          {
            "id": "balon",
            "label": "Balón"
          },
          {
            "id": "raqueta",
            "label": "Raqueta"
          }
        ],
        "answers": {
          "guantes": "left",
          "ring": "left",
          "campana": "left",
          "balon": "right",
          "raqueta": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 77,
    "roundLabel": "El béisbol",
    "questions": {
      "number": {
        "id": "casa-el-beisbol-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 12",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos innings aguantas?",
        "min": 0,
        "max": 12,
        "answer": 9,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-beisbol-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del juego es espera?",
        "min": 0,
        "max": 100,
        "answer": 70,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-beisbol-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por emoción: jonrón, strike y base.",
        "directionLabel": "de más a menos emoción",
        "items": [
          {
            "id": "jonron",
            "label": "Jonrón"
          },
          {
            "id": "strike",
            "label": "Strike"
          },
          {
            "id": "base",
            "label": "Base"
          }
        ],
        "answerOrder": [
          "jonron",
          "strike",
          "base"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-beisbol-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en béisbol o no.",
        "leftLabel": "Béisbol",
        "rightLabel": "No",
        "terms": [
          {
            "id": "bate",
            "label": "Bate"
          },
          {
            "id": "pelota",
            "label": "Pelota"
          },
          {
            "id": "guante",
            "label": "Guante"
          },
          {
            "id": "red",
            "label": "Red"
          },
          {
            "id": "porteria",
            "label": "Portería"
          }
        ],
        "answers": {
          "bate": "left",
          "pelota": "left",
          "guante": "left",
          "red": "right",
          "porteria": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 78,
    "roundLabel": "La F1",
    "questions": {
      "number": {
        "id": "casa-la-f1-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 100",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas vueltas aguantas?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-f1-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la carrera es estrategia?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-f1-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por velocidad: F1, moto y bici.",
        "directionLabel": "de más a menos velocidad",
        "items": [
          {
            "id": "f1",
            "label": "F1"
          },
          {
            "id": "moto",
            "label": "Moto"
          },
          {
            "id": "bici",
            "label": "Bici"
          }
        ],
        "answerOrder": [
          "f1",
          "moto",
          "bici"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-f1-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en F1 o no.",
        "leftLabel": "F1",
        "rightLabel": "No",
        "terms": [
          {
            "id": "casco",
            "label": "Casco"
          },
          {
            "id": "llanta",
            "label": "Llanta"
          },
          {
            "id": "pits",
            "label": "Pits"
          },
          {
            "id": "balon",
            "label": "Balón"
          },
          {
            "id": "raqueta",
            "label": "Raqueta"
          }
        ],
        "answers": {
          "casco": "left",
          "llanta": "left",
          "pits": "left",
          "balon": "right",
          "raqueta": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 79,
    "roundLabel": "La dieta",
    "questions": {
      "number": {
        "id": "casa-la-dieta-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos días aguantas la dieta?",
        "min": 0,
        "max": 30,
        "answer": 5,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-dieta-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la dieta es trampa?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-dieta-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por tentación: pastel, fruta y ensalada.",
        "directionLabel": "de más a menos tentación",
        "items": [
          {
            "id": "pastel",
            "label": "Pastel"
          },
          {
            "id": "fruta",
            "label": "Fruta"
          },
          {
            "id": "ensalada",
            "label": "Ensalada"
          }
        ],
        "answerOrder": [
          "pastel",
          "fruta",
          "ensalada"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-dieta-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se come en dieta o no.",
        "leftLabel": "Dieta",
        "rightLabel": "No",
        "terms": [
          {
            "id": "ensalada",
            "label": "Ensalada"
          },
          {
            "id": "pollo",
            "label": "Pollo"
          },
          {
            "id": "agua",
            "label": "Agua"
          },
          {
            "id": "pastel",
            "label": "Pastel"
          },
          {
            "id": "tacos",
            "label": "Tacos"
          }
        ],
        "answers": {
          "ensalada": "left",
          "pollo": "left",
          "agua": "left",
          "pastel": "right",
          "tacos": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 80,
    "roundLabel": "El doctor",
    "questions": {
      "number": {
        "id": "casa-el-doctor-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 12",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas esperas al doctor?",
        "min": 0,
        "max": 12,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-doctor-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la consulta es receta?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-doctor-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por miedo: inyección, bata y receta.",
        "directionLabel": "de más a menos miedo",
        "items": [
          {
            "id": "inyeccion",
            "label": "Inyección"
          },
          {
            "id": "bata",
            "label": "Bata"
          },
          {
            "id": "receta",
            "label": "Receta"
          }
        ],
        "answerOrder": [
          "inyeccion",
          "bata",
          "receta"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-doctor-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va al doctor o no.",
        "leftLabel": "Doctor",
        "rightLabel": "No",
        "terms": [
          {
            "id": "receta",
            "label": "Receta"
          },
          {
            "id": "estetoscopio",
            "label": "Estetoscopio"
          },
          {
            "id": "bata",
            "label": "Bata"
          },
          {
            "id": "martillo",
            "label": "Martillo"
          },
          {
            "id": "sarten",
            "label": "Sartén"
          }
        ],
        "answers": {
          "receta": "left",
          "estetoscopio": "left",
          "bata": "left",
          "martillo": "right",
          "sarten": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 81,
    "roundLabel": "La farmacia",
    "questions": {
      "number": {
        "id": "casa-la-farmacia-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos productos compras en la farmacia?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-farmacia-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la farmacia es dulce?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-farmacia-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por precio: medicina, dulce y curita.",
        "directionLabel": "de más a menos precio",
        "items": [
          {
            "id": "medicina",
            "label": "Medicina"
          },
          {
            "id": "dulce",
            "label": "Dulce"
          },
          {
            "id": "curita",
            "label": "Curita"
          }
        ],
        "answerOrder": [
          "medicina",
          "dulce",
          "curita"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-farmacia-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se vende en farmacia o no.",
        "leftLabel": "Farmacia",
        "rightLabel": "No",
        "terms": [
          {
            "id": "curita",
            "label": "Curita"
          },
          {
            "id": "medicina",
            "label": "Medicina"
          },
          {
            "id": "alcohol",
            "label": "Alcohol"
          },
          {
            "id": "martillo",
            "label": "Martillo"
          },
          {
            "id": "aguacate",
            "label": "Aguacate"
          }
        ],
        "answers": {
          "curita": "left",
          "medicina": "left",
          "alcohol": "left",
          "martillo": "right",
          "aguacate": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 82,
    "roundLabel": "El dentista",
    "questions": {
      "number": {
        "id": "casa-el-dentista-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas caries tienes?",
        "min": 0,
        "max": 20,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-dentista-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la cita es miedo?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-dentista-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por ruido: taladro, succión y enjuague.",
        "directionLabel": "de más a menos ruido",
        "items": [
          {
            "id": "taladro",
            "label": "Taladro"
          },
          {
            "id": "succion",
            "label": "Succión"
          },
          {
            "id": "enjuague",
            "label": "Enjuague"
          }
        ],
        "answerOrder": [
          "taladro",
          "succion",
          "enjuague"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-dentista-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en el dentista o no.",
        "leftLabel": "Dentista",
        "rightLabel": "No",
        "terms": [
          {
            "id": "cepillo",
            "label": "Cepillo"
          },
          {
            "id": "hilo",
            "label": "Hilo"
          },
          {
            "id": "espejo",
            "label": "Espejo"
          },
          {
            "id": "martillo",
            "label": "Martillo"
          },
          {
            "id": "sarten",
            "label": "Sartén"
          }
        ],
        "answers": {
          "cepillo": "left",
          "hilo": "left",
          "espejo": "left",
          "martillo": "right",
          "sarten": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 83,
    "roundLabel": "La escuela de hijos",
    "questions": {
      "number": {
        "id": "casa-la-escuela-de-hijos-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas juntas escolares al año?",
        "min": 0,
        "max": 20,
        "answer": 6,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-escuela-de-hijos-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la junta es culpa del niño?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-escuela-de-hijos-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por estrés: tarea, uniforme y lunch.",
        "directionLabel": "de más a menos estrés",
        "items": [
          {
            "id": "tarea",
            "label": "Tarea"
          },
          {
            "id": "uniforme",
            "label": "Uniforme"
          },
          {
            "id": "lunch",
            "label": "Lunch"
          }
        ],
        "answerOrder": [
          "tarea",
          "uniforme",
          "lunch"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-escuela-de-hijos-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en la mochila o no.",
        "leftLabel": "Mochila",
        "rightLabel": "No",
        "terms": [
          {
            "id": "cuaderno",
            "label": "Cuaderno"
          },
          {
            "id": "lunch",
            "label": "Lunch"
          },
          {
            "id": "lapiz",
            "label": "Lápiz"
          },
          {
            "id": "control",
            "label": "Control"
          },
          {
            "id": "sarten",
            "label": "Sartén"
          }
        ],
        "answers": {
          "cuaderno": "left",
          "lunch": "left",
          "lapiz": "left",
          "control": "right",
          "sarten": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 84,
    "roundLabel": "Los hijos",
    "questions": {
      "number": {
        "id": "casa-los-hijos-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 100",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas veces preguntan por qué?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-los-hijos-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del día es caos?",
        "min": 0,
        "max": 100,
        "answer": 70,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-los-hijos-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por energía: bebé, niño y adolescente.",
        "directionLabel": "de menos a más energía",
        "items": [
          {
            "id": "bebe",
            "label": "Bebé"
          },
          {
            "id": "nino",
            "label": "Niño"
          },
          {
            "id": "adolescente",
            "label": "Adolescente"
          }
        ],
        "answerOrder": [
          "bebe",
          "nino",
          "adolescente"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-los-hijos-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es cosa de hijos o no.",
        "leftLabel": "Hijos",
        "rightLabel": "No",
        "terms": [
          {
            "id": "panal",
            "label": "Pañal"
          },
          {
            "id": "tarea",
            "label": "Tarea"
          },
          {
            "id": "berrinche",
            "label": "Berrinche"
          },
          {
            "id": "renta",
            "label": "Renta"
          },
          {
            "id": "cafe",
            "label": "Café"
          }
        ],
        "answers": {
          "panal": "left",
          "tarea": "left",
          "berrinche": "left",
          "renta": "right",
          "cafe": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 85,
    "roundLabel": "Las mascotas",
    "questions": {
      "number": {
        "id": "casa-las-mascotas-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 30",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos juguetes tiene tu mascota?",
        "min": 0,
        "max": 30,
        "answer": 8,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-las-mascotas-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del sofá es pelo?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-las-mascotas-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por ruido: perro, gato y hámster.",
        "directionLabel": "de más a menos ruido",
        "items": [
          {
            "id": "perro",
            "label": "Perro"
          },
          {
            "id": "gato",
            "label": "Gato"
          },
          {
            "id": "hamster",
            "label": "Hámster"
          }
        ],
        "answerOrder": [
          "perro",
          "gato",
          "hamster"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-las-mascotas-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es mascota o no.",
        "leftLabel": "Mascota",
        "rightLabel": "No",
        "terms": [
          {
            "id": "perro",
            "label": "Perro"
          },
          {
            "id": "gato",
            "label": "Gato"
          },
          {
            "id": "pez",
            "label": "Pez"
          },
          {
            "id": "jefe",
            "label": "Jefe"
          },
          {
            "id": "vecino",
            "label": "Vecino"
          }
        ],
        "answers": {
          "perro": "left",
          "gato": "left",
          "pez": "left",
          "jefe": "right",
          "vecino": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 86,
    "roundLabel": "El perro",
    "questions": {
      "number": {
        "id": "casa-el-perro-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas veces te saca a pasear?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-perro-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del paseo es olfatear?",
        "min": 0,
        "max": 100,
        "answer": 70,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-perro-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por energía: cachorro, adulto y dormilón.",
        "directionLabel": "de más a menos energía",
        "items": [
          {
            "id": "cachorro",
            "label": "Cachorro"
          },
          {
            "id": "adulto",
            "label": "Adulto"
          },
          {
            "id": "dormilon",
            "label": "Dormilón"
          }
        ],
        "answerOrder": [
          "cachorro",
          "adulto",
          "dormilon"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-perro-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: le gusta al perro o no.",
        "leftLabel": "Le gusta",
        "rightLabel": "No",
        "terms": [
          {
            "id": "hueso",
            "label": "Hueso"
          },
          {
            "id": "pelota",
            "label": "Pelota"
          },
          {
            "id": "paseo",
            "label": "Paseo"
          },
          {
            "id": "bano",
            "label": "Baño"
          },
          {
            "id": "aspiradora",
            "label": "Aspiradora"
          }
        ],
        "answers": {
          "hueso": "left",
          "pelota": "left",
          "paseo": "left",
          "bano": "right",
          "aspiradora": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 87,
    "roundLabel": "El gato",
    "questions": {
      "number": {
        "id": "casa-el-gato-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 24",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas duerme el gato?",
        "min": 0,
        "max": 24,
        "answer": 16,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-gato-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del día te ignora?",
        "min": 0,
        "max": 100,
        "answer": 80,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-gato-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por desprecio: gato, perro y pez.",
        "directionLabel": "de más a menos desprecio",
        "items": [
          {
            "id": "gato",
            "label": "Gato"
          },
          {
            "id": "perro",
            "label": "Perro"
          },
          {
            "id": "pez",
            "label": "Pez"
          }
        ],
        "answerOrder": [
          "gato",
          "perro",
          "pez"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-gato-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: le gusta al gato o no.",
        "leftLabel": "Le gusta",
        "rightLabel": "No",
        "terms": [
          {
            "id": "caja",
            "label": "Caja"
          },
          {
            "id": "atun",
            "label": "Atún"
          },
          {
            "id": "siesta",
            "label": "Siesta"
          },
          {
            "id": "agua",
            "label": "Agua"
          },
          {
            "id": "perro",
            "label": "Perro"
          }
        ],
        "answers": {
          "caja": "left",
          "atun": "left",
          "siesta": "left",
          "agua": "right",
          "perro": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 88,
    "roundLabel": "El veterinario",
    "questions": {
      "number": {
        "id": "casa-el-veterinario-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 3000",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuánto cuesta la consulta?",
        "min": 0,
        "max": 3000,
        "answer": 500,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-veterinario-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la consulta es drama?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-veterinario-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por estrés: dueño, perro y gato.",
        "directionLabel": "de más a menos estrés",
        "items": [
          {
            "id": "dueno",
            "label": "Dueño"
          },
          {
            "id": "perro",
            "label": "Perro"
          },
          {
            "id": "gato",
            "label": "Gato"
          }
        ],
        "answerOrder": [
          "dueno",
          "perro",
          "gato"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-veterinario-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va en veterinario o no.",
        "leftLabel": "Veterinario",
        "rightLabel": "No",
        "terms": [
          {
            "id": "vacuna",
            "label": "Vacuna"
          },
          {
            "id": "correa",
            "label": "Correa"
          },
          {
            "id": "jaula",
            "label": "Jaula"
          },
          {
            "id": "estetoscopio",
            "label": "Estetoscopio"
          },
          {
            "id": "sarten",
            "label": "Sartén"
          }
        ],
        "answers": {
          "vacuna": "left",
          "correa": "left",
          "jaula": "left",
          "estetoscopio": "left",
          "sarten": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 89,
    "roundLabel": "La naturaleza",
    "questions": {
      "number": {
        "id": "casa-la-naturaleza-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 48",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas aguantas sin señal?",
        "min": 0,
        "max": 48,
        "answer": 6,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-naturaleza-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de naturaleza es insecto?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-naturaleza-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por paz: bosque, playa y ciudad.",
        "directionLabel": "de más a menos paz",
        "items": [
          {
            "id": "bosque",
            "label": "Bosque"
          },
          {
            "id": "playa",
            "label": "Playa"
          },
          {
            "id": "ciudad",
            "label": "Ciudad"
          }
        ],
        "answerOrder": [
          "bosque",
          "playa",
          "ciudad"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-naturaleza-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es naturaleza o no.",
        "leftLabel": "Naturaleza",
        "rightLabel": "No",
        "terms": [
          {
            "id": "arbol",
            "label": "Árbol"
          },
          {
            "id": "rio",
            "label": "Río"
          },
          {
            "id": "montana",
            "label": "Montaña"
          },
          {
            "id": "edificio",
            "label": "Edificio"
          },
          {
            "id": "asfalto",
            "label": "Asfalto"
          }
        ],
        "answers": {
          "arbol": "left",
          "rio": "left",
          "montana": "left",
          "edificio": "right",
          "asfalto": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 90,
    "roundLabel": "El volcán",
    "questions": {
      "number": {
        "id": "casa-el-volcan-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 50",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos volcanes activos hay en México?",
        "min": 0,
        "max": 50,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-volcan-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del volcán es humo?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-volcan-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por peligro: volcán, temblor y lluvia.",
        "directionLabel": "de más a menos peligro",
        "items": [
          {
            "id": "volcan",
            "label": "Volcán"
          },
          {
            "id": "temblor",
            "label": "Temblor"
          },
          {
            "id": "lluvia",
            "label": "Lluvia"
          }
        ],
        "answerOrder": [
          "volcan",
          "temblor",
          "lluvia"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-volcan-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: va con volcán o no.",
        "leftLabel": "Volcán",
        "rightLabel": "No",
        "terms": [
          {
            "id": "lava",
            "label": "Lava"
          },
          {
            "id": "ceniza",
            "label": "Ceniza"
          },
          {
            "id": "crater",
            "label": "Cráter"
          },
          {
            "id": "nieve",
            "label": "Nieve"
          },
          {
            "id": "playera",
            "label": "Playera"
          }
        ],
        "answers": {
          "lava": "left",
          "ceniza": "left",
          "crater": "left",
          "nieve": "right",
          "playera": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 91,
    "roundLabel": "La lluvia",
    "questions": {
      "number": {
        "id": "casa-la-lluvia-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 60",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos aguantas sin paraguas?",
        "min": 0,
        "max": 60,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-lluvia-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la lluvia es drama?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-lluvia-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por molestia: tráfico, lluvia y charco.",
        "directionLabel": "de más a menos molestia",
        "items": [
          {
            "id": "trafico",
            "label": "Tráfico"
          },
          {
            "id": "lluvia",
            "label": "Lluvia"
          },
          {
            "id": "charco",
            "label": "Charco"
          }
        ],
        "answerOrder": [
          "trafico",
          "lluvia",
          "charco"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-lluvia-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se usa en lluvia o no.",
        "leftLabel": "Lluvia",
        "rightLabel": "No",
        "terms": [
          {
            "id": "paraguas",
            "label": "Paraguas"
          },
          {
            "id": "impermeable",
            "label": "Impermeable"
          },
          {
            "id": "botas",
            "label": "Botas"
          },
          {
            "id": "lentes",
            "label": "Lentes"
          },
          {
            "id": "bloqueador",
            "label": "Bloqueador"
          }
        ],
        "answers": {
          "paraguas": "left",
          "impermeable": "left",
          "botas": "left",
          "lentes": "right",
          "bloqueador": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 92,
    "roundLabel": "El calor",
    "questions": {
      "number": {
        "id": "casa-el-calor-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 50",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos grados aguantas?",
        "min": 0,
        "max": 50,
        "answer": 32,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-calor-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del calor es humedad?",
        "min": 0,
        "max": 100,
        "answer": 50,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-calor-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por desesperación: calor, sed y abanico.",
        "directionLabel": "de más a menos desesperación",
        "items": [
          {
            "id": "calor",
            "label": "Calor"
          },
          {
            "id": "sed",
            "label": "Sed"
          },
          {
            "id": "abanico",
            "label": "Abanico"
          }
        ],
        "answerOrder": [
          "calor",
          "sed",
          "abanico"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-calor-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se usa con calor o no.",
        "leftLabel": "Con calor",
        "rightLabel": "No",
        "terms": [
          {
            "id": "agua",
            "label": "Agua"
          },
          {
            "id": "ventilador",
            "label": "Ventilador"
          },
          {
            "id": "hielo",
            "label": "Hielo"
          },
          {
            "id": "sueter",
            "label": "Suéter"
          },
          {
            "id": "cobija",
            "label": "Cobija"
          }
        ],
        "answers": {
          "agua": "left",
          "ventilador": "left",
          "hielo": "left",
          "sueter": "right",
          "cobija": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 93,
    "roundLabel": "El aire",
    "questions": {
      "number": {
        "id": "casa-el-aire-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 10",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos ventiladores tienes?",
        "min": 0,
        "max": 10,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-aire-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del aire es polvo?",
        "min": 0,
        "max": 100,
        "answer": 30,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-aire-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por frescura: aire, abanico y ventilador.",
        "directionLabel": "de menos a más fresco",
        "items": [
          {
            "id": "aire",
            "label": "Aire"
          },
          {
            "id": "abanico",
            "label": "Abanico"
          },
          {
            "id": "ventilador",
            "label": "Ventilador"
          }
        ],
        "answerOrder": [
          "aire",
          "abanico",
          "ventilador"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-aire-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: da aire o no.",
        "leftLabel": "Da aire",
        "rightLabel": "No",
        "terms": [
          {
            "id": "ventana",
            "label": "Ventana"
          },
          {
            "id": "ventilador",
            "label": "Ventilador"
          },
          {
            "id": "abanico",
            "label": "Abanico"
          },
          {
            "id": "pared",
            "label": "Pared"
          },
          {
            "id": "cobija",
            "label": "Cobija"
          }
        ],
        "answers": {
          "ventana": "left",
          "ventilador": "left",
          "abanico": "left",
          "pared": "right",
          "cobija": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 94,
    "roundLabel": "La luz",
    "questions": {
      "number": {
        "id": "casa-la-luz-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos focos fundidos tienes?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-luz-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del recibo es luz?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-luz-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por brillo: sol, foco y vela.",
        "directionLabel": "de más a menos brillo",
        "items": [
          {
            "id": "sol",
            "label": "Sol"
          },
          {
            "id": "foco",
            "label": "Foco"
          },
          {
            "id": "vela",
            "label": "Vela"
          }
        ],
        "answerOrder": [
          "sol",
          "foco",
          "vela"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-luz-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: da luz o no.",
        "leftLabel": "Da luz",
        "rightLabel": "No",
        "terms": [
          {
            "id": "foco",
            "label": "Foco"
          },
          {
            "id": "vela",
            "label": "Vela"
          },
          {
            "id": "sol",
            "label": "Sol"
          },
          {
            "id": "hielo",
            "label": "Hielo"
          },
          {
            "id": "piedra",
            "label": "Piedra"
          }
        ],
        "answers": {
          "foco": "left",
          "vela": "left",
          "sol": "left",
          "hielo": "right",
          "piedra": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 95,
    "roundLabel": "La renta",
    "questions": {
      "number": {
        "id": "casa-la-renta-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 6",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos meses de depósito piden?",
        "min": 0,
        "max": 6,
        "answer": 2,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-renta-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del sueldo se va en renta?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-renta-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por dolor: renta, luz e internet.",
        "directionLabel": "de más a menos dolor",
        "items": [
          {
            "id": "renta",
            "label": "Renta"
          },
          {
            "id": "luz",
            "label": "Luz"
          },
          {
            "id": "internet",
            "label": "Internet"
          }
        ],
        "answerOrder": [
          "renta",
          "luz",
          "internet"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-renta-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es gasto de casa o no.",
        "leftLabel": "Casa",
        "rightLabel": "No",
        "terms": [
          {
            "id": "renta",
            "label": "Renta"
          },
          {
            "id": "luz",
            "label": "Luz"
          },
          {
            "id": "agua",
            "label": "Agua"
          },
          {
            "id": "cine",
            "label": "Cine"
          },
          {
            "id": "tacos",
            "label": "Tacos"
          }
        ],
        "answers": {
          "renta": "left",
          "luz": "left",
          "agua": "left",
          "cine": "right",
          "tacos": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 96,
    "roundLabel": "La quincena",
    "questions": {
      "number": {
        "id": "casa-la-quincena-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 15",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos días dura la quincena?",
        "min": 0,
        "max": 15,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-quincena-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la quincena es gasto?",
        "min": 0,
        "max": 100,
        "answer": 80,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-quincena-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por rapidez: domingo, quincena y aguinaldo.",
        "directionLabel": "de más a menos rápido",
        "items": [
          {
            "id": "domingo",
            "label": "Domingo"
          },
          {
            "id": "quincena",
            "label": "Quincena"
          },
          {
            "id": "aguinaldo",
            "label": "Aguinaldo"
          }
        ],
        "answerOrder": [
          "domingo",
          "quincena",
          "aguinaldo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-quincena-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se paga en quincena o no.",
        "leftLabel": "Quincena",
        "rightLabel": "No",
        "terms": [
          {
            "id": "renta",
            "label": "Renta"
          },
          {
            "id": "despensa",
            "label": "Despensa"
          },
          {
            "id": "transporte",
            "label": "Transporte"
          },
          {
            "id": "regalo",
            "label": "Regalo"
          },
          {
            "id": "vacaciones",
            "label": "Vacaciones"
          }
        ],
        "answers": {
          "renta": "left",
          "despensa": "left",
          "transporte": "left",
          "regalo": "right",
          "vacaciones": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 97,
    "roundLabel": "La tarjeta",
    "questions": {
      "number": {
        "id": "casa-la-tarjeta-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 20",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas tarjetas tienes?",
        "min": 0,
        "max": 20,
        "answer": 3,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-tarjeta-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje de la tarjeta es deuda?",
        "min": 0,
        "max": 100,
        "answer": 60,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-tarjeta-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por peligro: crédito, débito y efectivo.",
        "directionLabel": "de más a menos peligro",
        "items": [
          {
            "id": "credito",
            "label": "Crédito"
          },
          {
            "id": "debito",
            "label": "Débito"
          },
          {
            "id": "efectivo",
            "label": "Efectivo"
          }
        ],
        "answerOrder": [
          "credito",
          "debito",
          "efectivo"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-tarjeta-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se paga con tarjeta o no.",
        "leftLabel": "Tarjeta",
        "rightLabel": "No",
        "terms": [
          {
            "id": "super",
            "label": "Súper"
          },
          {
            "id": "gasolina",
            "label": "Gasolina"
          },
          {
            "id": "streaming",
            "label": "Streaming"
          },
          {
            "id": "propina",
            "label": "Propina"
          },
          {
            "id": "cine",
            "label": "Cine"
          }
        ],
        "answers": {
          "super": "left",
          "gasolina": "left",
          "streaming": "left",
          "propina": "right",
          "cine": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 98,
    "roundLabel": "El banco",
    "questions": {
      "number": {
        "id": "casa-el-banco-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 120",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántos minutos en fila?",
        "min": 0,
        "max": 120,
        "answer": 20,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-banco-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del banco es papeleo?",
        "min": 0,
        "max": 100,
        "answer": 70,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-banco-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por paciencia: fila, cajero y app.",
        "directionLabel": "de más a menos paciencia",
        "items": [
          {
            "id": "fila",
            "label": "Fila"
          },
          {
            "id": "cajero",
            "label": "Cajero"
          },
          {
            "id": "app",
            "label": "App"
          }
        ],
        "answerOrder": [
          "fila",
          "cajero",
          "app"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-banco-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se hace en banco o no.",
        "leftLabel": "Banco",
        "rightLabel": "No",
        "terms": [
          {
            "id": "deposito",
            "label": "Depósito"
          },
          {
            "id": "retiro",
            "label": "Retiro"
          },
          {
            "id": "cheques",
            "label": "Cheques"
          },
          {
            "id": "cafe",
            "label": "Café"
          },
          {
            "id": "cine",
            "label": "Cine"
          }
        ],
        "answers": {
          "deposito": "left",
          "retiro": "left",
          "cheques": "left",
          "cafe": "right",
          "cine": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 99,
    "roundLabel": "La jubilación",
    "questions": {
      "number": {
        "id": "casa-la-jubilacion-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 100",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿a qué edad te jubilas?",
        "min": 0,
        "max": 100,
        "answer": 65,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-la-jubilacion-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del sueldo ahorras?",
        "min": 0,
        "max": 100,
        "answer": 10,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-la-jubilacion-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por lejanía: jubilación, quincena y viernes.",
        "directionLabel": "de más a menos lejos",
        "items": [
          {
            "id": "jubilacion",
            "label": "Jubilación"
          },
          {
            "id": "quincena",
            "label": "Quincena"
          },
          {
            "id": "viernes",
            "label": "Viernes"
          }
        ],
        "answerOrder": [
          "jubilacion",
          "quincena",
          "viernes"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-la-jubilacion-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: es plan de jubilación o no.",
        "leftLabel": "Jubilación",
        "rightLabel": "No",
        "terms": [
          {
            "id": "ahorro",
            "label": "Ahorro"
          },
          {
            "id": "pension",
            "label": "Pensión"
          },
          {
            "id": "inversion",
            "label": "Inversión"
          },
          {
            "id": "fiesta",
            "label": "Fiesta"
          },
          {
            "id": "apuesta",
            "label": "Apuesta"
          }
        ],
        "answers": {
          "ahorro": "left",
          "pension": "left",
          "inversion": "left",
          "fiesta": "right",
          "apuesta": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  },
  {
    "roundIndex": 100,
    "roundLabel": "El fin de semana",
    "questions": {
      "number": {
        "id": "casa-el-fin-de-semana-num",
        "categoryId": "number",
        "kind": "number",
        "title": "De 0 a 24",
        "shortLabel": "Número",
        "prompt": "La casa dice: ¿cuántas horas duermes el sábado?",
        "min": 0,
        "max": 24,
        "answer": 9,
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "percent": {
        "id": "casa-el-fin-de-semana-pct",
        "categoryId": "percent",
        "kind": "percent",
        "title": "Porcentaje",
        "shortLabel": "Porcentaje",
        "prompt": "¿Qué porcentaje del domingo es ansiedad?",
        "min": 0,
        "max": 100,
        "answer": 40,
        "unitLabel": "%",
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "rank": {
        "id": "casa-el-fin-de-semana-rank",
        "categoryId": "rank",
        "kind": "rank",
        "title": "En orden",
        "shortLabel": "Orden",
        "prompt": "Ordena por rapidez: sábado, domingo y lunes.",
        "directionLabel": "de más a menos rápido",
        "items": [
          {
            "id": "sabado",
            "label": "Sábado"
          },
          {
            "id": "domingo",
            "label": "Domingo"
          },
          {
            "id": "lunes",
            "label": "Lunes"
          }
        ],
        "answerOrder": [
          "sabado",
          "domingo",
          "lunes"
        ],
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      },
      "assign": {
        "id": "casa-el-fin-de-semana-assign",
        "categoryId": "assign",
        "kind": "assign",
        "title": "Intersección",
        "shortLabel": "Clasifica",
        "prompt": "Clasifica: se hace en fin de semana o no.",
        "leftLabel": "Fin de semana",
        "rightLabel": "No",
        "terms": [
          {
            "id": "dormir",
            "label": "Dormir"
          },
          {
            "id": "fiesta",
            "label": "Fiesta"
          },
          {
            "id": "mandado",
            "label": "Mandado"
          },
          {
            "id": "junta",
            "label": "Junta"
          },
          {
            "id": "escuela",
            "label": "Escuela"
          }
        ],
        "answers": {
          "dormir": "left",
          "fiesta": "left",
          "mandado": "left",
          "junta": "right",
          "escuela": "right"
        },
        "source": {
          "label": "La casa dice",
          "url": "https://party.visionplusv2.duckdns.org/"
        }
      }
    }
  }
] as SchaetzoramaRoundContent[]));
