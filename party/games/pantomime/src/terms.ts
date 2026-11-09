// Original, bilingual prompts chosen for recognisable physical actions.
const standardTerms = [
  {
    "id": "term-1",
    "de": "Avión",
    "en": "Avión"
  },
  {
    "id": "term-2",
    "de": "Cepillarse los dientes",
    "en": "Cepillarse los dientes"
  },
  {
    "id": "term-3",
    "de": "cactus",
    "en": "cactus"
  },
  {
    "id": "term-4",
    "de": "unicornio",
    "en": "unicornio"
  },
  {
    "id": "term-5",
    "de": "Hacer café",
    "en": "Hacer café"
  },
  {
    "id": "term-6",
    "de": "buzo",
    "en": "buzo"
  },
  {
    "id": "term-7",
    "de": "caballero",
    "en": "caballero"
  },
  {
    "id": "term-8",
    "de": "montaña rusa",
    "en": "montaña rusa"
  },
  {
    "id": "term-9",
    "de": "cocinar",
    "en": "cocinar"
  },
  {
    "id": "term-10",
    "de": "tocar la guitarra",
    "en": "tocar la guitarra"
  },
  {
    "id": "term-11",
    "de": "muñeco de nieve",
    "en": "muñeco de nieve"
  },
  {
    "id": "term-12",
    "de": "fantasma",
    "en": "fantasma"
  },
  {
    "id": "term-13",
    "de": "perro",
    "en": "perro"
  },
  {
    "id": "term-14",
    "de": "gato",
    "en": "gato"
  },
  {
    "id": "term-15",
    "de": "elefante",
    "en": "elefante"
  },
  {
    "id": "term-16",
    "de": "Jirafa",
    "en": "Jirafa"
  },
  {
    "id": "term-17",
    "de": "pingüino",
    "en": "pingüino"
  },
  {
    "id": "term-18",
    "de": "canguro",
    "en": "canguro"
  },
  {
    "id": "term-19",
    "de": "rana",
    "en": "rana"
  },
  {
    "id": "term-20",
    "de": "serpiente",
    "en": "serpiente"
  },
  {
    "id": "term-21",
    "de": "pollo",
    "en": "pollo"
  },
  {
    "id": "term-22",
    "de": "mono",
    "en": "mono"
  },
  {
    "id": "term-23",
    "de": "mariposa",
    "en": "mariposa"
  },
  {
    "id": "term-24",
    "de": "cangrejo",
    "en": "cangrejo"
  },
  {
    "id": "term-25",
    "de": "cocodrilo",
    "en": "cocodrilo"
  },
  {
    "id": "term-26",
    "de": "caballo",
    "en": "caballo"
  },
  {
    "id": "term-27",
    "de": "caracol",
    "en": "caracol"
  },
  {
    "id": "term-28",
    "de": "abeja",
    "en": "abeja"
  },
  {
    "id": "term-29",
    "de": "Tiburón",
    "en": "Tiburón"
  },
  {
    "id": "term-30",
    "de": "Flamenco",
    "en": "Flamenco"
  },
  {
    "id": "term-31",
    "de": "bombero",
    "en": "bombero"
  },
  {
    "id": "term-32",
    "de": "Asistente personal",
    "en": "Asistente personal"
  },
  {
    "id": "term-33",
    "de": "Director de orquesta",
    "en": "Director de orquesta"
  },
  {
    "id": "term-34",
    "de": "Fotógrafo",
    "en": "Fotógrafo"
  },
  {
    "id": "term-35",
    "de": "peluquero",
    "en": "peluquero"
  },
  {
    "id": "term-36",
    "de": "jardinero",
    "en": "jardinero"
  },
  {
    "id": "term-37",
    "de": "pintor",
    "en": "pintor"
  },
  {
    "id": "term-38",
    "de": "policía",
    "en": "policía"
  },
  {
    "id": "term-39",
    "de": "astronauta",
    "en": "astronauta"
  },
  {
    "id": "term-40",
    "de": "pirata",
    "en": "pirata"
  },
  {
    "id": "term-41",
    "de": "Robot",
    "en": "Robot"
  },
  {
    "id": "term-42",
    "de": "detective",
    "en": "detective"
  },
  {
    "id": "term-43",
    "de": "Nadar",
    "en": "Nadar"
  },
  {
    "id": "term-44",
    "de": "esquiar",
    "en": "esquiar"
  },
  {
    "id": "term-45",
    "de": "boxeo",
    "en": "boxeo"
  },
  {
    "id": "term-46",
    "de": "tiro con arco",
    "en": "tiro con arco"
  },
  {
    "id": "term-47",
    "de": "jugar tenis",
    "en": "jugar tenis"
  },
  {
    "id": "term-48",
    "de": "Jugar fútbol",
    "en": "Jugar fútbol"
  },
  {
    "id": "term-49",
    "de": "jugar baloncesto",
    "en": "jugar baloncesto"
  },
  {
    "id": "term-50",
    "de": "levantar pesas",
    "en": "levantar pesas"
  },
  {
    "id": "term-51",
    "de": "surfear",
    "en": "surfear"
  },
  {
    "id": "term-52",
    "de": "remo",
    "en": "remo"
  },
  {
    "id": "term-53",
    "de": "montar a caballo",
    "en": "montar a caballo"
  },
  {
    "id": "term-54",
    "de": "saltar la cuerda",
    "en": "saltar la cuerda"
  },
  {
    "id": "term-55",
    "de": "Bailar ballet",
    "en": "Bailar ballet"
  },
  {
    "id": "term-56",
    "de": "escalada",
    "en": "escalada"
  },
  {
    "id": "term-57",
    "de": "jugar bolos",
    "en": "jugar bolos"
  },
  {
    "id": "term-58",
    "de": "patinaje sobre hielo",
    "en": "patinaje sobre hielo"
  },
  {
    "id": "term-59",
    "de": "Pasar la aspiradora",
    "en": "Pasar la aspiradora"
  },
  {
    "id": "term-60",
    "de": "Limpiar ventanas",
    "en": "Limpiar ventanas"
  },
  {
    "id": "term-61",
    "de": "planchado",
    "en": "planchado"
  },
  {
    "id": "term-62",
    "de": "colgar la ropa",
    "en": "colgar la ropa"
  },
  {
    "id": "term-63",
    "de": "lavar los platos",
    "en": "lavar los platos"
  },
  {
    "id": "term-64",
    "de": "secar el cabello",
    "en": "secar el cabello"
  },
  {
    "id": "term-65",
    "de": "afeitado",
    "en": "afeitado"
  },
  {
    "id": "term-66",
    "de": "maquillaje",
    "en": "maquillaje"
  },
  {
    "id": "term-67",
    "de": "Ducharse",
    "en": "Ducharse"
  },
  {
    "id": "term-68",
    "de": "Atar los cordones",
    "en": "Atar los cordones"
  },
  {
    "id": "term-69",
    "de": "Haz las maletas",
    "en": "Haz las maletas"
  },
  {
    "id": "term-70",
    "de": "envolver un regalo",
    "en": "envolver un regalo"
  },
  {
    "id": "term-71",
    "de": "Apagar el despertador",
    "en": "Apagar el despertador"
  },
  {
    "id": "term-72",
    "de": "quedarse dormido",
    "en": "quedarse dormido"
  },
  {
    "id": "term-73",
    "de": "Estornudar",
    "en": "Estornudar"
  },
  {
    "id": "term-74",
    "de": "hipo",
    "en": "hipo"
  },
  {
    "id": "term-75",
    "de": "Tener frío",
    "en": "Tener frío"
  },
  {
    "id": "term-76",
    "de": "Quemarse con el sol",
    "en": "Quemarse con el sol"
  },
  {
    "id": "term-77",
    "de": "hornear pizza",
    "en": "hornear pizza"
  },
  {
    "id": "term-78",
    "de": "comer sopa",
    "en": "comer sopa"
  },
  {
    "id": "term-79",
    "de": "Comer espagueti",
    "en": "Comer espagueti"
  },
  {
    "id": "term-80",
    "de": "Probar el limón",
    "en": "Probar el limón"
  },
  {
    "id": "term-81",
    "de": "cortar cebolla",
    "en": "cortar cebolla"
  },
  {
    "id": "term-82",
    "de": "Amasar la masa",
    "en": "Amasar la masa"
  },
  {
    "id": "term-83",
    "de": "comer helado",
    "en": "comer helado"
  },
  {
    "id": "term-84",
    "de": "hacer palomitas de maíz",
    "en": "hacer palomitas de maíz"
  },
  {
    "id": "term-85",
    "de": "pelar plátano",
    "en": "pelar plátano"
  },
  {
    "id": "term-86",
    "de": "beber té",
    "en": "beber té"
  },
  {
    "id": "term-87",
    "de": "coco abierto",
    "en": "coco abierto"
  },
  {
    "id": "term-88",
    "de": "decorar pasteles",
    "en": "decorar pasteles"
  },
  {
    "id": "term-89",
    "de": "paraguas abierto",
    "en": "paraguas abierto"
  },
  {
    "id": "term-90",
    "de": "volar cometas",
    "en": "volar cometas"
  },
  {
    "id": "term-91",
    "de": "montar la tienda",
    "en": "montar la tienda"
  },
  {
    "id": "term-92",
    "de": "pesca",
    "en": "pesca"
  },
  {
    "id": "term-93",
    "de": "pelea de bolas de nieve",
    "en": "pelea de bolas de nieve"
  },
  {
    "id": "term-94",
    "de": "recogiendo flores",
    "en": "recogiendo flores"
  },
  {
    "id": "term-95",
    "de": "pasear al perro",
    "en": "pasear al perro"
  },
  {
    "id": "term-96",
    "de": "atrapar mosquitos",
    "en": "atrapar mosquitos"
  },
  {
    "id": "term-97",
    "de": "Senderismo en barro",
    "en": "Senderismo en barro"
  },
  {
    "id": "term-98",
    "de": "Viento en contra",
    "en": "Viento en contra"
  },
  {
    "id": "term-99",
    "de": "resbalando",
    "en": "resbalando"
  },
  {
    "id": "term-100",
    "de": "arena caliente",
    "en": "arena caliente"
  },
  {
    "id": "term-101",
    "de": "andar en bicicleta",
    "en": "andar en bicicleta"
  },
  {
    "id": "term-102",
    "de": "Estaciona el auto",
    "en": "Estaciona el auto"
  },
  {
    "id": "term-103",
    "de": "andar en motocicleta",
    "en": "andar en motocicleta"
  },
  {
    "id": "term-104",
    "de": "perder el autobús",
    "en": "perder el autobús"
  },
  {
    "id": "term-105",
    "de": "cambiar llantas",
    "en": "cambiar llantas"
  },
  {
    "id": "term-106",
    "de": "toma el ascensor",
    "en": "toma el ascensor"
  },
  {
    "id": "term-107",
    "de": "Abre la puerta",
    "en": "Abre la puerta"
  },
  {
    "id": "term-108",
    "de": "muro invisible",
    "en": "muro invisible"
  },
  {
    "id": "term-109",
    "de": "Paseo por la cuerda floja",
    "en": "Paseo por la cuerda floja"
  },
  {
    "id": "term-110",
    "de": "desenterrar tesoro",
    "en": "desenterrar tesoro"
  },
  {
    "id": "term-111",
    "de": "Ahuyentar a la mosca",
    "en": "Ahuyentar a la mosca"
  },
  {
    "id": "term-112",
    "de": "inflar globo",
    "en": "inflar globo"
  },
  {
    "id": "term-113",
    "de": "toca el piano",
    "en": "toca el piano"
  },
  {
    "id": "term-114",
    "de": "toca el violín",
    "en": "toca el violín"
  },
  {
    "id": "term-115",
    "de": "tamborilear",
    "en": "tamborilear"
  },
  {
    "id": "term-116",
    "de": "malabares",
    "en": "malabares"
  },
  {
    "id": "term-117",
    "de": "marioneta",
    "en": "marioneta"
  },
  {
    "id": "term-118",
    "de": "camara lenta",
    "en": "camara lenta"
  },
  {
    "id": "term-119",
    "de": "Imagen reflejada",
    "en": "Imagen reflejada"
  },
  {
    "id": "term-120",
    "de": "superhéroe",
    "en": "superhéroe"
  },
  {
    "id": "term-121",
    "de": "Zombi",
    "en": "Zombi"
  },
  {
    "id": "term-122",
    "de": "sonámbulo",
    "en": "sonámbulo"
  },
  {
    "id": "term-123",
    "de": "bebe tranquilo",
    "en": "bebe tranquilo"
  },
  {
    "id": "term-124",
    "de": "Tomate una selfie",
    "en": "Tomate una selfie"
  },
  {
    "id": "term-125",
    "de": "Hacer tortillas",
    "en": "Hacer tortillas"
  },
  {
    "id": "term-126",
    "de": "Bailar cumbia",
    "en": "Bailar cumbia"
  },
  {
    "id": "term-127",
    "de": "Lucha libre",
    "en": "Lucha libre"
  },
  {
    "id": "term-128",
    "de": "Pedir un Uber",
    "en": "Pedir un Uber"
  },
  {
    "id": "term-129",
    "de": "Pagar con billete grande",
    "en": "Pagar con billete grande"
  },
  {
    "id": "term-130",
    "de": "Hacer fila en el banco",
    "en": "Hacer fila en el banco"
  },
  {
    "id": "term-131",
    "de": "Comprar en el tianguis",
    "en": "Comprar en el tianguis"
  },
  {
    "id": "term-132",
    "de": "Comer elote",
    "en": "Comer elote"
  },
  {
    "id": "term-133",
    "de": "Dar el Grito",
    "en": "Dar el Grito"
  },
  {
    "id": "term-134",
    "de": "Romper una piñata",
    "en": "Romper una piñata"
  },
  {
    "id": "term-135",
    "de": "Jugar lotería",
    "en": "Jugar lotería"
  },
  {
    "id": "term-136",
    "de": "Tomar mezcal",
    "en": "Tomar mezcal"
  },
  {
    "id": "term-137",
    "de": "Servir agua fresca",
    "en": "Servir agua fresca"
  },
  {
    "id": "term-138",
    "de": "Hacer guacamole",
    "en": "Hacer guacamole"
  },
  {
    "id": "term-139",
    "de": "Pedir tacos al pastor",
    "en": "Pedir tacos al pastor"
  },
  {
    "id": "term-140",
    "de": "Pelearse con el vecino",
    "en": "Pelearse con el vecino"
  },
  {
    "id": "term-141",
    "de": "Ver telenovela",
    "en": "Ver telenovela"
  },
  {
    "id": "term-142",
    "de": "Llorar con telenovela",
    "en": "Llorar con telenovela"
  },
  {
    "id": "term-143",
    "de": "Cantar karaoke",
    "en": "Cantar karaoke"
  },
  {
    "id": "term-144",
    "de": "Tomar el metro",
    "en": "Tomar el metro"
  },
  {
    "id": "term-145",
    "de": "Ir al mercado",
    "en": "Ir al mercado"
  },
  {
    "id": "term-146",
    "de": "Regatear en el tianguis",
    "en": "Regatear en el tianguis"
  },
  {
    "id": "term-147",
    "de": "Ponerse bloqueador",
    "en": "Ponerse bloqueador"
  },
  {
    "id": "term-148",
    "de": "Espantar mosquitos",
    "en": "Espantar mosquitos"
  },
  {
    "id": "term-149",
    "de": "Sacar la basura",
    "en": "Sacar la basura"
  },
  {
    "id": "term-150",
    "de": "Lavar el coche",
    "en": "Lavar el coche"
  },
  {
    "id": "term-151",
    "de": "Arreglar la bici",
    "en": "Arreglar la bici"
  },
  {
    "id": "term-152",
    "de": "Cargar el celular",
    "en": "Cargar el celular"
  },
  {
    "id": "term-153",
    "de": "Buscar wifi",
    "en": "Buscar wifi"
  },
  {
    "id": "term-154",
    "de": "Tomar selfie",
    "en": "Tomar selfie"
  },
  {
    "id": "term-155",
    "de": "Hacer videollamada",
    "en": "Hacer videollamada"
  },
  {
    "id": "term-156",
    "de": "Comprar pan dulce",
    "en": "Comprar pan dulce"
  },
  {
    "id": "term-157",
    "de": "Hacer café de olla",
    "en": "Hacer café de olla"
  },
  {
    "id": "term-158",
    "de": "Comer chilaquiles",
    "en": "Comer chilaquiles"
  },
  {
    "id": "term-159",
    "de": "Preparar pozole",
    "en": "Preparar pozole"
  },
  {
    "id": "term-160",
    "de": "Hacer una ofrenda",
    "en": "Hacer una ofrenda"
  },
  {
    "id": "term-161",
    "de": "Ir a una posada",
    "en": "Ir a una posada"
  },
  {
    "id": "term-162",
    "de": "Pedir posada",
    "en": "Pedir posada"
  },
  {
    "id": "term-163",
    "de": "Abrir regalos",
    "en": "Abrir regalos"
  },
  {
    "id": "term-164",
    "de": "Soplar velitas",
    "en": "Soplar velitas"
  },
  {
    "id": "term-165",
    "de": "Jugar fútbol callejero",
    "en": "Jugar fútbol callejero"
  },
  {
    "id": "term-166",
    "de": "Ser portero",
    "en": "Ser portero"
  },
  {
    "id": "term-167",
    "de": "Hacer un gol",
    "en": "Hacer un gol"
  },
  {
    "id": "term-168",
    "de": "Echar porras",
    "en": "Echar porras"
  },
  {
    "id": "term-169",
    "de": "Hacer maroma",
    "en": "Hacer maroma"
  },
  {
    "id": "term-170",
    "de": "Dar concierto de mariachi",
    "en": "Dar concierto de mariachi"
  },
  {
    "id": "term-171",
    "de": "Afinar guitarra",
    "en": "Afinar guitarra"
  },
  {
    "id": "term-172",
    "de": "Tocar la trompeta",
    "en": "Tocar la trompeta"
  },
  {
    "id": "term-173",
    "de": "Hacer un antojo",
    "en": "Hacer un antojo"
  },
  {
    "id": "term-174",
    "de": "Comprar una caguama",
    "en": "Comprar una caguama"
  }
] as const;

