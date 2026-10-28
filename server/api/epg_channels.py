"""Canales de la guía EPG: normalización compartida por el worker y la API.

Dos piezas, una sola verdad:

* ``canonical_channel`` identifica la CADENA/EDICIÓN (``tudn``, ``foxsports2``,
  ``hbo2``…) sin importar el país ni los adornos del nombre.
* ``channel_country`` identifica el PAÍS de la señal (``mx``, ``cl``, ``ar``…).

La guía se guarda como ``<país>:<canónico>``. Así «Canal 5 de Honduras» no recibe
la programación de «Canal 5 de México», «Canal ESPN 2 (Chile)» no se confunde con
«Canal 2 (Las Estrellas)» y «Canal 13 de Chile» no muestra «El Trece» argentino.

El archivo vive duplicado en ``sync/`` y ``api/`` (cada imagen Docker copia solo su
carpeta); ``server/tests/test_epg.py`` exige que las dos copias sean idénticas.
"""

from __future__ import annotations

import re
import unicodedata

# Adornos que no distinguen una cadena de otra. OJO: los números de canal NO se
# descartan (antes «24 Horas» se convertía en «horas» y «Canal 7» en «jujuy»).
NOISE_WORDS = {
    "fhd", "uhd", "hd", "sd", "4k", "vip", "latino", "latam", "latinoamerica",
    "espana", "mexico", "mex", "colombia", "col", "chile", "cl", "argentina", "ar",
    "es", "pe", "rd", "gt", "sv", "ni", "hn", "pa", "uy", "central", "usa", "us",
    "plus", "east", "west", "pc", "tv", "de", "del", "la", "el", "los", "las", "en",
    "canal", "channel", "network", "television", "internacional", "international",
    "digital", "senal", "signal", "feed",
}

# Cadenas que son de un país concreto: si el canal es de otro país, NO se usa la
# guía de México (sería programación ajena). Las marcas internacionales (HBO,
# Warner, ESPN…) sí pueden caer a un feed latino cuando el país no se conoce.
NATIONAL_ONLY = {
    "canal1", "canal2", "canal3", "canal4", "canal5", "canal6", "canal7", "canal8",
    "canal9", "canal10", "canal11", "canal12", "canal13", "canal14", "canal15",
    "canal22", "24horas", "adn40", "azteca1", "azteca7", "aztecadeportes",
    "aztezacorazon", "aztecamundo", "lasestrellas", "televisa", "multimedios",
    "milenio", "excelsior", "foro", "heraldo", "unicable", "distritocomedia",
    "bandamax", "telehit", "tlnovelas", "pelicula", "bitme", "mexiquense",
    "canaln", "tvn", "telefe", "caracol", "rcn",
}

# Orden de respaldo para marcas internacionales cuando el país no se conoce (o es
# México): primero la señal mexicana (la base de usuarios), luego el resto de
# LatAm. Nunca se mezclan filas de dos países: se elige el primer feed con datos.
COUNTRY_FALLBACK = ("mx", "ar", "cl", "co", "pe", "ec")

# --- países -----------------------------------------------------------------

COUNTRY_PHRASES = (
    ("costa rica", "cr"),
    ("el salvador", "sv"),
    ("republica dominicana", "do"),
    ("rep dominicana", "do"),
    ("puerto rico", "pr"),
    ("estados unidos", "us"),
)

COUNTRY_TOKENS = {
    "mx": "mx", "mex": "mx", "mexico": "mx",
    "us": "us", "usa": "us", "eua": "us", "eeuu": "us",
    "ar": "ar", "arg": "ar", "argentina": "ar",
    "co": "co", "col": "co", "colombia": "co",
    "cl": "cl", "chile": "cl",
    "pe": "pe", "peru": "pe",
    "ec": "ec", "ecuador": "ec",
    "bo": "bo", "bolivia": "bo",
    "ve": "ve", "venezuela": "ve",
    "py": "py", "paraguay": "py",
    "uy": "uy", "uruguay": "uy",
    "cr": "cr",
    "pa": "pa", "panama": "pa",
    "hn": "hn", "honduras": "hn",
    "sv": "sv", "salvador": "sv",
    "ni": "ni", "nic": "ni", "nicaragua": "ni",
    "gt": "gt", "guatemala": "gt", "guate": "gt", "gua": "gt",
    "do": "do", "rd": "do", "dominicana": "do",
    "pr": "pr",
    "es": "es", "esp": "es", "espana": "es",
    "br": "br", "brasil": "br", "bra": "br",
    "ca": "ca", "canada": "ca",
}

