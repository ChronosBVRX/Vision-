#!/usr/bin/env python3
"""Genera el mapa de canales deportivos de la parrilla XTV de Vision+.

Entrada: TSV `title<TAB>play_ref<TAB>position` (una fila por canal de tv-en-vivo).
Salida: `sports_channels.json` con cada señal clasificada por marca, feed, país y
dueño, más el catálogo de emisoras que usa ESPN (canales, OTT y aliases).

No toca GeeSports: es una capa propia para cruzar la agenda ESPN con la parrilla.
"""
from __future__ import annotations

import argparse
import json
import re
import unicodedata
from collections import Counter, OrderedDict

# ------------------------------------------------------------------ utilidades


def fold(text: str) -> str:
    text = unicodedata.normalize("NFD", (text or "").lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


QUALITY = re.compile(r"\b(fhd|uhd|4k|hd|sd|h265|hevc|h264|1080p|720p|540p|480p)\b")


def clean_name(name: str) -> str:
    return re.sub(r"\s+", " ", QUALITY.sub(" ", name or "")).strip()


# ------------------------------------------------------------------ deportes

SPORTS_WORDS = (
    "sport", "deporte", "futbol", "futv", "beisbol", "liga pro", "liga 1",
    "liga mx", "liga boliviana", "liga de campeones", "nba", "nfl", "mlb",
    "mls", "nhl", "ufc", "wwe", "box nation", "tennis", "golf", "racing",
    "formula", "dazn", "tudn", "tdn", "gol peru", "gol tv", "canal del futbol",
    "el canal del futbol", "tvc deportes", "deportes tvc", "aym sports",
    "px sports", "cdo", "fight", "combate", "toros tv", "mundo toro",
    "hipodromo", "tvg network", "tvg 2", "ovacion", "estadio tnt",
    "claro sports", "movistar deportes", "fox deporte", "fox sport",
    "tnt sport", "win sport", "bally", "nesn", "yes network", "sportsnet",
    "sportsmax", "directv sport", "espn", "fox racing", "fox soccer",
    "red zone", "fight pass", "premier league", "champions", "eurosport",
    "nba tv", "mlb network", "nfl network", "nhl network", "golf channel",
    "tennis channel", "bein", "sky sports", "motogp", "nascar", "formula 1",
    "trace sports", "sportv", "sport tv", "sportdigital", "magentasport",
    "elevensports", "eleven sports", "cbs sports", "nbc sports",
    "fanatiz", "poker", "billar", "ajedrez", "fox one", "copa", "eurocopa",
)

# Emisoras generalistas que también transmiten deporte y aparecen en la agenda ESPN.
GENERAL_SPORTS = (
    "canal 5", "azteca 7", "las estrellas", "canal 2", "canal 4", "canal 6",
    "canal 9", "canal 13", "univision", "unimas", "telemundo", "universo",
    "galavision", "usa network",
    "rcn", "caracol", "telefe", "tvn", "globo", "band", "record", "sbt",
    "red uno", "unitel", "atb", "ecuavisa", "rcn nuestra tele",
)

SHORT_TOKENS = {"gol", "f1", "tv", "td", "cd"}


def is_sports(name: str) -> bool:
    n = fold(name)
    if not n:
        return False
    padded = f" {n} "
    for word in SPORTS_WORDS:
        if word in SHORT_TOKENS:
            if re.search(rf"\b{re.escape(word)}\b", n):
                return True
            continue
        if word in n:
            return True
    return False


def is_general_sports(name: str) -> bool:
    n = fold(name)
    return any(re.search(rf"\b{re.escape(w)}\b", n) for w in GENERAL_SPORTS)


# ------------------------------------------------------------------ país

COUNTRY_TOKENS = OrderedDict([
    ("mexico", "México"), ("mex", "México"), ("mx", "México"),
    ("usa", "EE. UU."), ("us", "EE. UU."), ("eua", "EE. UU."), ("eeuu", "EE. UU."),
    ("east", "EE. UU."), ("west", "EE. UU."), ("miami", "EE. UU."),
    ("york", "EE. UU."), ("chicago", "EE. UU."), ("dallas", "EE. UU."),
    ("boston", "EE. UU."), ("arizona", "EE. UU."), ("ohio", "EE. UU."),
    ("kansas", "EE. UU."), ("wisconsin", "EE. UU."), ("detroit", "EE. UU."),
    ("california", "EE. UU."), ("washington", "EE. UU."), ("philly", "EE. UU."),
    ("diego", "EE. UU."), ("carolina", "EE. UU."), ("colorado", "EE. UU."),
    ("utah", "EE. UU."), ("phoenix", "EE. UU."), ("atlanta", "EE. UU."),
    ("espana", "España"), ("esp", "España"),
    ("argentina", "Argentina"), ("arg", "Argentina"), ("ar", "Argentina"),
    ("colombia", "Colombia"), ("col", "Colombia"), ("co", "Colombia"),
    ("peru", "Perú"), ("pe", "Perú"), ("chile", "Chile"), ("cl", "Chile"),
    ("ecuador", "Ecuador"), ("ec", "Ecuador"), ("bolivia", "Bolivia"),
    ("bo", "Bolivia"), ("paraguay", "Paraguay"), ("py", "Paraguay"),
    ("uruguay", "Uruguay"), ("uy", "Uruguay"), ("venezuela", "Venezuela"),
    ("cr", "Costa Rica"), ("panama", "Panamá"), ("honduras", "Honduras"),
    ("hn", "Honduras"), ("salvador", "El Salvador"), ("nicaragua", "Nicaragua"),
    ("guatemala", "Guatemala"), ("guate", "Guatemala"),
    ("dominicana", "R. Dominicana"), ("rd", "R. Dominicana"),
    ("pr", "Puerto Rico"), ("brasil", "Brasil"), ("bra", "Brasil"),
    ("canada", "Canadá"), ("ca", "Canadá"),
    ("nic", "Nicaragua"), ("gua", "Guatemala"), ("sv", "El Salvador"),
    ("reino unido", "Reino Unido"), ("uk", "Reino Unido"),
    ("portugal", "Portugal"), ("alemania", "Alemania"), ("germany", "Alemania"),
])

COUNTRY_BRANDS = (
    ("fox sports central", "México"), ("fox sports 2 central", "México"),
    ("fox sports 3 central", "México"), ("fox sports premium", "México"),
    ("fox sports", "LatAm"), ("tnt sports", "LatAm"), ("dazn", "LatAm"),
    ("elite sports hub", "LatAm"), ("fox one", "México"),
    ("vix deportes", "México"), ("vix", "México"),
    ("espn deportes", "EE. UU."), ("espn", "LatAm"),
    ("nbc golf", "EE. UU."), ("golf usa", "EE. UU."), ("movistar golf", "España"),
    ("sports max", "R. Dominicana"),
    ("fox deportes", "EE. UU."), ("fox deporte", "EE. UU."),
    ("fox soccer", "EE. UU."), ("fox 40", "EE. UU."), ("fox 5", "EE. UU."),
    ("tudn usa", "EE. UU."), ("tudn", "México"), ("tdn", "México"),
    ("claro sports", "México"), ("sky sports", "México"),
    ("win sport", "Colombia"), ("tyc", "Argentina"), ("gol peru", "Perú"),
    ("movistar deportes", "Perú"), ("tvc deportes", "México"),
    ("deportes tvc", "México"), ("aym sports", "México"),
    ("azteca deportes", "México"), ("px sports", "Colombia"),
    ("sportsmax", "R. Dominicana"), ("sportsnet", "Canadá"),
    ("bally", "EE. UU."), ("nbc sports", "EE. UU."), ("nesn", "EE. UU."),
    ("yes network", "EE. UU."), ("altitude", "EE. UU."),
    ("spectrum", "EE. UU."), ("fight network", "Canadá"),
    ("box nation", "Reino Unido"), ("eurosport", "Internacional"),
    ("el canal del futbol", "Ecuador"), ("futbol e opc", "Ecuador"),
    ("liga 1 pe", "Perú"), ("liga 1 max peru", "Perú"),
    ("liga pro ec", "Ecuador"), ("liga boliviana", "Bolivia"),
    ("directv sport", "LatAm"), ("tnt sport", "LatAm"),
    ("espn deportes", "EE. UU."), ("univision", "EE. UU."),
    ("unimas", "EE. UU."), ("telemundo", "EE. UU."), ("universo", "EE. UU."),
    ("galavision", "EE. UU."), ("usa network", "EE. UU."),
    ("cbs sports", "EE. UU."), ("nfl network", "EE. UU."),
    ("mlb network", "EE. UU."), ("nba tv", "EE. UU."), ("nhl network", "EE. UU."),
    ("golf", "EE. UU."), ("ufc", "EE. UU."), ("wwe", "EE. UU."),
    ("fox racing", "EE. UU."), ("trace sports", "Internacional"),
    ("canal 5", "México"), ("azteca 7", "México"), ("las estrellas", "México"),
    ("rcn", "Colombia"), ("caracol", "Colombia"), ("telefe", "Argentina"),
    ("tvn", "Chile"), ("globo", "Brasil"), ("ecuavisa", "Ecuador"),
    ("unitel", "Bolivia"), ("red uno", "Bolivia"), ("atb", "Bolivia"),
)


def country_for(name: str) -> str:
    n = fold(name)
    padded = f" {n} "
    for token, country in COUNTRY_TOKENS.items():
        if f" {token} " in padded:
            return country
    for brand, country in COUNTRY_BRANDS:
        if brand in n:
            return country
    return "Internacional"


# ------------------------------------------------------------------ marca/feed

BRAND_RULES = (
    (r"elite\s+sports\s+hub\s*4", "ESPN 4", "Disney", "4"),
    (r"elite\s+sports\s+hub\s*3", "ESPN 3", "Disney", "3"),
    (r"elite\s+sports\s+hub\s*2", "ESPN 2", "Disney", "2"),
    (r"elite\s+sports\s+hub", "ESPN", "Disney", "1"),
    (r"espn\s+knockout", "ESPN KnockOut", "Disney", "KnockOut"),
    (r"espn\s+extra", "ESPN Extra", "Disney", "Extra"),
    (r"espn\s+premium", "ESPN Premium", "Disney", "Premium"),
    (r"espn\s+deportes", "ESPN Deportes", "Disney", "US"),
    (r"espn\s*([1-9])", "ESPN", "Disney", r"\1"),
    (r"espn", "ESPN", "Disney", ""),
    (r"fox\s+sports\s+premium", "Fox Sports", "Fox Sports (Lauman)", "Premium"),
    (r"fox\s+sports\s+([1-3])\s+central", "Fox Sports", "Fox Sports (Lauman)", r"\1"),
    (r"fox\s+sports\s+central", "Fox Sports", "Fox Sports (Lauman)", "1"),
    (r"fox\s+sports\s+([1-3])\s+mx", "Fox Sports", "Fox Sports (Lauman)", r"\1"),
    (r"fox\s+sports\s+([1-3])\s+usa", "Fox Sports", "Fox Sports (Disney)", r"\1"),
    (r"fox\s+deportes?", "Fox Deportes", "Fox (Disney)", ""),
    (r"fox\s+soccer", "Fox Soccer Plus", "Fox (Disney)", "Plus"),
    (r"fox\s+one", "Fox One", "Fox Corporation", ""),
    (r"fox\s+esp", "Fox", "Fox (España)", ""),
    (r"fox\s+racing", "Fox Racing", "Fox (EE. UU.)", ""),
    (r"fox\s+sports\s+ar", "Fox Sports", "Fox Sports (Disney)", ""),
    (r"fox\s+sports\s+chile", "Fox Sports", "Fox Sports (Disney)", ""),
    (r"fox\s+sports\s+col", "Fox Sports", "Fox Sports (Disney)", ""),
    (r"fox\s+sports\s+peru", "Fox Sports", "Fox Sports (Disney)", ""),
    (r"fox\s+sports", "Fox Sports", "Fox Sports (Disney)", ""),
    (r"tudn\s+usa", "TUDN USA", "TUDN (Televisa/Univision)", "USA"),
    (r"tudn", "TUDN", "TUDN (Televisa/Univision)", "MX"),
    (r"tdn", "TDN", "Televisa", ""),
    (r"claro\s+sports\s*([12])", "Claro Sports", "Claro", r"\1"),
    (r"claro\s+sports", "Claro Sports", "Claro", "1"),
    (r"tnt\s+sports\s+premium", "TNT Sports", "Warner Bros. Discovery", "Premium"),
    (r"tnt\s+sports\s+chile\s*2", "TNT Sports", "Warner Bros. Discovery", "2"),
    (r"tnt\s+sports\s+chile", "TNT Sports", "Warner Bros. Discovery", "CL"),
    (r"tnt\s+sports\s+argentina", "TNT Sports", "Warner Bros. Discovery", "AR"),
    (r"tnt\s+sports\s+estadio", "TNT Sports", "Warner Bros. Discovery", "Estadio"),
    (r"tnt\s+sports", "TNT Sports", "Warner Bros. Discovery", ""),
    (r"dazn\s+f1", "DAZN", "DAZN", "F1"),
    (r"dazn\s+eventos", "DAZN", "DAZN", "Eventos"),
    (r"dazn\s*([1-4])", "DAZN", "DAZN", r"\1"),
    (r"dazn", "DAZN", "DAZN", ""),
    (r"bein\s+sports\s+en\s+espanol", "beIN Sports", "beIN", "US"),
    (r"bein\s+sports\s*([1-3])", "beIN Sports", "beIN", r"\1"),
    (r"bein\s+sports", "beIN Sports", "beIN", ""),
    (r"win\s+sports\s*(?:plus|\+)", "Win Sports+", "Win Sports", "Plus"),
    (r"win\s+sports\s*([1-4])", "Win Sports", "Win Sports", r"\1"),
    (r"win\s+sports", "Win Sports", "Win Sports", ""),
    (r"tyc\s+sports", "TyC Sports", "TyC", ""),
    (r"directv\s+sports?\s*(2|plus|\+)?", "DIRECTV Sports", "DIRECTV", ""),
    (r"sky\s+sports\s*([0-9]+)", "Sky Sports", "Sky", r"\1"),
    (r"sky\s+sports", "Sky Sports", "Sky", ""),
    (r"movistar\s+deportes", "Movistar Deportes", "Movistar", ""),
    (r"gol\s+peru", "Gol Perú", "Gol Perú", ""),
    (r"tvc\s+deportes\s*2", "TVC Deportes", "TV Azteca", "2"),
    (r"tvc\s+deportes", "TVC Deportes", "TV Azteca", ""),
    (r"deportes\s+tvc", "TVC Deportes", "TV Azteca", ""),
    (r"aym\s+sports", "AYM Sports", "AYM", ""),
    (r"px\s+sports", "PX Sports", "PX", ""),
    (r"cdo", "CDO", "CDO", ""),
    (r"sports\s*max", "Sportsmax", "Sportsmax", ""),
    (r"sportsnet", "Sportsnet", "Sportsnet", ""),
    (r"bally\s+sports", "Bally Sports", "Bally", ""),
    (r"nbc\s+sports", "NBC Sports", "NBC", ""),
    (r"nesn", "NESN", "NESN", ""),
    (r"yes\s+network", "YES Network", "YES", ""),
    (r"altitude", "Altitude Sports", "Altitude", ""),
    (r"spectrum", "Spectrum SportsNet", "Spectrum", ""),
    (r"nfl\s+network", "NFL Network", "NFL", ""),
    (r"mlb\s+network", "MLB Network", "MLB", ""),
    (r"nba\s+tv", "NBA TV", "NBA", ""),
    (r"nhl\s+network", "NHL Network", "NHL", ""),
    (r"nba\s+pass\s*0*([0-9]+)", "NBA Pass", "NBA", r"\1"),
    (r"nfl\s+pass\s*0*([0-9]+)", "NFL Pass", "NFL", r"\1"),
    (r"mlb\s+pass\s*0*([0-9]+)", "MLB Pass", "MLB", r"\1"),
    (r"nfl\s+red\s*zone", "NFL RedZone", "NFL", "RedZone"),
    (r"golf", "Golf", "Golf", ""),
    (r"ufc", "UFC", "UFC", ""),
    (r"wwe", "WWE", "WWE", ""),
    (r"box\s+nation", "Box Nation", "Box Nation", ""),
    (r"fight\s+network", "Fight Network", "Fight Network", ""),
    (r"eurosport", "Eurosport", "Eurosport", ""),
    (r"trace\s+sports", "Trace Sports", "Trace", ""),
    (r"futv", "FUTV", "FUTV", ""),
    (r"liga\s+1\s+max\s+peru", "Liga 1 Max", "Liga 1", ""),
    (r"liga\s+1\s+pe", "Liga 1", "Liga 1", ""),
    (r"liga\s+pro\s+ec", "Liga Pro", "Liga Pro", ""),
    (r"liga\s+boliviana", "Liga Boliviana", "Liga", ""),
    (r"liga\s+de\s+campeones", "Liga de Campeones", "UEFA", ""),
    (r"el\s+canal\s+del\s+futbol", "El Canal del Fútbol", "El Canal del Fútbol", ""),
    (r"futbol\s+e\s+opc", "Fútbol E OPC", "Fútbol E OPC", ""),
    (r"canal\s+5", "Canal 5", "Televisa", ""),
    (r"azteca\s+7", "Azteca 7", "TV Azteca", ""),
    (r"las\s+estrellas", "Las Estrellas", "Televisa", ""),
    (r"univision", "Univision", "Univision", ""),
    (r"unimas", "UniMás", "Univision", ""),
    (r"telemundo", "Telemundo", "Telemundo", ""),
    (r"universo", "Universo", "Telemundo", ""),
    (r"vix\s+deportes", "ViX Deportes", "Televisa/Univision", ""),
    (r"usa\s+network", "USA Network", "NBCUniversal", ""),
    (r"cbs\s+sports", "CBS Sports", "Paramount", ""),
)


def brand_for(name: str) -> tuple[str, str, str]:
    n = fold(name)
    for pattern, brand, owner, feed in BRAND_RULES:
        match = re.search(pattern, n)
        if match:
            value = feed
            if isinstance(feed, str) and "\\" in feed:
                value = match.expand(feed)
            return brand, owner, value.strip()
    return fold(name).split()[0].upper() if n else "?", "", ""


# ------------------------------------------------------------------ emisoras ESPN

BROADCASTERS = {
    # marca: (canonical, owner, kind, país por defecto, aliases)
    "espn": ("ESPN", "Disney", "channel", "LatAm", ["espn", "espn 1"]),
    "espn 2": ("ESPN 2", "Disney", "channel", "LatAm", ["espn 2", "espn2"]),
    "espn 3": ("ESPN 3", "Disney", "channel", "LatAm", ["espn 3", "espn3"]),
    "espn 4": ("ESPN 4", "Disney", "channel", "LatAm", ["espn 4", "espn4"]),
    "espn 5": ("ESPN 5", "Disney", "channel", "LatAm", ["espn 5", "espn5"]),
    "espn 6": ("ESPN 6", "Disney", "channel", "LatAm", ["espn 6", "espn6"]),
    "espn 7": ("ESPN 7", "Disney", "channel", "LatAm", ["espn 7", "espn7"]),
    "espn deportes": ("ESPN Deportes", "Disney", "channel", "EE. UU.", ["espn deportes"]),
    "espn extra": ("ESPN Extra", "Disney", "channel", "LatAm", ["espn extra"]),
    "espn premium": ("ESPN Premium", "Disney", "channel", "LatAm", ["espn premium"]),
    "espn knockout": ("ESPN KnockOut", "Disney", "channel", "LatAm", ["espn knockout"]),
    "fox sports": ("Fox Sports", "Fox", "channel", "LatAm", ["fox sports", "fox sports 1"]),
    "fox sports 1": ("Fox Sports", "Fox", "channel", "LatAm", ["fox sports 1"]),
    "fox sports 2": ("Fox Sports 2", "Fox", "channel", "LatAm", ["fox sports 2"]),
    "fox sports 3": ("Fox Sports 3", "Fox", "channel", "LatAm", ["fox sports 3"]),
    "fox sports premium": ("Fox Sports", "Fox", "channel", "México", ["fox sports premium"]),
    "fox deportes": ("Fox Deportes", "Fox", "channel", "EE. UU.", ["fox deportes", "fox deporte"]),
    "fox soccer plus": ("Fox Soccer Plus", "Fox", "channel", "EE. UU.", ["fox soccer plus"]),
    "fox basic": ("FOX", "Fox Corporation", "linear", "México", ["fox", "fox basic"]),
    "fox plus": ("FOX+", "Fox Corporation", "linear", "México", ["fox plus", "fox+"]),
    "fox tubi": ("FOX | Tubi", "Fox Corporation", "linear", "México", ["fox tubi", "tubi"]),
    "fox one": ("FOX One", "Fox Corporation", "channel", "México", ["fox one"]),
    "tudn": ("TUDN", "Televisa/Univision", "channel", "México", ["tudn"]),
    "tudn usa": ("TUDN USA", "Televisa/Univision", "channel", "EE. UU.", ["tudn usa"]),
    "claro sports": ("Claro Sports", "Claro", "channel", "México", ["claro sports"]),
    "tnt sports": ("TNT Sports", "WBD", "channel", "LatAm", ["tnt sports"]),
    "dazn": ("DAZN", "DAZN", "channel", "LatAm", ["dazn"]),
    "bein sports": ("beIN Sports", "beIN", "channel", "Internacional", ["bein sports"]),
    "win sports": ("Win Sports", "Win", "channel", "Colombia", ["win sports"]),
    "tyc sports": ("TyC Sports", "TyC", "channel", "Argentina", ["tyc sports"]),
    "directv sports": ("DIRECTV Sports", "DIRECTV", "channel", "LatAm", ["directv sports"]),
    "sky sports": ("Sky Sports", "Sky", "channel", "México", ["sky sports"]),
    "movistar deportes": ("Movistar Deportes", "Movistar", "channel", "Perú", ["movistar deportes"]),
    "gol peru": ("Gol Perú", "Gol Perú", "channel", "Perú", ["gol peru"]),
    "tvc deportes": ("TVC Deportes", "TV Azteca", "channel", "México", ["tvc deportes"]),
    "aym sports": ("AYM Sports", "AYM", "channel", "México", ["aym sports"]),
    "tdn": ("TDN", "Televisa", "channel", "México", ["tdn"]),
    "univision": ("Univision", "Univision", "channel", "EE. UU.", ["univision"]),
    "unimas": ("UniMás", "Univision", "channel", "EE. UU.", ["unimas"]),
    "telemundo": ("Telemundo", "Telemundo", "channel", "EE. UU.", ["telemundo"]),
    "universo": ("Universo", "Telemundo", "channel", "EE. UU.", ["universo"]),
    "canal 5": ("Canal 5", "Televisa", "channel", "México", ["canal 5"]),
    "azteca 7": ("Azteca 7", "TV Azteca", "channel", "México", ["azteca 7"]),
    "usa network": ("USA Network", "NBCUniversal", "channel", "EE. UU.", ["usa network", "usa net"]),
    "cbs sports network": ("CBS Sports Network", "Paramount", "channel", "EE. UU.", ["cbs sports network"]),
    "nbcsn": ("NBC Sports", "NBC", "channel", "EE. UU.", ["nbcsn"]),
    "space": ("Space", "Warner Bros. Discovery", "channel", "México", ["space"]),
    "nbc sports": ("NBC Sports", "NBC", "channel", "EE. UU.", ["nbc sports"]),
    "fox deportes": ("Fox Deportes", "Fox", "channel", "EE. UU.", ["fox deportes"]),
    # OTT / streaming: no son canales de la parrilla, pero se listan para no inventar.
    "paramount plus": ("Paramount+", "Paramount", "ott", "", ["paramount", "paramount plus"]),
    "peacock": ("Peacock", "NBCUniversal", "ott", "EE. UU.", ["peacock"]),
    "max": ("Max", "Warner Bros. Discovery", "ott", "LatAm", ["max", "hbo max"]),
    "disney plus": ("Disney+", "Disney", "ott", "LatAm", ["disney", "disney plus", "disney plus premium"]),
    "vix deportes": ("ViX Deportes", "Televisa/Univision", "channel", "México", ["vix deportes"]),
    "vix": ("ViX", "Televisa/Univision", "ott", "LatAm", ["vix"]),
    "prime video": ("Prime Video", "Amazon", "ott", "LatAm", ["prime video"]),
    "fubotv": ("fuboTV", "fuboTV", "ott", "EE. UU.", ["fubotv"]),
    "fanatiz": ("Fanatiz", "Fanatiz", "ott", "Internacional", ["fanatiz"]),
    "espn plus": ("ESPN+", "Disney", "ott", "EE. UU.", ["espn plus", "espn+"]),
    "sky plus": ("Sky+", "Sky", "ott", "México", ["sky plus", "sky+"]),
    "izzi": ("izzi", "Izzi", "ott", "México", ["izzi"]),
    "tubi": ("Tubi", "Fox", "ott", "EE. UU.", ["tubi"]),
    "fandango": ("Fandango", "Fandango", "ott", "EE. UU.", ["fandango"]),
    "magenta sport": ("MagentaSport", "Telekom", "ott", "Alemania", ["magentasport", "magenta sport"]),
    "sport tv": ("Sport TV", "Sport TV", "ott", "Portugal", ["sport tv"]),
    "btv": ("BTV", "BTV", "ott", "Bulgaria", ["btv online"]),
    "zap": ("ZAP", "ZAP", "ott", "Angola", ["zap"]),
    "sportdigital": ("Sportdigital", "Sportdigital", "ott", "Alemania", ["sportdigital"]),
}


def broadcast_norm(name: str) -> str:
    """Normaliza el nombre de ESPN; '+' se convierte en 'plus' (Paramount+ → paramount plus)."""
    return fold((name or "").replace("+", " plus "))


def broadcaster_for(name: str):
    n = broadcast_norm(name)
    if not n:
        return None
    entries = []
    for key, value in BROADCASTERS.items():
        canonical, owner, kind, country, aliases = value
        for candidate in [key, *aliases]:
            entries.append((broadcast_norm(candidate), canonical, owner, kind, country))
    # 1) coincidencia exacta; 2) el alias MÁS LARGO que sea prefijo (ESPN 2 gana a ESPN)
    for candidate, canonical, owner, kind, country in entries:
        if n == candidate:
            return canonical, owner, kind, country
    best = None
    for candidate, canonical, owner, kind, country in entries:
        if candidate and (n.startswith(candidate + " ") or n.endswith(" " + candidate)):
            if best is None or len(candidate) > best[0]:
                best = (len(candidate), canonical, owner, kind, country)
    if best:
        return best[1], best[2], best[3], best[4]
    return None


# ------------------------------------------------------------------ main


def build(tsv: str):
    sports, general = [], []
    with open(tsv, encoding="utf-8") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            title, ref, pos = parts[0].strip(), parts[1].strip(), parts[2].strip()
            if not title:
                continue
            code = ref.removeprefix("xtvlive:")
            # El portal llama «TNT SPORTS HD» a TNT (entretenimiento), no a TNT
            # Sports. Se queda fuera del mapa deportivo para no ofrecerlo en un
            # partido. TNT Sports real = HD+/FHD/Premium/Estadio (Chile).
            if code == "cyx_TNTSports3":
                continue
            # «Elite Sports Hub» es una señal de Disney+ (pantalla/placeholder),
            # no un ESPN en vivo. No sirve como fuente del partido.
            if code in {
                "ST☆R+HD_cyx", "ST☆R+ 2 HD_cyx", "ST☆R+ 3 HD_cyx", "ST☆R+ 4 HD_cyx",
            }:
                continue
            if is_sports(title):
                sports.append((title, code, int(pos) if pos.isdigit() else 0))
            elif is_general_sports(title):
                general.append((title, code, int(pos) if pos.isdigit() else 0))

    channels = []
    for title, code, pos in sports + general:
        brand, owner, feed = brand_for(title)
        if brand == "CANAL":
            m = re.search(r"canal\s+(\d+)", fold(title))
            if m:
                brand = "Canal " + m.group(1)
        entry = {
            "name": title,
            "code": "xtvlive:" + code if code and not code.startswith("xtvlive:") else code,
            "country": country_for(title),
            "brand": brand,
            "feed": feed,
            "owner": owner,
            "quality": "FHD" if "fhd" in title.lower() else "HD" if "hd" in title.lower() else "",
            "general": (title, code, pos) in general,
            "position": pos,
        }
        # Prueba de campo 2026-11-01: el portal etiquetado FOX ONE sí es el feed
        # de eventos de Fox Corporation (Xolos vs Atlas). AYM emitía el estudio
        # «Cuadro Titular» (no el partido); Fox Sports Premium mostraba peleas
        # (Lauman, no FOX+); Sony Central era películas (no ESPN).
        if fold(title) == "fox one":
            entry.update({
                "country": "México",
                "owner": "Fox Corporation",
                "displayName": "FOX One",
                "verified": True,
                "verifiedBy": "prueba de campo 2026-11-01 (Xolos vs Atlas)",
                "rights": ["Fox One", "FOX", "FOX+"],
                "note": "Señal de Fox Corporation; probada con Xolos vs Atlas. Es el feed de eventos, no el canal lineal de estudio.",
            })
        if "aym sports" in fold(title):
            entry.update({
                "note": "Emite bloques de FOX One (Cuadro Titular); no los partidos.",
                "notEventFeed": True,
            })
        # Los feeds TNT Sports que sí son deporte son de Chile (CDF/Chile);
        # el genérico «TNT SPORTS HD» ya se excluyó arriba.
        if code in {
            "cyx_622EEA9699C5DBB97E7DEC", "cyx_7A1A0524509A17E70D29A64",
            "cyx-CDFHD", "cyx-CDFFHD", "cyx_TNTSports☆",
            "cyx_053453F405BE0EE2B020FAE5", "TNTSPORTSESTADIO",
            "cyx_A9F70E1DE8D6AEAA4FF0AE4F", "cyx_D9C311EF4F8A26E5899A2C71",
            "cyx_F4649D6456804C9E853B5EC7", "cyx_137D3CEBB4F8F95624D03D",
        }:
            entry["country"] = "Chile"
        if "fox sports premium" in fold(title):
            entry.update({
                "note": "Fox Sports Premium de Grupo Lauman; muestra peleas; no es FOX+.",
                "notFoxPlus": True,
                "legacy": True,
            })
        elif "fox sports" in fold(title):
            entry.update({
                "note": "Fox Sports de Grupo Lauman; sin derechos del nuevo Fox (Champions/Liga MX/Fox One).",
                "legacy": True,
            })
        # El portal camufla los feeds de ESPN/Star+ como «Elite Sports Hub». El
        # código ST☆R+ y la guía (mx:espn/2/3, co:espn4) los delatan.
        if "elite sports hub" in fold(title):
            n = re.search(r"elite\s+sports\s+hub\s*([2-4])", fold(title))
            number = int(n.group(1)) if n else 1
            display = "ESPN / Star+ HD" if number == 1 else f"ESPN {number} / Star+ {number} HD"
            entry.update({
                "displayName": display,
                "aliases": ["ESPN", "Star+", "Elite Sports Hub"] + (
                    [] if number == 1 else [f"ESPN {number}", f"Star+ {number}"]
                ),
                "camouflaged": True,
                "epgNorm": "mx:espn" if number == 1 else (
                    "mx:espn2" if number == 2 else (
                        "mx:espn3" if number == 3 else "co:espn4"
                    )
                ),
                "note": "El portal XTV lo publica como Elite Sports Hub; la app lo desenmascara como ESPN/Star+.",
            })
        channels.append(entry)
    # dedupe by name+code
    seen, unique = set(), []
    for ch in channels:
        key = (ch["name"].lower(), ch["code"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(ch)
    unique.sort(key=lambda c: (c["country"] != "México", c["brand"], c["name"]))
    counts = Counter(c["country"] for c in unique)
    brands = Counter(c["brand"] for c in unique)
    return {
        "generatedAt": "2026-11-01",
        "source": "XTV tv-en-vivo + ESPN site.api scoreboard",
        "totalXtvChannels": 1035,
        "sportsChannels": len(unique),
        "byCountry": counts.most_common(),
        "byBrand": brands.most_common(80),
        "channels": unique,
        "broadcasters": BROADCASTERS,
        "foxLinear": {
            "FOX": {
                "name": "FOX",
                "owner": "Fox Corporation",
                "country": "México",
                "mirror": "AYM SPORTS MEXICO",
                "mirrorName": "AYM Sports México",
                "note": "FOX basic: NFL, NASCAR, select Liga MX, Cuadro Titular/FOX 365. AYM es el espejo verificado por Cuadro Titular (prueba 2026-11-01).",
            },
            "FOX+": {
                "name": "FOX+",
                "owner": "Fox Corporation",
                "country": "México",
                "mirror": "FOX ONE",
                "mirrorName": "FOX One",
                "note": "FOX+ premium: Champions, Liga MX, Premier, Bundesliga. FOX ONE es el espejo verificado por Xolos vs Atlas (prueba 2026-11-01).",
            },
            "FOX | Tubi": {
                "name": "FOX | Tubi",
                "owner": "Fox Corporation",
                "country": "México",
                "mirror": "",
                "mirrorName": "",
                "note": "Señal gratuita de Tubi (NFL, Premier, Liga MX, NASCAR); sin espejo XTV confirmado.",
            },
            "FOX One": {
                "name": "FOX One",
                "owner": "Fox Corporation",
                "country": "México",
                "mirror": "FOX ONE",
                "mirrorName": "FOX One",
                "note": "Plataforma/eventos de Fox One; el canal FOX ONE de XTV es el espejo verificado.",
            },
        },
    }


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("tsv")
    ap.add_argument("out")
    args = ap.parse_args()
    data = build(args.tsv)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    print("deportivos:", data["sportsChannels"])
    print("paises:", data["byCountry"])
    print("marcas:", data["byBrand"][:25])