// Adult-only, original prompts. These never enter the default U18 pool.
const adultTerms = [
  {
    "id": "adult-1",
    "de": "Resaca matutina",
    "en": "Resaca matutina"
  },
  {
    "id": "adult-2",
    "de": "Escabullirse a casa en secreto",
    "en": "Escabullirse a casa en secreto"
  },
  {
    "id": "adult-3",
    "de": "mezclar coctel",
    "en": "mezclar coctel"
  },
  {
    "id": "adult-4",
    "de": "Toca el barril de cerveza",
    "en": "Toca el barril de cerveza"
  },
  {
    "id": "adult-5",
    "de": "beber tequila",
    "en": "beber tequila"
  },
  {
    "id": "adult-6",
    "de": "Descorchar champagne",
    "en": "Descorchar champagne"
  },
  {
    "id": "adult-7",
    "de": "jugar al beer pong",
    "en": "jugar al beer pong"
  },
  {
    "id": "adult-8",
    "de": "baile de mesa",
    "en": "baile de mesa"
  },
  {
    "id": "adult-9",
    "de": "Coqueteo de bar",
    "en": "Coqueteo de bar"
  },
  {
    "id": "adult-10",
    "de": "Convocar a los gorilas",
    "en": "Convocar a los gorilas"
  },
  {
    "id": "adult-11",
    "de": "tacones altos",
    "en": "tacones altos"
  },
  {
    "id": "adult-12",
    "de": "bajo de club",
    "en": "bajo de club"
  },
  {
    "id": "adult-13",
    "de": "karaoke borracho",
    "en": "karaoke borracho"
  },
  {
    "id": "adult-14",
    "de": "coger un taxi",
    "en": "coger un taxi"
  },
  {
    "id": "adult-15",
    "de": "proyecto de ley del partido",
    "en": "proyecto de ley del partido"
  },
  {
    "id": "adult-16",
    "de": "Ordenar tomas",
    "en": "Ordenar tomas"
  },
  {
    "id": "adult-17",
    "de": "cita incómoda",
    "en": "cita incómoda"
  },
  {
    "id": "adult-18",
    "de": "beso secreto",
    "en": "beso secreto"
  },
  {
    "id": "adult-19",
    "de": "chupetón",
    "en": "chupetón"
  },
  {
    "id": "adult-20",
    "de": "propuesta de matrimonio",
    "en": "propuesta de matrimonio"
  },
  {
    "id": "adult-21",
    "de": "Ocultar carta de amor",
    "en": "Ocultar carta de amor"
  },
  {
    "id": "adult-22",
    "de": "atrapado haciendo trampa",
    "en": "atrapado haciendo trampa"
  },
  {
    "id": "adult-23",
    "de": "conocer ex",
    "en": "conocer ex"
  },
  {
    "id": "adult-24",
    "de": "Línea de recogida fallida",
    "en": "Línea de recogida fallida"
  },
  {
    "id": "adult-25",
    "de": "aplicación de citas",
    "en": "aplicación de citas"
  },
  {
    "id": "adult-26",
    "de": "cita a ciegas",
    "en": "cita a ciegas"
  },
  {
    "id": "adult-27",
    "de": "striptease",
    "en": "striptease"
  },
  {
    "id": "adult-28",
    "de": "baile en barra",
    "en": "baile en barra"
  },
  {
    "id": "adult-29",
    "de": "esposas",
    "en": "esposas"
  },
  {
    "id": "adult-30",
    "de": "sujetador abierto",
    "en": "sujetador abierto"
  },
  {
    "id": "adult-31",
    "de": "Ponte un condón",
    "en": "Ponte un condón"
  },
  {
    "id": "adult-32",
    "de": "máquina expendedora de condones",
    "en": "máquina expendedora de condones"
  },
  {
    "id": "adult-33",
    "de": "cama chirriante",
    "en": "cama chirriante"
  },
  {
    "id": "adult-34",
    "de": "sexo en el coche",
    "en": "sexo en el coche"
  },
  {
    "id": "adult-35",
    "de": "sexo en la ducha",
    "en": "sexo en la ducha"
  },
  {
    "id": "adult-36",
    "de": "Vibrador vacio",
    "en": "Vibrador vacio"
  },
  {
    "id": "adult-37",
    "de": "Inflar muñeca sexual",
    "en": "Inflar muñeca sexual"
  },
  {
    "id": "adult-38",
    "de": "porno ruidoso",
    "en": "porno ruidoso"
  },
  {
    "id": "adult-39",
    "de": "Masaje erótico",
    "en": "Masaje erótico"
  },
  {
    "id": "adult-40",
    "de": "Una aventura de noche",
    "en": "Una aventura de noche"
  },
  {
    "id": "adult-41",
    "de": "Tomar el sol desnudo",
    "en": "Tomar el sol desnudo"
  },
  {
    "id": "adult-42",
    "de": "calzoncillos olvidados",
    "en": "calzoncillos olvidados"
  },
  {
    "id": "adult-43",
    "de": "Bloqueado desnudo",
    "en": "Bloqueado desnudo"
  },
  {
    "id": "adult-44",
    "de": "sonidos sexuales",
    "en": "sonidos sexuales"
  },
  {
    "id": "adult-45",
    "de": "liar un porro",
    "en": "liar un porro"
  },
  {
    "id": "adult-46",
    "de": "usar bong",
    "en": "usar bong"
  },
  {
    "id": "adult-47",
    "de": "Antojos de fumeta",
    "en": "Antojos de fumeta"
  },
  {
    "id": "adult-48",
    "de": "Activar detectores de humo",
    "en": "Activar detectores de humo"
  },
  {
    "id": "adult-49",
    "de": "encender un cigarrillo",
    "en": "encender un cigarrillo"
  },
  {
    "id": "adult-50",
    "de": "Quédate con parches de nicotina",
    "en": "Quédate con parches de nicotina"
  },
  {
    "id": "adult-51",
    "de": "preparar shisha",
    "en": "preparar shisha"
  },
  {
    "id": "adult-52",
    "de": "fumando un cigarro",
    "en": "fumando un cigarro"
  },
  {
    "id": "adult-53",
    "de": "Inodoro obstruido",
    "en": "Inodoro obstruido"
  },
  {
    "id": "adult-54",
    "de": "Falta papel higiénico",
    "en": "Falta papel higiénico"
  },
  {
    "id": "adult-55",
    "de": "Ocultar pedo",
    "en": "Ocultar pedo"
  },
  {
    "id": "adult-56",
    "de": "orinar salvaje",
    "en": "orinar salvaje"
  },
  {
    "id": "adult-57",
    "de": "Vejiga llena",
    "en": "Vejiga llena"
  },
  {
    "id": "adult-58",
    "de": "afeitado íntimo",
    "en": "afeitado íntimo"
  },
  {
    "id": "adult-59",
    "de": "depilacion intima",
    "en": "depilacion intima"
  },
  {
    "id": "adult-60",
    "de": "prueba de embarazo",
    "en": "prueba de embarazo"
  },
  {
    "id": "adult-61",
    "de": "Visita al urólogo",
    "en": "Visita al urólogo"
  },
  {
    "id": "adult-62",
    "de": "examen de próstata",
    "en": "examen de próstata"
  },
  {
    "id": "adult-63",
    "de": "Tanga incómoda",
    "en": "Tanga incómoda"
  },
  {
    "id": "adult-64",
    "de": "Ponerse un tampón",
    "en": "Ponerse un tampón"
  },
  {
    "id": "adult-65",
    "de": "Hacer la declaración de impuestos",
    "en": "Hacer la declaración de impuestos"
  },
  {
    "id": "adult-66",
    "de": "Mentirle al jefe",
    "en": "Mentirle al jefe"
  },
  {
    "id": "adult-67",
    "de": "Dormir en la oficina",
    "en": "Dormir en la oficina"
  },
  {
    "id": "adult-68",
    "de": "Resaca festiva",
    "en": "Resaca festiva"
  },
  {
    "id": "adult-69",
    "de": "Pedir aumento de sueldo",
    "en": "Pedir aumento de sueldo"
  },
  {
    "id": "adult-70",
    "de": "Descubrir el sobregiro",
    "en": "Descubrir el sobregiro"
  },
  {
    "id": "adult-71",
    "de": "Multa de tráfico",
    "en": "Multa de tráfico"
  },
  {
    "id": "adult-72",
    "de": "Quítate el anillo de bodas",
    "en": "Quítate el anillo de bodas"
  },
  {
    "id": "adult-73",
    "de": "Visitar a los suegros",
    "en": "Visitar a los suegros"
  },
  {
    "id": "adult-74",
    "de": "Crisis de la mediana edad",
    "en": "Crisis de la mediana edad"
  },
  {
    "id": "adult-75",
    "de": "Tatuaje de arrepentimiento",
    "en": "Tatuaje de arrepentimiento"
  },
  {
    "id": "adult-76",
    "de": "Piercing íntimo",
    "en": "Piercing íntimo"
  }
] as const;

export const terms = [
  ...standardTerms.map((term) => ({ ...term, category: "standard" as const })),
  ...adultTerms.map((term) => ({ ...term, category: "adult" as const }))
] as const;