# Marcas nacionales (se revisan DESPUÉS de los tokens explícitos: «Azteca Guate»
# ya salió Guatemala y no debe caer en México).
COUNTRY_BRANDS = (
    ("las estrellas", "mx"), ("canal de las estrellas", "mx"), ("televisa", "mx"),
    ("azteca", "mx"), ("tudn", "mx"), ("canal 5", "mx"), ("adn40", "mx"),
    ("adn noticia", "mx"), ("multimedios", "mx"), ("milenio", "mx"),
    ("excelsior", "mx"), ("foro tv", "mx"), ("heraldo", "mx"), ("unicable", "mx"),
    ("distrito comedia", "mx"), ("bandamax", "mx"), ("telehit", "mx"),
    ("tlnovelas", "mx"), ("de pelicula", "mx"), ("golden", "mx"), ("bitme", "mx"),
    ("mexiquense", "mx"), ("el nueve", "ar"), ("elnueve", "ar"),
    ("win sports", "co"), ("caracol", "co"), ("rcn", "co"), ("cable noticias", "co"),
    ("teleantioquia", "co"), ("telecaribe", "co"), ("telecafe", "co"),
    ("senal colombia", "co"),
    ("canal 13 de chile", "cl"), ("tvn", "cl"), ("la red", "cl"),
    ("canal 13 de argentina", "ar"), ("el trece", "ar"), ("eltece", "ar"),
    ("telefe", "ar"), ("america tv", "ar"), ("tv publica", "ar"),
    ("television publica", "ar"),
    ("latina", "pe"), ("panamericana", "pe"), ("willax", "pe"), ("canal n", "pe"),
    ("ecuavisa", "ec"), ("canal uno", "ec"),
    ("tve", "es"), ("antena 3", "es"), ("telecinco", "es"), ("la sexta", "es"),
    ("globo", "br"), ("sbt", "br"), ("record", "br"),
)


