import type { SupportedLanguage } from "@open-party-lab/game-core";
import type { ZeichnenUndErratenWordCategory } from "../protocol.js";

// Authored bilingual pairs retain the existing category membership and vocabulary.
export const drawingWords = [
  {
    "de": "cactus",
    "en": "cactus",
    "category": "standard"
  },
  {
    "de": "bicicleta",
    "en": "bicicleta",
    "category": "standard"
  },
  {
    "de": "cohete",
    "en": "cohete",
    "category": "standard"
  },
  {
    "de": "castillo",
    "en": "castillo",
    "category": "standard"
  },
  {
    "de": "nube",
    "en": "nube",
    "category": "standard"
  },
  {
    "de": "piña",
    "en": "piña",
    "category": "standard"
  },
  {
    "de": "dinosaurios",
    "en": "dinosaurios",
    "category": "standard"
  },
  {
    "de": "Paraguas",
    "en": "Paraguas",
    "category": "standard"
  },
  {
    "de": "pingüino",
    "en": "pingüino",
    "category": "standard"
  },
  {
    "de": "faro",
    "en": "faro",
    "category": "standard"
  },
  {
    "de": "trompeta",
    "en": "trompeta",
    "category": "standard"
  },
  {
    "de": "volcán",
    "en": "volcán",
    "category": "standard"
  },
  {
    "de": "manzana",
    "en": "manzana",
    "category": "standard"
  },
  {
    "de": "Plátano",
    "en": "Plátano",
    "category": "standard"
  },
  {
    "de": "cereza",
    "en": "cereza",
    "category": "standard"
  },
  {
    "de": "fresa",
    "en": "fresa",
    "category": "standard"
  },
  {
    "de": "pera",
    "en": "pera",
    "category": "standard"
  },
  {
    "de": "limon",
    "en": "limon",
    "category": "standard"
  },
  {
    "de": "naranja",
    "en": "naranja",
    "category": "standard"
  },
  {
    "de": "uva",
    "en": "uva",
    "category": "standard"
  },
  {
    "de": "Sandía",
    "en": "Sandía",
    "category": "standard"
  },
  {
    "de": "kiwi",
    "en": "kiwi",
    "category": "standard"
  },
  {
    "de": "durazno",
    "en": "durazno",
    "category": "standard"
  },
  {
    "de": "mango",
    "en": "mango",
    "category": "standard"
  },
  {
    "de": "coco",
    "en": "coco",
    "category": "standard"
  },
  {
    "de": "aguacate",
    "en": "aguacate",
    "category": "standard"
  },
  {
    "de": "zanahoria",
    "en": "zanahoria",
    "category": "standard"
  },
  {
    "de": "tomate",
    "en": "tomate",
    "category": "standard"
  },
  {
    "de": "pepino",
    "en": "pepino",
    "category": "standard"
  },
  {
    "de": "pimentón",
    "en": "pimentón",
    "category": "standard"
  },
  {
    "de": "Hongo",
    "en": "Hongo",
    "category": "standard"
  },
  {
    "de": "brócoli",
    "en": "brócoli",
    "category": "standard"
  },
  {
    "de": "patata",
    "en": "patata",
    "category": "standard"
  },
  {
    "de": "cebolla",
    "en": "cebolla",
    "category": "standard"
  },
  {
    "de": "ajo",
    "en": "ajo",
    "category": "standard"
  },
  {
    "de": "maíz",
    "en": "maíz",
    "category": "standard"
  },
  {
    "de": "pan",
    "en": "pan",
    "category": "standard"
  },
  {
    "de": "Queso",
    "en": "Queso",
    "category": "standard"
  },
  {
    "de": "pizzería",
    "en": "pizzería",
    "category": "standard"
  },
  {
    "de": "hamburguesas",
    "en": "hamburguesas",
    "category": "standard"
  },
  {
    "de": "papas fritas",
    "en": "papas fritas",
    "category": "standard"
  },
  {
    "de": "espaguetis",
    "en": "espaguetis",
    "category": "standard"
  },
  {
    "de": "sopa",
    "en": "sopa",
    "category": "standard"
  },
  {
    "de": "pastel",
    "en": "pastel",
    "category": "standard"
  },
  {
    "de": "helado",
    "en": "helado",
    "category": "standard"
  },
  {
    "de": "galleta",
    "en": "galleta",
    "category": "standard"
  },
  {
    "de": "taza",
    "en": "taza",
    "category": "standard"
  },
  {
    "de": "cuchara",
    "en": "cuchara",
    "category": "standard"
  },
  {
    "de": "tenedor",
    "en": "tenedor",
    "category": "standard"
  },
  {
    "de": "cuchillo",
    "en": "cuchillo",
    "category": "standard"
  },
  {
    "de": "plato",
    "en": "plato",
    "category": "standard"
  },
  {
    "de": "olla",
    "en": "olla",
    "category": "standard"
  },
  {
    "de": "sartén",
    "en": "sartén",
    "category": "standard"
  },
  {
    "de": "refrigerador",
    "en": "refrigerador",
    "category": "standard"
  },
  {
    "de": "Tostadora",
    "en": "Tostadora",
    "category": "standard"
  },
  {
    "de": "licuadora",
    "en": "licuadora",
    "category": "standard"
  },
  {
    "de": "horno",
    "en": "horno",
    "category": "standard"
  },
  {
    "de": "lámpara",
    "en": "lámpara",
    "category": "standard"
  },
  {
    "de": "Sofá",
    "en": "Sofá",
    "category": "standard"
  },
  {
    "de": "silla",
    "en": "silla",
    "category": "standard"
  },
  {
    "de": "mesa",
    "en": "mesa",
    "category": "standard"
  },
  {
    "de": "cama",
    "en": "cama",
    "category": "standard"
  },
  {
    "de": "almohada",
    "en": "almohada",
    "category": "standard"
  },
  {
    "de": "manta",
    "en": "manta",
    "category": "standard"
  },
  {
    "de": "armario",
    "en": "armario",
    "category": "standard"
  },
  {
    "de": "espejo",
    "en": "espejo",
    "category": "standard"
  },
  {
    "de": "ventana",
    "en": "ventana",
    "category": "standard"
  },
  {
    "de": "puerta",
    "en": "puerta",
    "category": "standard"
  },
  {
    "de": "Alfombra",
    "en": "Alfombra",
    "category": "standard"
  },
  {
    "de": "reloj",
    "en": "reloj",
    "category": "standard"
  },
  {
    "de": "florero",
    "en": "florero",
    "category": "standard"
  },
  {
    "de": "vela",
    "en": "vela",
    "category": "standard"
  },
  {
    "de": "clave",
    "en": "clave",
    "category": "standard"
  },
  {
    "de": "Mochila",
    "en": "Mochila",
    "category": "standard"
  },
  {
    "de": "maleta",
    "en": "maleta",
    "category": "standard"
  },
  {
    "de": "Gafas",
    "en": "Gafas",
    "category": "standard"
  },
  {
    "de": "sombrero",
    "en": "sombrero",
    "category": "standard"
  },
  {
    "de": "zapato",
    "en": "zapato",
    "category": "standard"
  },
  {
    "de": "chaqueta",
    "en": "chaqueta",
    "category": "standard"
  },
  {
    "de": "Pantalones",
    "en": "Pantalones",
    "category": "standard"
  },
  {
    "de": "vestir",
    "en": "vestir",
    "category": "standard"
  },
  {
    "de": "Guante",
    "en": "Guante",
    "category": "standard"
  },
  {
    "de": "Bufanda",
    "en": "Bufanda",
    "category": "standard"
  },
  {
    "de": "bandera",
    "en": "bandera",
    "category": "standard"
  },
  {
    "de": "pelota",
    "en": "pelota",
    "category": "standard"
  },
  {
    "de": "dragones",
    "en": "dragones",
    "category": "standard"
  },
  {
    "de": "columpio",
    "en": "columpio",
    "category": "standard"
  },
  {
    "de": "diapositiva",
    "en": "diapositiva",
    "category": "standard"
  },
  {
    "de": "tienda de campaña",
    "en": "tienda de campaña",
    "category": "standard"
  },
  {
    "de": "Globo",
    "en": "Globo",
    "category": "standard"
  },
  {
    "de": "Regalo",
    "en": "Regalo",
    "category": "standard"
  },
  {
    "de": "corona",
    "en": "corona",
    "category": "standard"
  },
  {
    "de": "robots",
    "en": "robots",
    "category": "standard"
  },
  {
    "de": "pirata",
    "en": "pirata",
    "category": "standard"
  },
  {
    "de": "caballero",
    "en": "caballero",
    "category": "standard"
  },
  {
    "de": "princesa",
    "en": "princesa",
    "category": "standard"
  },
  {
    "de": "Asistente",
    "en": "Asistente",
    "category": "standard"
  },
  {
    "de": "bruja",
    "en": "bruja",
    "category": "standard"
  },
  {
    "de": "dragón",
    "en": "dragón",
    "category": "standard"
  },
  {
    "de": "unicornio",
    "en": "unicornio",
    "category": "standard"
  },
  {
    "de": "monstruos",
    "en": "monstruos",
    "category": "standard"
  },
  {
    "de": "espíritu",
    "en": "espíritu",
    "category": "standard"
  },
  {
    "de": "esqueleto",
    "en": "esqueleto",
    "category": "standard"
  },
  {
    "de": "vampiro",
    "en": "vampiro",
    "category": "standard"
  },
  {
    "de": "hombre lobo",
    "en": "hombre lobo",
    "category": "standard"
  },
  {
    "de": "extraterrestre",
    "en": "extraterrestre",
    "category": "standard"
  },
  {
    "de": "astronauta",
    "en": "astronauta",
    "category": "standard"
  },
  {
    "de": "Planeta",
    "en": "Planeta",
    "category": "standard"
  },
  {
    "de": "estrella",
    "en": "estrella",
    "category": "standard"
  },
  {
    "de": "luna",
    "en": "luna",
    "category": "standard"
  },
  {
    "de": "sol",
    "en": "sol",
    "category": "standard"
  },
  {
    "de": "Saturno",
    "en": "Saturno",
    "category": "standard"
  },
  {
    "de": "cometa",
    "en": "cometa",
    "category": "standard"
  },
  {
    "de": "Meteorito",
    "en": "Meteorito",
    "category": "standard"
  },
  {
    "de": "OVNI",
    "en": "OVNI",
    "category": "standard"
  },
  {
    "de": "departamento de bomberos",
    "en": "departamento de bomberos",
    "category": "standard"
  },
  {
    "de": "policia",
    "en": "policia",
    "category": "standard"
  },
  {
    "de": "Ambulancia",
    "en": "Ambulancia",
    "category": "standard"
  },
  {
    "de": "Tractor",
    "en": "Tractor",
    "category": "standard"
  },
  {
    "de": "motocicleta",
    "en": "motocicleta",
    "category": "standard"
  },
  {
    "de": "autobús",
    "en": "autobús",
    "category": "standard"
  },
  {
    "de": "tren",
    "en": "tren",
    "category": "standard"
  },
  {
    "de": "Avion",
    "en": "Avion",
    "category": "standard"
  },
  {
    "de": "Helicóptero",
    "en": "Helicóptero",
    "category": "standard"
  },
  {
    "de": "barco",
    "en": "barco",
    "category": "standard"
  },
  {
    "de": "submarino",
    "en": "submarino",
    "category": "standard"
  },
  {
    "de": "velero",
    "en": "velero",
    "category": "standard"
  },
  {
    "de": "Camiones",
    "en": "Camiones",
    "category": "standard"
  },
  {
    "de": "taxi",
    "en": "taxi",
    "category": "standard"
  },
  {
    "de": "semáforo",
    "en": "semáforo",
    "category": "standard"
  },
  {
    "de": "calle",
    "en": "calle",
    "category": "standard"
  },
  {
    "de": "puente",
    "en": "puente",
    "category": "standard"
  },
  {
    "de": "túneles",
    "en": "túneles",
    "category": "standard"
  },
  {
    "de": "monte",
    "en": "monte",
    "category": "standard"
  },
  {
    "de": "río",
    "en": "río",
    "category": "standard"
  },
  {
    "de": "lago",
    "en": "lago",
    "category": "standard"
  },
  {
    "de": "mar",
    "en": "mar",
    "category": "standard"
  },
  {
    "de": "isla",
    "en": "isla",
    "category": "standard"
  },
  {
    "de": "palmera",
    "en": "palmera",
    "category": "standard"
  },
  {
    "de": "bosque",
    "en": "bosque",
    "category": "standard"
  },
  {
    "de": "árbol",
    "en": "árbol",
    "category": "standard"
  },
  {
    "de": "flor",
    "en": "flor",
    "category": "standard"
  },
  {
    "de": "rosa",
    "en": "rosa",
    "category": "standard"
  },
  {
    "de": "girasol",
    "en": "girasol",
    "category": "standard"
  },
  {
    "de": "trébol",
    "en": "trébol",
    "category": "standard"
  },
  {
    "de": "casa de setas",
    "en": "casa de setas",
    "category": "standard"
  },
  {
    "de": "pájaro",
    "en": "pájaro",
    "category": "standard"
  },
  {
    "de": "Búho",
    "en": "Búho",
    "category": "standard"
  },
  {
    "de": "pato",
    "en": "pato",
    "category": "standard"
  },
  {
    "de": "pollo",
    "en": "pollo",
    "category": "standard"
  },
  {
    "de": "gallo",
    "en": "gallo",
    "category": "standard"
  },
  {
    "de": "caballo",
    "en": "caballo",
    "category": "standard"
  },
  {
    "de": "vaca",
    "en": "vaca",
    "category": "standard"
  },
  {
    "de": "ovejas",
    "en": "ovejas",
    "category": "standard"
  },
  {
    "de": "cerdo",
    "en": "cerdo",
    "category": "standard"
  },
  {
    "de": "cabra",
    "en": "cabra",
    "category": "standard"
  },
  {
    "de": "perro",
    "en": "perro",
    "category": "standard"
  },
  {
    "de": "gato",
    "en": "gato",
    "category": "standard"
  },
  {
    "de": "ratón",
    "en": "ratón",
    "category": "standard"
  },
  {
    "de": "conejito",
    "en": "conejito",
    "category": "standard"
  },
  {
    "de": "zorro",
    "en": "zorro",
    "category": "standard"
  },
  {
    "de": "Baer",
    "en": "Baer",
    "category": "standard"
  },
  {
    "de": "Loewe",
    "en": "Loewe",
    "category": "standard"
  },
  {
    "de": "tigres",
    "en": "tigres",
    "category": "standard"
  },
  {
    "de": "elefante",
    "en": "elefante",
    "category": "standard"
  },
  {
    "de": "Jirafa",
    "en": "Jirafa",
    "category": "standard"
  },
  {
    "de": "cebra",
    "en": "cebra",
    "category": "standard"
  },
  {
    "de": "mono",
    "en": "mono",
    "category": "standard"
  },
  {
    "de": "gorila",
    "en": "gorila",
    "category": "standard"
  },
  {
    "de": "panda",
    "en": "panda",
    "category": "standard"
  },
  {
    "de": "Koala",
    "en": "Koala",
    "category": "standard"
  },
  {
    "de": "canguro",
    "en": "canguro",
    "category": "standard"
  },
  {
    "de": "Polluelos de pingüino",
    "en": "Polluelos de pingüino",
    "category": "standard"
  },
  {
    "de": "ballena",
    "en": "ballena",
    "category": "standard"
  },
  {
    "de": "Hai",
    "en": "Hai",
    "category": "standard"
  },
  {
    "de": "delfín",
    "en": "delfín",
    "category": "standard"
  },
  {
    "de": "pulpo",
    "en": "pulpo",
    "category": "standard"
  },
  {
    "de": "tortuga",
    "en": "tortuga",
    "category": "standard"
  },
  {
    "de": "caballito de mar",
    "en": "caballito de mar",
    "category": "standard"
  },
  {
    "de": "pescado",
    "en": "pescado",
    "category": "standard"
  },
  {
    "de": "medusas",
    "en": "medusas",
    "category": "standard"
  },
  {
    "de": "abeja",
    "en": "abeja",
    "category": "standard"
  },
  {
    "de": "mariposa",
    "en": "mariposa",
    "category": "standard"
  },
  {
    "de": "Mariquita",
    "en": "Mariquita",
    "category": "standard"
  },
  {
    "de": "hormiga",
    "en": "hormiga",
    "category": "standard"
  },
  {
    "de": "araña",
    "en": "araña",
    "category": "standard"
  },
  {
    "de": "caracol",
    "en": "caracol",
    "category": "standard"
  },
  {
    "de": "arcoiris",
    "en": "arcoiris",
    "category": "standard"
  },
  {
    "de": "relámpago",
    "en": "relámpago",
    "category": "standard"
  },
  {
    "de": "nieve",
    "en": "nieve",
    "category": "standard"
  },
  {
    "de": "muñeco de nieve",
    "en": "muñeco de nieve",
    "category": "standard"
  },
  {
    "de": "trineo",
    "en": "trineo",
    "category": "standard"
  },
  {
    "de": "patineta",
    "en": "patineta",
    "category": "standard"
  },
  {
    "de": "patines en línea",
    "en": "patines en línea",
    "category": "standard"
  },
  {
    "de": "guitarra",
    "en": "guitarra",
    "category": "standard"
  },
  {
    "de": "Piano",
    "en": "Piano",
    "category": "standard"
  },
  {
    "de": "violín",
    "en": "violín",
    "category": "standard"
  },
  {
    "de": "flauta",
    "en": "flauta",
    "category": "standard"
  },
  {
    "de": "micrófono",
    "en": "micrófono",
    "category": "standard"
  },
  {
    "de": "cámara",
    "en": "cámara",
    "category": "standard"
  },
  {
    "de": "televisión",
    "en": "televisión",
    "category": "standard"
  },
  {
    "de": "portátil",
    "en": "portátil",
    "category": "standard"
  },
  {
    "de": "tableta",
    "en": "tableta",
    "category": "standard"
  },
  {
    "de": "telefono celular",
    "en": "telefono celular",
    "category": "standard"
  },
  {
    "de": "auriculares",
    "en": "auriculares",
    "category": "standard"
  },
  {
    "de": "palanca de mando",
    "en": "palanca de mando",
    "category": "standard"
  },
  {
    "de": "libro",
    "en": "libro",
    "category": "standard"
  },
  {
    "de": "Folleto",
    "en": "Folleto",
    "category": "standard"
  },
  {
    "de": "bolígrafo",
    "en": "bolígrafo",
    "category": "standard"
  },
  {
    "de": "borrador",
    "en": "borrador",
    "category": "standard"
  },
  {
    "de": "gobernante",
    "en": "gobernante",
    "category": "standard"
  },
  {
    "de": "tijeras",
    "en": "tijeras",
    "category": "standard"
  },
  {
    "de": "bumerán",
    "en": "bumerán",
    "category": "standard"
  },
  {
    "de": "linterna",
    "en": "linterna",
    "category": "standard"
  },
  {
    "de": "lupa",
    "en": "lupa",
    "category": "standard"
  },
  {
    "de": "binoculares",
    "en": "binoculares",
    "category": "standard"
  },
  {
    "de": "Brújula",
    "en": "Brújula",
    "category": "standard"
  },
  {
    "de": "castillo de arena",
    "en": "castillo de arena",
    "category": "standard"
  },
  {
    "de": "Concha",
    "en": "Concha",
    "category": "standard"
  },
  {
    "de": "pala",
    "en": "pala",
    "category": "standard"
  },
  {
    "de": "iglú",
    "en": "iglú",
    "category": "standard"
  },
  {
    "de": "pirámide",
    "en": "pirámide",
    "category": "standard"
  },
  {
    "de": "mapa del tesoro",
    "en": "mapa del tesoro",
    "category": "standard"
  },
  {
    "de": "Cofre del tesoro",
    "en": "Cofre del tesoro",
    "category": "standard"
  },
  {
    "de": "varita magica",
    "en": "varita magica",
    "category": "standard"
  },
  {
    "de": "caldero",
    "en": "caldero",
    "category": "standard"
  },
  {
    "de": "Alas de hadas",
    "en": "Alas de hadas",
    "category": "standard"
  },
  {
    "de": "botas de lluvia",
    "en": "botas de lluvia",
    "category": "standard"
  },
  {
    "de": "Despertador",
    "en": "Despertador",
    "category": "standard"
  },
  {
    "de": "cepillo de dientes",
    "en": "cepillo de dientes",
    "category": "standard"
  },
  {
    "de": "Burbuja de jabón",
    "en": "Burbuja de jabón",
    "category": "standard"
  },
  {
    "de": "saltar la cuerda",
    "en": "saltar la cuerda",
    "category": "standard"
  },
  {
    "de": "disco volador",
    "en": "disco volador",
    "category": "standard"
  },
  {
    "de": "aros de hula",
    "en": "aros de hula",
    "category": "standard"
  },
  {
    "de": "patinar",
    "en": "patinar",
    "category": "standard"
  },
  {
    "de": "raqueta de tenis",
    "en": "raqueta de tenis",
    "category": "standard"
  },
  {
    "de": "aro de baloncesto",
    "en": "aro de baloncesto",
    "category": "standard"
  },
  {
    "de": "gol de futbol",
    "en": "gol de futbol",
    "category": "standard"
  },
  {
    "de": "Medalla",
    "en": "Medalla",
    "category": "standard"
  },
  {
    "de": "copa",
    "en": "copa",
    "category": "standard"
  },
  {
    "de": "tambores",
    "en": "tambores",
    "category": "standard"
  },
  {
    "de": "acordeon",
    "en": "acordeon",
    "category": "standard"
  },
  {
    "de": "clave",
    "en": "clave",
    "category": "standard"
  },
  {
    "de": "flamencos",
    "en": "flamencos",
    "category": "standard"
  },
  {
    "de": "pavo real",
    "en": "pavo real",
    "category": "standard"
  },
  {
    "de": "cocodrilo",
    "en": "cocodrilo",
    "category": "standard"
  },
  {
    "de": "hipopótamo",
    "en": "hipopótamo",
    "category": "standard"
  },
  {
    "de": "camaleón",
    "en": "camaleón",
    "category": "standard"
  },
  {
    "de": "rana",
    "en": "rana",
    "category": "standard"
  },
  {
    "de": "cangrejo",
    "en": "cangrejo",
    "category": "standard"
  },
  {
    "de": "langosta",
    "en": "langosta",
    "category": "standard"
  },
  {
    "de": "estrella de mar",
    "en": "estrella de mar",
    "category": "standard"
  },
  {
    "de": "murciélago",
    "en": "murciélago",
    "category": "standard"
  },
  {
    "de": "ardilla",
    "en": "ardilla",
    "category": "standard"
  },
  {
    "de": "erizo",
    "en": "erizo",
    "category": "standard"
  },
  {
    "de": "ciervo",
    "en": "ciervo",
    "category": "standard"
  },
  {
    "de": "burro",
    "en": "burro",
    "category": "standard"
  },
  {
    "de": "llama",
    "en": "llama",
    "category": "standard"
  },
  {
    "de": "castor",
    "en": "castor",
    "category": "standard"
  },
  {
    "de": "nutrias",
    "en": "nutrias",
    "category": "standard"
  },
  {
    "de": "Pereza",
    "en": "Pereza",
    "category": "standard"
  },
  {
    "de": "loro",
    "en": "loro",
    "category": "standard"
  },
  {
    "de": "Strauss",
    "en": "Strauss",
    "category": "standard"
  },
  {
    "de": "pavo",
    "en": "pavo",
    "category": "standard"
  },
  {
    "de": "gorro de cocinero",
    "en": "gorro de cocinero",
    "category": "standard"
  },
  {
    "de": "botas de goma",
    "en": "botas de goma",
    "category": "standard"
  },
  {
    "de": "saco de dormir",
    "en": "saco de dormir",
    "category": "standard"
  },
  {
    "de": "Globo de nieve",
    "en": "Globo de nieve",
    "category": "standard"
  },
  {
    "de": "pistola de agua",
    "en": "pistola de agua",
    "category": "standard"
  },
  {
    "de": "varita de burbujas",
    "en": "varita de burbujas",
    "category": "standard"
  },
  {
    "de": "manojo de llaves",
    "en": "manojo de llaves",
    "category": "standard"
  },
  {
    "de": "carpa de circo",
    "en": "carpa de circo",
    "category": "standard"
  },
  {
    "de": "pelotas de malabarismo",
    "en": "pelotas de malabarismo",
    "category": "standard"
  },
  {
    "de": "Lupa de detectives",
    "en": "Lupa de detectives",
    "category": "standard"
  },
  {
    "de": "gnomo de jardin",
    "en": "gnomo de jardin",
    "category": "standard"
  },
  {
    "de": "Espantapájaros",
    "en": "Espantapájaros",
    "category": "standard"
  },
  {
    "de": "buzón",
    "en": "buzón",
    "category": "standard"
  },
  {
    "de": "Lavadora",
    "en": "Lavadora",
    "category": "standard"
  },
  {
    "de": "aspiradora",
    "en": "aspiradora",
    "category": "standard"
  },
  {
    "de": "bañera",
    "en": "bañera",
    "category": "standard"
  },
  {
    "de": "Silla de dentista",
    "en": "Silla de dentista",
    "category": "standard"
  },
  {
    "de": "fuegos artificiales",
    "en": "fuegos artificiales",
    "category": "standard"
  },
  {
    "de": "Veleta",
    "en": "Veleta",
    "category": "standard"
  },
  {
    "de": "Señalización",
    "en": "Señalización",
    "category": "standard"
  },
  {
    "de": "cadena de nieve",
    "en": "cadena de nieve",
    "category": "standard"
  },
  {
    "de": "hélice de barco",
    "en": "hélice de barco",
    "category": "standard"
  },
  {
    "de": "cascada",
    "en": "cascada",
    "category": "standard"
  },
  {
    "de": "nido de pájaro",
    "en": "nido de pájaro",
    "category": "standard"
  },
  {
    "de": "colmena",
    "en": "colmena",
    "category": "standard"
  },
  {
    "de": "Cóctel",
    "en": "Cóctel",
    "category": "adult"
  },
  {
    "de": "jarra de cerveza",
    "en": "jarra de cerveza",
    "category": "adult"
  },
  {
    "de": "botella de vino",
    "en": "botella de vino",
    "category": "adult"
  },
  {
    "de": "vaso de whisky",
    "en": "vaso de whisky",
    "category": "adult"
  },
  {
    "de": "botella de champán",
    "en": "botella de champán",
    "category": "adult"
  },
  {
    "de": "champán",
    "en": "champán",
    "category": "adult"
  },
  {
    "de": "discoteca",
    "en": "discoteca",
    "category": "adult"
  },
  {
    "de": "bola de discoteca",
    "en": "bola de discoteca",
    "category": "adult"
  },
  {
    "de": "despues de la fiesta",
    "en": "despues de la fiesta",
    "category": "adult"
  },
  {
    "de": "camarero",
    "en": "camarero",
    "category": "adult"
  },
  {
    "de": "barra de karaoke",
    "en": "barra de karaoke",
    "category": "adult"
  },
  {
    "de": "casinos",
    "en": "casinos",
    "category": "adult"
  },
  {
    "de": "Póquer",
    "en": "Póquer",
    "category": "adult"
  },
  {
    "de": "Ruleta",
    "en": "Ruleta",
    "category": "adult"
  },
  {
    "de": "cigarrillo",
    "en": "cigarrillo",
    "category": "adult"
  },
  {
    "de": "shisha",
    "en": "shisha",
    "category": "adult"
  },
  {
    "de": "Desayuno para la resaca",
    "en": "Desayuno para la resaca",
    "category": "adult"
  },
  {
    "de": "despedida de soltero",
    "en": "despedida de soltero",
    "category": "adult"
  },
  {
    "de": "limusina",
    "en": "limusina",
    "category": "adult"
  },
  {
    "de": "Sello del club",
    "en": "Sello del club",
    "category": "adult"
  },
  {
    "de": "tacones altos",
    "en": "tacones altos",
    "category": "adult"
  },
  {
    "de": "lápiz labial",
    "en": "lápiz labial",
    "category": "adult"
  },
  {
    "de": "perfume",
    "en": "perfume",
    "category": "adult"
  },
  {
    "de": "Tatuaje",
    "en": "Tatuaje",
    "category": "adult"
  },
  {
    "de": "perforación",
    "en": "perforación",
    "category": "adult"
  },
  {
    "de": "lencería",
    "en": "lencería",
    "category": "adult"
  },
  {
    "de": "sujetador",
    "en": "sujetador",
    "category": "adult"
  },
  {
    "de": "tanga",
    "en": "tanga",
    "category": "adult"
  },
  {
    "de": "condón",
    "en": "condón",
    "category": "adult"
  },
  {
    "de": "esposas",
    "en": "esposas",
    "category": "adult"
  },
  {
    "de": "látigo",
    "en": "látigo",
    "category": "adult"
  },
  {
    "de": "aceite de masaje",
    "en": "aceite de masaje",
    "category": "adult"
  },
  {
    "de": "jacuzzi",
    "en": "jacuzzi",
    "category": "adult"
  },
  {
    "de": "sauna",
    "en": "sauna",
    "category": "adult"
  },
  {
    "de": "Habitación de hotel",
    "en": "Habitación de hotel",
    "category": "adult"
  },
  {
    "de": "cama doble",
    "en": "cama doble",
    "category": "adult"
  },
  {
    "de": "Pétalos de rosa",
    "en": "Pétalos de rosa",
    "category": "adult"
  },
  {
    "de": "carta de amor",
    "en": "carta de amor",
    "category": "adult"
  },
  {
    "de": "cita a ciegas",
    "en": "cita a ciegas",
    "category": "adult"
  },
  {
    "de": "coquetear",
    "en": "coquetear",
    "category": "adult"
  },
  {
    "de": "anillo de compromiso",
    "en": "anillo de compromiso",
    "category": "adult"
  },
  {
    "de": "noche de bodas",
    "en": "noche de bodas",
    "category": "adult"
  },
  {
    "de": "Baño de burbujas",
    "en": "Baño de burbujas",
    "category": "adult"
  },
  {
    "de": "Antifaz para dormir",
    "en": "Antifaz para dormir",
    "category": "adult"
  },
  {
    "de": "baile en barra",
    "en": "baile en barra",
    "category": "adult"
  },
  {
    "de": "striptease",
    "en": "striptease",
    "category": "adult"
  },
  {
    "de": "Kama Sutra",
    "en": "Kama Sutra",
    "category": "adult"
  },
  {
    "de": "chaqueta de cuero",
    "en": "chaqueta de cuero",
    "category": "adult"
  },
  {
    "de": "Zapato de charol",
    "en": "Zapato de charol",
    "category": "adult"
  },
  {
    "de": "beso de medianoche",
    "en": "beso de medianoche",
    "category": "adult"
  },
  {
    "de": "copa de champán",
    "en": "copa de champán",
    "category": "adult"
  },
  {
    "de": "pista de baile",
    "en": "pista de baile",
    "category": "adult"
  },
  {
    "de": "banda vip",
    "en": "banda vip",
    "category": "adult"
  },
  {
    "de": "Bañadores",
    "en": "Bañadores",
    "category": "adult"
  },
  {
    "de": "biquini",
    "en": "biquini",
    "category": "adult"
  },
  {
    "de": "candado de amor",
    "en": "candado de amor",
    "category": "adult"
  },
  {
    "de": "cena a la luz de las velas",
    "en": "cena a la luz de las velas",
    "category": "adult"
  },
  {
    "de": "boxeador de corazon",
    "en": "boxeador de corazon",
    "category": "adult"
  },
  {
    "de": "autobús de fiesta",
    "en": "autobús de fiesta",
    "category": "adult"
  },
  {
    "de": "nido de amor",
    "en": "nido de amor",
    "category": "adult"
  },
  {
    "de": "juegos previos",
    "en": "juegos previos",
    "category": "adult"
  },
  {
    "de": "Orgasmo",
    "en": "Orgasmo",
    "category": "adult"
  },
  {
    "de": "posición sexual",
    "en": "posición sexual",
    "category": "adult"
  },
  {
    "de": "posición misionera",
    "en": "posición misionera",
    "category": "adult"
  },
  {
    "de": "Posición del ciclista",
    "en": "Posición del ciclista",
    "category": "adult"
  },
  {
    "de": "estilo perrito",
    "en": "estilo perrito",
    "category": "adult"
  },
  {
    "de": "sesenta y nueve",
    "en": "sesenta y nueve",
    "category": "adult"
  },
  {
    "de": "beso francés",
    "en": "beso francés",
    "category": "adult"
  },
  {
    "de": "mordedura de amor",
    "en": "mordedura de amor",
    "category": "adult"
  },
  {
    "de": "Charla de almohada",
    "en": "Charla de almohada",
    "category": "adult"
  },
  {
    "de": "hablar sucio",
    "en": "hablar sucio",
    "category": "adult"
  },
  {
    "de": "Sextear",
    "en": "Sextear",
    "category": "adult"
  },
  {
    "de": "Lubricante",
    "en": "Lubricante",
    "category": "adult"
  },
  {
    "de": "consolador",
    "en": "consolador",
    "category": "adult"
  },
  {
    "de": "vibrador",
    "en": "vibrador",
    "category": "adult"
  },
  {
    "de": "juguetes sexuales",
    "en": "juguetes sexuales",
    "category": "adult"
  },
  {
    "de": "bola de placer",
    "en": "bola de placer",
    "category": "adult"
  },
  {
    "de": "tienda de sexo",
    "en": "tienda de sexo",
    "category": "adult"
  },
  {
    "de": "club de swingers",
    "en": "club de swingers",
    "category": "adult"
  },
  {
    "de": "sexo seguro",
    "en": "sexo seguro",
    "category": "adult"
  },
  {
    "de": "paquete de condones",
    "en": "paquete de condones",
    "category": "adult"
  },
  {
    "de": "ropa interior sexy",
    "en": "ropa interior sexy",
    "category": "adult"
  },
  {
    "de": "Tirantes",
    "en": "Tirantes",
    "category": "adult"
  },
  {
    "de": "traje de látex",
    "en": "traje de látex",
    "category": "adult"
  },
  {
    "de": "Con los ojos vendados",
    "en": "Con los ojos vendados",
    "category": "adult"
  },
  {
    "de": "Columpio de amor",
    "en": "Columpio de amor",
    "category": "adult"
  },
  {
    "de": "baile erótico",
    "en": "baile erótico",
    "category": "adult"
  },
  {
    "de": "burlesco",
    "en": "burlesco",
    "category": "adult"
  },
  {
    "de": "fetiche",
    "en": "fetiche",
    "category": "adult"
  },
  {
    "de": "bdsm",
    "en": "bdsm",
    "category": "adult"
  },
  {
    "de": "novela erótica",
    "en": "novela erótica",
    "category": "adult"
  },
  {
    "de": "inmersión delgada",
    "en": "inmersión delgada",
    "category": "adult"
  },
  {
    "de": "topless",
    "en": "topless",
    "category": "adult"
  },
  {
    "de": "Beso en la boca",
    "en": "Beso en la boca",
    "category": "adult"
  },
  {
    "de": "juego de roles",
    "en": "juego de roles",
    "category": "adult"
  },
  {
    "de": "cubo de amor",
    "en": "cubo de amor",
    "category": "adult"
  },
  {
    "de": "palacio del placer",
    "en": "palacio del placer",
    "category": "adult"
  },
  {
    "de": "club de striptease",
    "en": "club de striptease",
    "category": "adult"
  },
  {
    "de": "baile privado",
    "en": "baile privado",
    "category": "adult"
  },
  {
    "de": "pintura corporal",
    "en": "pintura corporal",
    "category": "adult"
  },
  {
    "de": "Mojito",
    "en": "Mojito",
    "category": "adult"
  },
  {
    "de": "tequila",
    "en": "tequila",
    "category": "adult"
  },
  {
    "de": "corchos de vino",
    "en": "corchos de vino",
    "category": "adult"
  },
  {
    "de": "abridor de botellas",
    "en": "abridor de botellas",
    "category": "adult"
  },
  {
    "de": "Estera de cerveza",
    "en": "Estera de cerveza",
    "category": "adult"
  },
  {
    "de": "botella de deposito",
    "en": "botella de deposito",
    "category": "adult"
  },
  {
    "de": "Gorila",
    "en": "Gorila",
    "category": "adult"
  },
  {
    "de": "cabina de DJ",
    "en": "cabina de DJ",
    "category": "adult"
  },
  {
    "de": "cinta del festival",
    "en": "cinta del festival",
    "category": "adult"
  },
  {
    "de": "bar del hotel",
    "en": "bar del hotel",
    "category": "adult"
  },
  {
    "de": "Servicio de habitaciones",
    "en": "Servicio de habitaciones",
    "category": "adult"
  },
  {
    "de": "pastel de bodas",
    "en": "pastel de bodas",
    "category": "adult"
  },
  {
    "de": "ramo de novia",
    "en": "ramo de novia",
    "category": "adult"
  },
  {
    "de": "anillo de bodas",
    "en": "anillo de bodas",
    "category": "adult"
  },
  {
    "de": "papeles de divorcio",
    "en": "papeles de divorcio",
    "category": "adult"
  },
  {
    "de": "aplicación de citas",
    "en": "aplicación de citas",
    "category": "adult"
  },
  {
    "de": "perfil de yesca",
    "en": "perfil de yesca",
    "category": "adult"
  },
  {
    "de": "vacaciones de amor",
    "en": "vacaciones de amor",
    "category": "adult"
  },
  {
    "de": "Terapia de pareja",
    "en": "Terapia de pareja",
    "category": "adult"
  },
  {
    "de": "novela romántica",
    "en": "novela romántica",
    "category": "adult"
  },
  {
    "de": "bata",
    "en": "bata",
    "category": "adult"
  },
  {
    "de": "Body de encaje",
    "en": "Body de encaje",
    "category": "adult"
  },
  {
    "de": "Medias de rejilla",
    "en": "Medias de rejilla",
    "category": "adult"
  },
  {
    "de": "Cuello halter",
    "en": "Cuello halter",
    "category": "adult"
  },
  {
    "de": "Vela de masaje",
    "en": "Vela de masaje",
    "category": "adult"
  },
  {
    "de": "sábanas",
    "en": "sábanas",
    "category": "adult"
  },
  {
    "de": "boa de plumas",
    "en": "boa de plumas",
    "category": "adult"
  },
  {
    "de": "hacer el amor",
    "en": "hacer el amor",
    "category": "adult"
  },
  {
    "de": "acrobacias en la cama",
    "en": "acrobacias en la cama",
    "category": "adult"
  },
  {
    "de": "máquina expendedora de condones",
    "en": "máquina expendedora de condones",
    "category": "adult"
  },
  {
    "de": "Anticoncepción",
    "en": "Anticoncepción",
    "category": "adult"
  },
  {
    "de": "prueba de embarazo",
    "en": "prueba de embarazo",
    "category": "adult"
  },
  {
    "de": "copa menstrual",
    "en": "copa menstrual",
    "category": "adult"
  },
  {
    "de": "Feria erótica",
    "en": "Feria erótica",
    "category": "adult"
  },
  {
    "de": "Fiesta swinger",
    "en": "Fiesta swinger",
    "category": "adult"
  },
  {
    "de": "curso de baile en pareja",
    "en": "curso de baile en pareja",
    "category": "adult"
  },
  {
    "de": "Seducción",
    "en": "Seducción",
    "category": "adult"
  },
  {
    "de": "Impresión de beso",
    "en": "Impresión de beso",
    "category": "adult"
  },
  {
    "de": "chupetón",
    "en": "chupetón",
    "category": "adult"
  },
  {
    "de": "vale de amor",
    "en": "vale de amor",
    "category": "adult"
  },
  {
    "de": "Mini nevera para hoteles",
    "en": "Mini nevera para hoteles",
    "category": "adult"
  },
  {
    "de": "Ducha de champán",
    "en": "Ducha de champán",
    "category": "adult"
  },
  {
    "de": "danza burlesca",
    "en": "danza burlesca",
    "category": "adult"
  },
  {
    "de": "bailarina del vientre",
    "en": "bailarina del vientre",
    "category": "adult"
  },
  {
    "de": "Ruta de bares",
    "en": "Ruta de bares",
    "category": "adult"
  },
  {
    "de": "hora feliz",
    "en": "hora feliz",
    "category": "adult"
  },
  {
    "de": "micrófono de karaoke",
    "en": "micrófono de karaoke",
    "category": "adult"
  },
  {
    "de": "zapatos de baile",
    "en": "zapatos de baile",
    "category": "adult"
  },
  {
    "de": "luna de miel",
    "en": "luna de miel",
    "category": "adult"
  },
  {
    "de": "Consejería matrimonial",
    "en": "Consejería matrimonial",
    "category": "adult"
  },
  {
    "de": "mensaje de amor",
    "en": "mensaje de amor",
    "category": "adult"
  },
  {
    "de": "fiesta de compromiso",
    "en": "fiesta de compromiso",
    "category": "adult"
  },
  {
    "de": "hotel para adultos",
    "en": "hotel para adultos",
    "category": "adult"
  },
  {
    "de": "ropa interior atractiva",
    "en": "ropa interior atractiva",
    "category": "adult"
  },
  {
    "de": "perlas de placer",
    "en": "perlas de placer",
    "category": "adult"
  },
  {
    "de": "juguetes eróticos",
    "en": "juguetes eróticos",
    "category": "adult"
  },
  {
    "de": "Paquete de condones XXL",
    "en": "Paquete de condones XXL",
    "category": "adult"
  },
  {
    "de": "Póquer desnudo",
    "en": "Póquer desnudo",
    "category": "adult"
  },
  {
    "de": "masaje sensual",
    "en": "masaje sensual",
    "category": "adult"
  },
  {
    "de": "Restricciones de terciopelo",
    "en": "Restricciones de terciopelo",
    "category": "adult"
  },
  {
    "de": "inversión de roles",
    "en": "inversión de roles",
    "category": "adult"
  },
  {
    "de": "Baño de burbujas para dos",
    "en": "Baño de burbujas para dos",
    "category": "adult"
  },
  {
    "de": "Ojo de cerradura",
    "en": "Ojo de cerradura",
    "category": "adult"
  },
  {
    "de": "nido de amor",
    "en": "nido de amor",
    "category": "adult"
  },
  {
    "de": "pene",
    "en": "pene",
    "category": "adult"
  },
  {
    "de": "vagina",
    "en": "vagina",
    "category": "adult"
  },
  {
    "de": "vulva",
    "en": "vulva",
    "category": "adult"
  },
  {
    "de": "clítoris",
    "en": "clítoris",
    "category": "adult"
  },
  {
    "de": "testiculos",
    "en": "testiculos",
    "category": "adult"
  },
  {
    "de": "escroto",
    "en": "escroto",
    "category": "adult"
  },
  {
    "de": "bellota",
    "en": "bellota",
    "category": "adult"
  },
  {
    "de": "prepucio",
    "en": "prepucio",
    "category": "adult"
  },
  {
    "de": "Vello púbico",
    "en": "Vello púbico",
    "category": "adult"
  },
  {
    "de": "cola",
    "en": "cola",
    "category": "adult"
  },
  {
    "de": "Moisés",
    "en": "Moisés",
    "category": "adult"
  },
  {
    "de": "tetas",
    "en": "tetas",
    "category": "adult"
  },
  {
    "de": "pezones",
    "en": "pezones",
    "category": "adult"
  },
  {
    "de": "gilipollas",
    "en": "gilipollas",
    "category": "adult"
  },
  {
    "de": "erección",
    "en": "erección",
    "category": "adult"
  },
  {
    "de": "esperma",
    "en": "esperma",
    "category": "adult"
  },
  {
    "de": "Eyaculación",
    "en": "Eyaculación",
    "category": "adult"
  },
  {
    "de": "sexo",
    "en": "sexo",
    "category": "adult"
  },
  {
    "de": "joder",
    "en": "joder",
    "category": "adult"
  },
  {
    "de": "pájaros",
    "en": "pájaros",
    "category": "adult"
  },
  {
    "de": "explosión",
    "en": "explosión",
    "category": "adult"
  },
  {
    "de": "masturbarse",
    "en": "masturbarse",
    "category": "adult"
  },
  {
    "de": "masturbación",
    "en": "masturbación",
    "category": "adult"
  },
  {
    "de": "Burbujas",
    "en": "Burbujas",
    "category": "adult"
  },
  {
    "de": "lamer",
    "en": "lamer",
    "category": "adult"
  },
  {
    "de": "corrida",
    "en": "corrida",
    "category": "adult"
  },
  {
    "de": "sexo oral",
    "en": "sexo oral",
    "category": "adult"
  },
  {
    "de": "sexo anal",
    "en": "sexo anal",
    "category": "adult"
  },
  {
    "de": "mamada",
    "en": "mamada",
    "category": "adult"
  },
  {
    "de": "trabajo manual",
    "en": "trabajo manual",
    "category": "adult"
  },
  {
    "de": "dedos",
    "en": "dedos",
    "category": "adult"
  },
  {
    "de": "rapidito",
    "en": "rapidito",
    "category": "adult"
  },
  {
    "de": "trío",
    "en": "trío",
    "category": "adult"
  },
  {
    "de": "sexo en grupo",
    "en": "sexo en grupo",
    "category": "adult"
  },
  {
    "de": "gangbang",
    "en": "gangbang",
    "category": "adult"
  },
  {
    "de": "Una aventura de noche",
    "en": "Una aventura de noche",
    "category": "adult"
  },
  {
    "de": "sexo en el auto",
    "en": "sexo en el auto",
    "category": "adult"
  },
  {
    "de": "Sexo en la ducha",
    "en": "Sexo en la ducha",
    "category": "adult"
  },
  {
    "de": "pelicula porno",
    "en": "pelicula porno",
    "category": "adult"
  },
  {
    "de": "estrella porno",
    "en": "estrella porno",
    "category": "adult"
  },
  {
    "de": "Fanáticos",
    "en": "Fanáticos",
    "category": "adult"
  },
  {
    "de": "burdel",
    "en": "burdel",
    "category": "adult"
  },
  {
    "de": "puf",
    "en": "puf",
    "category": "adult"
  },
  {
    "de": "Barrio rojo",
    "en": "Barrio rojo",
    "category": "adult"
  },
  {
    "de": "pretendiente",
    "en": "pretendiente",
    "category": "adult"
  },
  {
    "de": "prostitutas",
    "en": "prostitutas",
    "category": "adult"
  },
  {
    "de": "esclavitud",
    "en": "esclavitud",
    "category": "adult"
  },
  {
    "de": "esclavitud",
    "en": "esclavitud",
    "category": "adult"
  },
  {
    "de": "shibari",
    "en": "shibari",
    "category": "adult"
  },
  {
    "de": "dominatriz",
    "en": "dominatriz",
    "category": "adult"
  },
  {
    "de": "dominatriz",
    "en": "dominatriz",
    "category": "adult"
  },
  {
    "de": "azotes",
    "en": "azotes",
    "category": "adult"
  },
  {
    "de": "Látigo de cuero",
    "en": "Látigo de cuero",
    "category": "adult"
  },
  {
    "de": "mordaza",
    "en": "mordaza",
    "category": "adult"
  },
  {
    "de": "Pinza para pezones",
    "en": "Pinza para pezones",
    "category": "adult"
  },
  {
    "de": "tapón anal",
    "en": "tapón anal",
    "category": "adult"
  },
  {
    "de": "cuentas anales",
    "en": "cuentas anales",
    "category": "adult"
  },
  {
    "de": "Anillo para el pene",
    "en": "Anillo para el pene",
    "category": "adult"
  },
  {
    "de": "Correa en",
    "en": "Correa en",
    "category": "adult"
  },
  {
    "de": "máscara de látex",
    "en": "máscara de látex",
    "category": "adult"
  },
  {
    "de": "puta",
    "en": "puta",
    "category": "adult"
  },
  {
    "de": "hijo de puta",
    "en": "hijo de puta",
    "category": "adult"
  },
  {
    "de": "Pendejo",
    "en": "Pendejo",
    "category": "adult"
  },
  {
    "de": "violín",
    "en": "violín",
    "category": "adult"
  },
  {
    "de": "Besador de culos",
    "en": "Besador de culos",
    "category": "adult"
  },
  {
    "de": "cara de culo",
    "en": "cara de culo",
    "category": "adult"
  },
  {
    "de": "Mierda",
    "en": "Mierda",
    "category": "adult"
  },
  {
    "de": "Mierda",
    "en": "Mierda",
    "category": "adult"
  },
  {
    "de": "Basura",
    "en": "Basura",
    "category": "adult"
  },
  {
    "de": "perra",
    "en": "perra",
    "category": "adult"
  },
  {
    "de": "perra",
    "en": "perra",
    "category": "adult"
  },
  {
    "de": "perra",
    "en": "perra",
    "category": "adult"
  },
  {
    "de": "joder",
    "en": "joder",
    "category": "adult"
  },
  {
    "de": "Vete a la mierda",
    "en": "Vete a la mierda",
    "category": "adult"
  },
  {
    "de": "lameme",
    "en": "lameme",
    "category": "adult"
  },
  {
    "de": "Vete a la mierda",
    "en": "Vete a la mierda",
    "category": "adult"
  },
  {
    "de": "Mierda",
    "en": "Mierda",
    "category": "adult"
  },
  {
    "de": "vomitar",
    "en": "vomitar",
    "category": "adult"
  },
  {
    "de": "Estúpido hijo de puta",
    "en": "Estúpido hijo de puta",
    "category": "adult"
  },
  {
    "de": "cara de saco",
    "en": "cara de saco",
    "category": "adult"
  },
  {
    "de": "alcohol",
    "en": "alcohol",
    "category": "adult"
  },
  {
    "de": "vodka",
    "en": "vodka",
    "category": "adult"
  },
  {
    "de": "licor",
    "en": "licor",
    "category": "adult"
  },
  {
    "de": "vaso de chupito",
    "en": "vaso de chupito",
    "category": "adult"
  },
  {
    "de": "ron",
    "en": "ron",
    "category": "adult"
  },
  {
    "de": "ginebra",
    "en": "ginebra",
    "category": "adult"
  },
  {
    "de": "whisky",
    "en": "whisky",
    "category": "adult"
  },
  {
    "de": "Absenta",
    "en": "Absenta",
    "category": "adult"
  },
  {
    "de": "licor",
    "en": "licor",
    "category": "adult"
  },
  {
    "de": "grano",
    "en": "grano",
    "category": "adult"
  },
  {
    "de": "Jägermeister",
    "en": "Jägermeister",
    "category": "adult"
  },
  {
    "de": "pipa de cerveza",
    "en": "pipa de cerveza",
    "category": "adult"
  },
  {
    "de": "Pong de cerveza",
    "en": "Pong de cerveza",
    "category": "adult"
  },
  {
    "de": "bola fluky",
    "en": "bola fluky",
    "category": "adult"
  },
  {
    "de": "juego de beber",
    "en": "juego de beber",
    "category": "adult"
  },
  {
    "de": "beber en exceso",
    "en": "beber en exceso",
    "category": "adult"
  },
  {
    "de": "Intoxicación completa",
    "en": "Intoxicación completa",
    "category": "adult"
  },
  {
    "de": "lágrima de película",
    "en": "lágrima de película",
    "category": "adult"
  },
  {
    "de": "por mil",
    "en": "por mil",
    "category": "adult"
  },
  {
    "de": "prueba de alcoholemia",
    "en": "prueba de alcoholemia",
    "category": "adult"
  },
  {
    "de": "fumar hierba",
    "en": "fumar hierba",
    "category": "adult"
  },
  {
    "de": "cannabis",
    "en": "cannabis",
    "category": "adult"
  },
  {
    "de": "marihuana",
    "en": "marihuana",
    "category": "adult"
  },
  {
    "de": "hierba",
    "en": "hierba",
    "category": "adult"
  },
  {
    "de": "hachís",
    "en": "hachís",
    "category": "adult"
  },
  {
    "de": "conjunta",
    "en": "conjunta",
    "category": "adult"
  },
  {
    "de": "bong",
    "en": "bong",
    "category": "adult"
  },
  {
    "de": "Pito para fumar",
    "en": "Pito para fumar",
    "category": "adult"
  },
  {
    "de": "Molinillo",
    "en": "Molinillo",
    "category": "adult"
  },
  {
    "de": "papel de liar",
    "en": "papel de liar",
    "category": "adult"
  },
  {
    "de": "pastel espacial",
    "en": "pastel espacial",
    "category": "adult"
  },
  {
    "de": "galleta de hachís",
    "en": "galleta de hachís",
    "category": "adult"
  },
  {
    "de": "bocadillos",
    "en": "bocadillos",
    "category": "adult"
  },
  {
    "de": "cafeteria",
    "en": "cafeteria",
    "category": "adult"
  },
  {
    "de": "fumeta",
    "en": "fumeta",
    "category": "adult"
  },
  {
    "de": "taco",
    "en": "taco",
    "category": "standard"
  },
  {
    "de": "tamal",
    "en": "tamal",
    "category": "standard"
  },
  {
    "de": "elote",
    "en": "elote",
    "category": "standard"
  },
  {
    "de": "piñata",
    "en": "piñata",
    "category": "standard"
  },
  {
    "de": "mariachi",
    "en": "mariachi",
    "category": "standard"
  },
  {
    "de": "ajolote",
    "en": "ajolote",
    "category": "standard"
  },
  {
    "de": "nopal",
    "en": "nopal",
    "category": "standard"
  },
  {
    "de": "catrina",
    "en": "catrina",
    "category": "standard"
  },
  {
    "de": "luchador",
    "en": "luchador",
    "category": "standard"
  },
  {
    "de": "máscara de lucha",
    "en": "máscara de lucha",
    "category": "standard"
  },
  {
    "de": "sarape",
    "en": "sarape",
    "category": "standard"
  },
  {
    "de": "jacaranda",
    "en": "jacaranda",
    "category": "standard"
  },
  {
    "de": "cenote",
    "en": "cenote",
    "category": "standard"
  },
  {
    "de": "camión",
    "en": "camión",
    "category": "standard"
  },
  {
    "de": "metro",
    "en": "metro",
    "category": "standard"
  },
  {
    "de": "puesto de mercado",
    "en": "puesto de mercado",
    "category": "standard"
  },
  {
    "de": "molcajete",
    "en": "molcajete",
    "category": "standard"
  },
  {
    "de": "comal",
    "en": "comal",
    "category": "standard"
  },
  {
    "de": "tortilla",
    "en": "tortilla",
    "category": "standard"
  },
  {
    "de": "concha",
    "en": "concha",
    "category": "standard"
  },
  {
    "de": "churro",
    "en": "churro",
    "category": "standard"
  },
  {
    "de": "jaguar",
    "en": "jaguar",
    "category": "standard"
  },
  {
    "de": "vaquita marina",
    "en": "vaquita marina",
    "category": "standard"
  },
  {
    "de": "margarita",
    "en": "margarita",
    "category": "standard"
  },
  {
    "de": "mezcal",
    "en": "mezcal",
    "category": "standard"
  },
  {
    "de": "papel picado",
    "en": "papel picado",
    "category": "standard"
  },
  {
    "de": "ofrenda",
    "en": "ofrenda",
    "category": "standard"
  },
  {
    "de": "trompo",
    "en": "trompo",
    "category": "standard"
  },
  {
    "de": "salsa",
    "en": "salsa",
    "category": "standard"
  },
  {
    "de": "frijoles",
    "en": "frijoles",
    "category": "standard"
  },
  {
    "de": "calavera",
    "en": "calavera",
    "category": "standard"
  },
  {
    "de": "lucha libre",
    "en": "lucha libre",
    "category": "standard"
  }
] as const;

export function drawingWordPool(language: SupportedLanguage, category: ZeichnenUndErratenWordCategory): string[] {
  return drawingWords.filter((entry) => category === "all" || entry.category === category).map((entry) => entry[language === "en" ? "en" : "de"]);
}