def _fold(text: str) -> str:
    """Minúsculas, sin acentos, signos como espacio y espacios colapsados."""
    if not text:
        return ""
    t = "".join(
        c for c in unicodedata.normalize("NFD", text)
        if unicodedata.category(c) != "Mn"
    ).lower()
    t = re.sub(r"[^a-z0-9]+", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def channel_country(text: str) -> str:
    """País de la señal por el nombre (o el código): ``mx``, ``cl``… o ``""``.

    Primero frases y tokens explícitos («CANAL 5 HN HD» → ``hn``), después marcas
    nacionales («TUDN» → ``mx``, «Win Sports» → ``co``). Las marcas internacionales
    devuelven ``""``: el llamador decide el respaldo sin inventar un país.
    """
    if not text:
        return ""
    t = _fold(text)
    if not t:
        return ""
    padded = f" {t} "
    for phrase, country in COUNTRY_PHRASES:
        if f" {phrase} " in padded:
            return country
    tokens = set(t.split())
    # Los tokens largos mandan: «USA» antes que «US», «NIC» antes que «NI».
    for token in sorted(COUNTRY_TOKENS, key=len, reverse=True):
        if token in tokens:
            return COUNTRY_TOKENS[token]
    for brand, country in COUNTRY_BRANDS:
        if len(brand) <= 3:
            if f" {brand} " in padded:
                return country
        elif brand in t:
            return country
    return ""


def canonical_channel(text: str) -> str:
    """Token canónico de la cadena/edición (sin país; el país va aparte)."""
    if not text:
        return ""
    t = _fold(text)
    tokens = t.split()
    if not tokens:
        return ""
    tok = set(tokens)

    # Los feeds publican señales desplazadas («-1 Hora», «-2 Horas»): no deben
    # mezclarse con la señal principal.
    shift = re.search(r"-\s*([12])\s*horas?", text.lower())
    suffix = f"m{shift.group(1)}" if shift else ""

    def out(key: str) -> str:
        return key + suffix

    # --- cadenas nacionales / marcas ---
    if "estrellas" in tok or "lasestrellas" in tok or "xew" in tok or "canal de las estrellas" in t:
        return out("lasestrellas")
    if "azteca" in tok:
        if any(x in tok for x in ("1", "uno", "un")):
            return out("azteca1")
        if "7" in tok or "siete" in tok:
            return out("azteca7")
        if "deportes" in tok:
            return out("aztecadeportes")
        if "noticias" in tok or "adn" in tok or "40" in tok:
            return out("adn40")
        if "corazon" in tok:
            return out("aztezacorazon")
        if "mundo" in tok:
            return out("aztecamundo")
        return out("azteca")
    if "tudn" in tok:
        return out("tudn")
    if "adn" in tok and ("40" in tok or "noticia" in tok or "mx" in tok):
        return out("adn40")
    if "ntn24" in tok:
        return out("ntn24")
    if "24" in tok and "horas" in tok:
        return out("24horas")
    if "cinemax" in tok:
        return out("cinemax")
    if "warner" in tok:
        return out("warner")
    if "sony" in tok:
        return out("sony")
    if "hbo" in tok:
        if "family" in tok:
            return out("hbofamily")
        if "plus" in tok:
            return out("hboplus")
        if "mundi" in tok:
            return out("hbomundi")
        if "pop" in tok:
            return out("hbopop")
        if "xtreme" in tok:
            return out("hboxtreme")
        if "signature" in tok:
            return out("hbosignature")
        if "zone" in tok:
            return out("hbozone")
        if "2" in tok:
            return out("hbo2")
        return out("hbo")
    if "fox" in tok and "sports" in tok:
        if "premium" in tok:
            return out("foxsportspremium")
        if "2" in tok:
            return out("foxsports2")
        if "3" in tok:
            return out("foxsports3")
        return out("foxsports")
    if "fox" in tok and "deportes" in tok:
        return out("foxdeportes")
    if "fox" in tok and "one" in tok:
        return out("foxone")
    if "espn" in tok:
        if "2" in tok:
            return out("espn2")
        if "3" in tok:
            return out("espn3")
        if "4" in tok:
            return out("espn4")
        if "5" in tok:
            return out("espn5")
        if "6" in tok:
            return out("espn6")
        if "7" in tok:
            return out("espn7")
        if "extra" in tok:
            return out("espnextra")
        if "premium" in tok:
            return out("espnpremium")
        return out("espn")
    if "bein" in tok and "sports" in tok:
        if "1" in tok:
            return out("bein1sports")
        if "2" in tok:
            return out("bein2sports")
        if "3" in tok:
            return out("bein3sports")
        return out("beinsports")
    if "claro" in tok and "sports" in tok:
        if "2" in tok:
            return out("clarosports2")
        return out("clarosports1")
    if "directv" in tok or "dsports" in tok:
        if "2" in tok:
            return out("dsports2")
        if "plus" in tok:
            return out("dsportsplus")
        if "motor" in tok:
            return out("dsportsmotor")
        return out("dsports")
    if "win" in tok and "sports" in tok:
        if "plus" in tok or "premium" in tok or "+" in text:
            return out("winsportsplus")
        return out("winsports")
    if "tnt" in tok and "sports" in tok:
        if "2" in tok:
            return out("tntsports2")
        if "premium" in tok:
            return out("tntsportspremium")
        if "estadio" in tok:
            return out("tntsportsestadio")
        return out("tntsports")
    if "tnt" in tok:
        if "series" in tok:
            return out("tntseries")
        if "novelas" in tok:
            return out("tntnovelas")
        return out("tnt")
    if "golden" in tok and "premier" in tok:
        if "2" in tok:
            return out("goldenpremier2")
        return out("goldenpremier")
    if "history" in tok:
        if "2" in tok:
            return out("history2")
        return out("history")
    if ("investigation" in tok and "discovery" in tok) or "id" in tok:
        return out("id")
    if "usa" in tok and "network" in tok:
        return out("usanetwork")
    if "e" in tok and "entertainment" in tok:
        return out("eentertainment")
    if "gourmet" in tok:
        return out("elgourmet")
    if "mas" in tok and "chic" in tok:
        return out("maschic")
    if "discovery" in tok and "world" in tok:
        return out("discoveryworld")
    if "discovery" in tok and ("home" in tok or tokens.count("h") >= 2):
        return out("discoveryhomehealth")
    if "az" in tok and "corazon" in tok:
        return out("aztezacorazon")
    if "az" in tok and "cinema" in tok:
        return out("azcinema")
    if "3abn" in tok:
        return out("3abn")
    if "cnn" in tok:
        if "chile" in tok:
            return out("cnnchile")
        if "espanol" in tok:
            return out("cnnespanol")
        if "internacional" in tok or "international" in tok:
            return out("cnninternacional")
        return out("cnn")
    if "axn" in tok:
        if "white" in tok:
            return out("axnwhite")
        return out("axn")
    if "a3" in tok and "cinema" in tok:
        return out("a3cinema")
    if "a3s" in tok:
        return out("a3s")
    if "america" in tok and "teve" in tok:
        return out("americateve")
    if "america" in tok and ("television" in tok or "tv" in tok or "peru" in tok):
        return out("america")
    if "tve" in tok and ("internacional" in tok or "international" in tok):
        return out("tveinternacional")
    if "television" in tok and "publica" in tok:
        return out("televisionpublica")
    if "trece" in tok:
        return out("canal13")
    if "cable" in tok and "noticias" in tok:
        return out("cablenoticias")
    if "baby" in tok:
        return out("baby")
    if "nick" in tok:
        if "jr" in tok:
            return out("nickjr")
        if "music" in tok:
            return out("nickmusic")
        return out("nick")
    if "teennick" in tok:
        return out("teennick")
    if "nickelodeon" in tok:
        return out("nickelodeon")
    if "disney" in tok:
        if "junior" in tok:
            return out("disneyjunior")
        if "channel" in tok:
            return out("disneychannel")
    if "cartoon" in tok:
        if "network" in tok:
            return out("cartoonnetwork")
        return out("cartoonito")
    if "discovery" in tok and "kids" in tok:
        return out("discoverykids")
    if "pakapaka" in tok:
        return out("pakapaka")
    if "enlace" in tok:
        return out("enlace")
    if "ewtn" in tok:
        return out("ewtnespanol")
    if "hola" in tok:
        return out("hola")
    if "star" in tok and "channel" in tok:
        return out("starchannel")
    if "space" in tok:
        return out("space")
    if "cinecanal" in tok:
        return out("cinecanal")
    if "fx" in tok:
        return out("fx")
    if "amc" in tok:
        return out("amc")
    if "a" in tok and "e" in tok:
        return out("ae")
    if "tlc" in tok:
        return out("tlc")
    if "lifetime" in tok:
        return out("lifetime")
    if "wobi" in tok:
        return out("wobi")
    if "esne" in tok:
        return out("esne")
    if "mariavision" in tok:
        return out("mariavision")
    if "claro" in tok:
        if "cinema" in tok:
            return out("clarocinema")
        return out("claro")
    if "multimedios" in tok:
        return out("multimedios")
    if "milenio" in tok:
        return out("milenio")
    if "excelsior" in tok:
        return out("excelsior")
    if "foro" in tok:
        return out("foro")
    if "heraldo" in tok:
        return out("heraldo")
    if "unicable" in tok:
        return out("unicable")
    if "distrito" in tok and "comedia" in tok:
        return out("distritocomedia")
    if "bandamax" in tok:
        return out("bandamax")
    if "telehit" in tok:
        return out("telehit")
    if "tlnovelas" in tok:
        return out("tlnovelas")
    if "pelicula" in tok:
        return out("pelicula")
    if "bitme" in tok:
        return out("bitme")
    if "mexiquense" in tok:
        return out("mexiquense")
    if "teleformula" in tok:
        return out("teleformula")
    if "efekto" in tok:
        return out("efekto")
    if "vibra" in tok:
        return out("vibra")
    if "aprende" in tok:
        return out("aprende")
    if "clic" in tok:
        return out("clic")
    if "tvunam" in tok:
        return out("tvunam")

    # --- canales numerados (después de las marcas: «Azteca 7» ya salió) ---
    if "canal" in tok:
        if "24" in tok and "horas" in tok:
            return out("24horas")
        for number in (
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "22",
        ):
            if number in tok:
                return out(f"canal{number}")
        if "once" in tok:
            return out("canal11")
        if "n" in tok:
            return out("canaln")

    filtered = [x for x in tokens if x not in NOISE_WORDS]
    if len(filtered) > 1 and filtered[0] == "canal":
        filtered = filtered[1:]
    return out("".join(filtered) if filtered else "".join(tokens))


def epg_norms_for(name: str, code: str = "") -> list[str]:
    """Claves de guía a probar, en orden de prioridad (nunca mezcla países).

    * Canal de un país concreto (``cl``, ``co``, ``hn``…): SOLO ese país. Si no hay
      feed, se devuelve vacío (mejor «Guía no disponible» que programación ajena).
    * Canal mexicano o sin país: la señal mexicana primero y, para marcas
      internacionales, los feeds latinos como respaldo.
    * Cadena nacional (Canal 5, Azteca 7, Las Estrellas…): jamás cruza de país.
    """
    canon = canonical_channel(name or "") or canonical_channel(code or "")
    if not canon:
        return []
    country = channel_country(name or "") or channel_country(code or "")
    if country and country != "mx":
        return [f"{country}:{canon}"]
    if canon in NATIONAL_ONLY:
        return [f"mx:{canon}"]
    return [f"{c}:{canon}" for c in COUNTRY_FALLBACK]
