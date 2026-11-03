' Vision+ for Roku - utilidades compartidas, catálogo, TV en vivo y deportes.
' Sin dependencias de Android. Todo el estado vive en el registro de Roku.

function Theme_Bg() as String : return "0x070B13FF" : end function
function Theme_Surface() as String : return "0x0F1626FF" : end function
function Theme_Surface2() as String : return "0x1B2538FF" : end function
function Theme_Line() as String : return "0x2C3A54FF" : end function
function Theme_Text() as String : return "0xF4F7FCFF" : end function
function Theme_Muted() as String : return "0x9BA8BDFF" : end function
function Theme_Muted2() as String : return "0x6F7D96FF" : end function
function Theme_Blue() as String : return "0x2D7DF6FF" : end function
function Theme_BlueLt() as String : return "0x6BA6FFFF" : end function
function Theme_Green() as String : return "0x7AD9A8FF" : end function
function Theme_Gold() as String : return "0xF5C518FF" : end function
function Theme_Red() as String : return "0xFF4B4BFF" : end function

function Tern(cond as Boolean, a as Dynamic, b as Dynamic) as Dynamic
    if cond then return a
    return b
end function

function SafeStr(value as Dynamic) as String
    if value = invalid then return ""
    if Type(value) = "roString" or Type(value) = "String" then return value
    return value.ToStr()
end function

function AsInt(value as Dynamic) as Integer
    if value = invalid then return 0
    if Type(value) = "roString" or Type(value) = "String" then return Int(Val(value))
    return Int(value)
end function

function AsFloat(value as Dynamic) as Float
    if value = invalid then return 0.0
    if Type(value) = "roString" or Type(value) = "String" then return Val(value)
    return value
end function

function AsBool(value as Dynamic) as Boolean
    if value = invalid then return false
    if Type(value) = "roBoolean" or Type(value) = "Boolean" then return value
    return false
end function

function IsValidStr(value as Dynamic) as Boolean
    return value <> invalid and (Type(value) = "roString" or Type(value) = "String") and value <> ""
end function

function EncodeUrl(text as String) as String
    out = ""
    for i = 0 to Len(text) - 1
        c = Mid(text, i + 1, 1)
        code = Asc(c)
        if (code >= 48 and code <= 57) or (code >= 65 and code <= 90) or (code >= 97 and code <= 122) or c = "-" or c = "_" or c = "." or c = "~"
            out = out + c
        else
            out = out + "%" + Right("0" + StrI(code, 16), 2).Upper()
        end if
    end for
    return out
end function

function NormalizeTitle(raw as String) as String
    t = raw.Trim()
    if Instr(1, t, "_") > 0 then t = t.Replace("_", " ")
    while Instr(1, t, "  ") > 0
        t = t.Replace("  ", " ")
    end while
    rx = CreateObject("roRegex", "\\s*[-–—(]\\s*(espanol|español|latino|castellano)\\s*\\)?\\s*$", "i")
    t = rx.ReplaceAll(t, "")
    return t.Trim()
end function

function NowSeconds() as Integer
    return CreateObject("roDateTime").AsSeconds()
end function

function MD5Hex(text as String) as String
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(text)
    d = CreateObject("roEVPDigest")
    if d = invalid or d.Setup("md5") <> 0 then return ""
    return LCase(d.Process(ba))
end function

function Sha256Hex(text as String) as String
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(text)
    d = CreateObject("roEVPDigest")
    if d = invalid or d.Setup("sha256") <> 0 then return ""
    return LCase(d.Process(ba))
end function

function RandomHex(chars as Integer) as String
    di = CreateObject("roDeviceInfo")
    out = ""
    while Len(out) < chars
        out = out + LCase(di.GetRandomUUID().Replace("-", ""))
    end while
    return Left(out, chars)
end function

function BytesToHex(ba as Object) as String
    if ba = invalid then return ""
    out = ""
    for i = 0 to ba.Count() - 1
        out = out + Right("0" + StrI(ba[i], 16), 2)
    end for
    return LCase(out)
end function

function HexToBytes(hex as String) as Object
    ba = CreateObject("roByteArray")
    ba.FromHexString(hex)
    return ba
end function

function HexToBase64(hex as String) as String
    ba = HexToBytes(hex)
    if ba = invalid or ba.Count() = 0 then return ""
    return ba.ToBase64String()
end function

function Base64ToHex(b64 as String) as String
    ba = CreateObject("roByteArray")
    ba.FromBase64String(b64)
    return BytesToHex(ba)
end function

function ParseJsonSafe(text as String) as Dynamic
    if text = "" or text = invalid then return invalid
    return ParseJSON(text)
end function

' ---------------------------------------------------------------------------
' API del VPS de Vision+
' ---------------------------------------------------------------------------

function VPS_Base() as String
    return "https://visionplusv2.duckdns.org"
end function

function VPS_ApiKey() as String
    ' Llave de solo lectura que ya viaja en el APK de Vision+.
    return "fb2244441f690185f861470423afb43278c3b19d31b14115"
end function

function VPS_Headers() as Object
    return {
        "X-Api-Key": VPS_ApiKey()
        "Accept": "application/json"
        "Content-Type": "application/json"
    }
end function

function VPS_Url(path as String) as String
    return VPS_Base() + path
end function

function NewApiTask(url as String, method as String, body as String) as Object
    task = CreateObject("roSGNode", "ApiTask")
    task.url = url
    task.method = method
    task.body = body
    task.headers = VPS_Headers()
    return task
end function

function NewXuperTask(op as String, args as Object) as Object
    task = CreateObject("roSGNode", "XuperTask")
    task.op = op
    task.args = args
    return task
end function

' ---------------------------------------------------------------------------
' Catálogo
' ---------------------------------------------------------------------------

function Film_FromJson(f as Object) as Dynamic
    if f = invalid then return invalid
    rawId = SafeStr(f.id)
    rawVideo = SafeStr(f.videoUrl)
    if Left(rawVideo, 9) = "magisvod:" then
        cid = Mid(rawVideo, 10)
        rawId = "xtv:" + cid
        rawVideo = "xtv:" + cid
    else if Left(rawId, 6) = "magis:" and Left(rawVideo, 4) <> "http" then
        cid = Mid(rawId, 7)
        rawId = "xtv:" + cid
        rawVideo = "xtv:" + cid
    end if
    if rawId = "" then return invalid
    seasons = []
    if f.seasons <> invalid
        for each s in f.seasons
            n = AsInt(s.n)
            if n > 0
                seasons.Push({ n: n, titleId: SafeStr(s.titleId), videoUrl: SafeStr(s.videoUrl) })
            end if
        end for
    end if
    genres = []
    if f.genres <> invalid
        for each g in f.genres
            if IsValidStr(g) then genres.Push(SafeStr(g))
        end for
    end if
    return {
        id: rawId
        title: NormalizeTitle(SafeStr(f.title))
        originalTitle: NormalizeTitle(SafeStr(f.originalTitle))
        year: AsInt(f.year)
        durationMin: AsInt(f.durationMin)
        synopsis: SafeStr(f.synopsis)
        posterUrl: SafeStr(f.posterUrl)
        videoUrl: rawVideo
        sourceUrl: SafeStr(f.sourceUrl)
        score: AsFloat(f.score)
        popularity: AsFloat(f.popularity)
        voteCount: AsInt(f.voteCount)
        tmdbId: AsInt(f.tmdbId)
        groupId: SafeStr(f.groupId)
        seasons: seasons
        kind: SafeStr(f.kind)
        genres: genres
        adult: AsBool(f.adult)
    }
end function

function ParseFilms(arr as Dynamic) as Object
    out = []
    if arr = invalid then return out
    seen = {}
    for each raw in arr
        f = Film_FromJson(raw)
        if f <> invalid and seen[f.id] = invalid
            seen[f.id] = true
            out.Push(f)
        end if
    end for
    return out
end function

function ParseIndexRows(json as String) as Object
    root = ParseJsonSafe(json)
    if root = invalid or root.categories = invalid then return []
    rows = []
    for each cat in root.categories
        films = ParseFilms(cat.films)
        if films.Count() > 0
            rows.Push({ id: SafeStr(cat.id), title: SafeStr(cat.title), films: films, total: AsInt(cat.total) })
        end if
    end for
    return rows
end function

function ParseSectionRows(json as String) as Object
    root = ParseJsonSafe(json)
    if root = invalid or root.rows = invalid then return []
    rows = []
    for each cat in root.rows
        films = ParseFilms(cat.films)
        if films.Count() > 0
            rows.Push({ id: SafeStr(cat.id), title: SafeStr(cat.title), films: films, total: AsInt(cat.total) })
        end if
    end for
    return rows
end function

function ParseSearchFilms(json as String) as Object
    root = ParseJsonSafe(json)
    if root = invalid or root.films = invalid then return []
    return ParseFilms(root.films)
end function

function ParseCategoryFilms(json as String) as Object
    root = ParseJsonSafe(json)
    if root = invalid or root.films = invalid then return []
    return ParseFilms(root.films)
end function

function Film_Cid(f as Object) as String
    if f = invalid then return ""
    v = SafeStr(f.videoUrl)
    if Left(v, 4) = "xtv:" then return Mid(v, 5)
    i = SafeStr(f.id)
    if Left(i, 4) = "xtv:" then return Mid(i, 5)
    return i
end function

function Film_IsSeries(f as Object) as Boolean
    if f = invalid then return false
    if f.seasons <> invalid and f.seasons.Count() > 0 then return true
    k = SafeStr(f.kind)
    return k = "series" or k = "anime" or k = "novela" or k = "telenovela"
end function

function Film_IsLive(f as Object) as Boolean
    if f = invalid then return false
    return Left(SafeStr(f.videoUrl), 8) = "xtvlive:" or SafeStr(f.kind) = "live"
end function

function Film_ScoreText(f as Object) as String
    if f = invalid or f.score <= 0 then return ""
    return Str(f.score).Trim()
end function

function Film_DurationText(minutes as Integer) as String
    if minutes <= 0 then return ""
    h = minutes \ 60
    m = minutes mod 60
    if h > 0 then return h.ToStr() + "h " + m.ToStr() + "m"
    return m.ToStr() + "m"
end function

function Film_Subtitle(f as Object) as String
    if f = invalid then return ""
    parts = []
    if f.year > 0 then parts.Push(f.year.ToStr())
    if f.durationMin > 0 then parts.Push(Film_DurationText(f.durationMin))
    if f.seasons <> invalid and f.seasons.Count() > 0 then parts.Push(f.seasons.Count().ToStr() + " temp.")
    if parts.Count() = 0 then return SafeStr(f.kind)
    return parts.Join(" · ")
end function

function Film_Badge(f as Object) as String
    if f = invalid then return ""
    if Film_IsLive(f) then return "EN VIVO"
    if Film_IsSeries(f) then return "SERIE"
    if f.year >= 2025 then return "ESTRENO"
    return ""
end function

function Film_Mini(f as Object) as Object
    if f = invalid then return invalid
    syn = SafeStr(f.synopsis)
    if Len(syn) > 420 then syn = Left(syn, 420)
    return {
        id: f.id
        title: f.title
        year: f.year
        durationMin: f.durationMin
        synopsis: syn
        posterUrl: f.posterUrl
        videoUrl: f.videoUrl
        score: f.score
        kind: f.kind
        genres: f.genres
        groupId: f.groupId
        seasons: f.seasons
        adult: AsBool(f.adult)
    }
end function

function Film_FromMini(m as Object) as Object
    if m = invalid then return invalid
    return {
        id: SafeStr(m.id)
        title: SafeStr(m.title)
        originalTitle: ""
        year: AsInt(m.year)
        durationMin: AsInt(m.durationMin)
        synopsis: SafeStr(m.synopsis)
        posterUrl: SafeStr(m.posterUrl)
        videoUrl: SafeStr(m.videoUrl)
        sourceUrl: "xtv"
        score: AsFloat(m.score)
        popularity: 0.0
        voteCount: 0
        tmdbId: 0
        groupId: SafeStr(m.groupId)
        seasons: Tern(m.seasons = invalid, [], m.seasons)
        kind: SafeStr(m.kind)
        genres: Tern(m.genres = invalid, [], m.genres)
        adult: AsBool(m.adult)
    }
end function

' ---------------------------------------------------------------------------
' Contenido para RowList / MarkupGrid / LabelList
' ---------------------------------------------------------------------------

function EmptyContent() as Object
    return CreateObject("roSGNode", "ContentNode")
end function

function FilmItemNode(f as Object) as Object
    item = CreateObject("roSGNode", "ContentNode")
    item.AddFields({
        film: f
        title: f.title
        poster: f.posterUrl
        score: Film_ScoreText(f)
        subtitle: Film_Subtitle(f)
        badge: Film_Badge(f)
        rank: 0
    })
    return item
end function

function Rows_ToContent(rows as Object) as Object
    content = EmptyContent()
    for each row in rows
        rowNode = CreateObject("roSGNode", "ContentNode")
        rowNode.AddFields({ Title: row.title, rowId: row.id })
        rank = 0
        for each f in row.films
            rank = rank + 1
            item = FilmItemNode(f)
            if row.id = "top10" then item.rank = rank
            rowNode.AppendChild(item)
        end for
        content.AppendChild(rowNode)
    end for
    return content
end function

function Grid_ToContent(films as Object) as Object
    content = EmptyContent()
    for each f in films
        content.AppendChild(FilmItemNode(f))
    end for
    return content
end function

function Channels_ToContent(channels as Object, epg as Object) as Object
    content = EmptyContent()
    for each ch in channels
        item = CreateObject("roSGNode", "ContentNode")
        epgText = ""
        if epg <> invalid
            e = epg[ch.code]
            if e = invalid then e = epg[LCase(ch.name)]
            if e <> invalid and e.now <> "" then epgText = "Ahora: " + e.now
        end if
        item.AddFields({
            channel: ch
            title: ch.name
            poster: ch.logo
            subtitle: epgText
            badge: Tern(ch.number > 0, ch.number.ToStr(), "")
        })
        content.AppendChild(item)
    end for
    return content
end function

function Categories_ToContent(categories as Object) as Object
    content = EmptyContent()
    for each c in categories
        item = CreateObject("roSGNode", "ContentNode")
        item.AddFields({ Title: c.name, category: c })
        content.AppendChild(item)
    end for
    return content
end function

' ---------------------------------------------------------------------------
' TV en vivo
' ---------------------------------------------------------------------------

function ParseLiveCategories(json as String) as Object
    root = ParseJsonSafe(json)
    out = []
    if root = invalid then return out
    if root.childColumnList <> invalid
        for each c in root.childColumnList
            out.Push({ id: AsInt(c.id), name: SafeStr(c.name), code: SafeStr(c.code) })
        end for
    end if
    if out.Count() = 0 then out.Push({ id: 76182, name: "Todos los canales", code: "" })
    return out
end function

function ParseLiveChannels(json as String) as Object
    root = ParseJsonSafe(json)
    out = []
    if root = invalid then return out
    arr = root.channelList
    if arr = invalid then arr = root
    if arr = invalid then return out
    seen = {}
    for each c in arr
        code = SafeStr(c.channelCode)
        if code = "" and c.liveAddressList <> invalid and c.liveAddressList.Count() > 0
            code = SafeStr(c.liveAddressList[0].playCode)
        end if
        if code = "" then continue for
        if seen[code] <> invalid then continue for
        seen[code] = true
        logo = SafeStr(c.posterUrl)
        if logo = "" and c.posterList <> invalid
            for each p in c.posterList
                if SafeStr(p.fileType) = "icon" then logo = SafeStr(p.fileUrl)
            end for
        end if
        group = SafeStr(c.categoryName)
        if group = "" then group = SafeStr(c.columnName)
        if group = "" then group = SafeStr(c.group)
        if group = "" then group = "TV en vivo"
        out.Push({
            code: code
            name: SafeStr(c.name)
            logo: logo
            number: AsInt(c.channelNumber)
            group: group
            playUrl: SafeStr(c.playUrl)
        })
    end for
    return out
end function

function ChannelCodeFromRef(ref as String) as String
    if Left(ref, 8) = "xtvlive:" then return Mid(ref, 9)
    return ref
end function

function ParseEpgBulk(json as String) as Object
    root = ParseJsonSafe(json)
    map = {}
    if root = invalid or root.channels = invalid then return map
    for each key in root.channels
        e = root.channels[key]
        if e = invalid then continue for
        now = ""
        upNext = ""
        startMs = 0
        endMs = 0
        if e.now <> invalid
            now = SafeStr(e.now.name)
            startMs = AsInt(e.now.startMs)
            endMs = AsInt(e.now.endMs)
        end if
        if e["next"] <> invalid then upNext = SafeStr(e["next"].name)
        map[key] = { now: now, upNext: upNext, startMs: startMs, endMs: endMs }
        map[LCase(key)] = map[key]
    end for
    return map
end function

function Epg_TextFor(epg as Object, code as String, name as String) as String
    if epg = invalid then return ""
    e = epg[code]
    if e = invalid then e = epg[LCase(name)]
    if e = invalid then return ""
    if e.now = "" then return ""
    t = "Ahora: " + e.now
    if e["next"] <> "" then t = t + "   ·   Sigue: " + e["next"]
    return t
end function

' ---------------------------------------------------------------------------
' Deportes (GeeSports + agenda del VPS)
' ---------------------------------------------------------------------------

function ParseGeeLinks(arr as Dynamic) as Object
    out = []
    if arr = invalid then return out
    for each l in arr
        url = SafeStr(l.url)
        if url = "" then url = SafeStr(l.link)
        if Instr(1, url, "|") > 0 then url = Left(url, Instr(1, url, "|") - 1)
        out.Push({
            name: SafeStr(l.name)
            url: url
            type: SafeStr(l.type)
            drm: SafeStr(l.drm)
            alive: Tern(l.alive = invalid, true, AsBool(l.alive))
            headers: Tern(l.headers = invalid, {}, l.headers)
        })
    end for
    return out
end function

function ParseGeeXtv(arr as Dynamic) as Object
    out = []
    if arr = invalid then return out
    for each x in arr
        name = SafeStr(x.name)
        code = ChannelCodeFromRef(SafeStr(x.code))
        if name = "" or code = "" then continue for
        out.Push({
            name: name
            code: code
            country: SafeStr(x.country)
            brand: SafeStr(x.brand)
            recommended: AsBool(x.recommended)
            verified: AsBool(x.verified)
            note: SafeStr(x.note)
        })
    end for
    return out
end function

function ParseGeeEvents(json as String) as Object
    root = ParseJsonSafe(json)
    out = []
    if root = invalid or root.events = invalid then return out
    for each e in root.events
        if e.visible = false then continue for
        title = SafeStr(e.team_a)
        away = SafeStr(e.team_b)
        if title <> "" and away <> "" then title = title + " vs " + away
        if title = "" then title = SafeStr(e.event)
        score = ""
        live = false
        detail = ""
        if e.score <> invalid
            if e.score.home <> invalid and e.score.away <> invalid
                score = SafeStr(e.score.home) + " - " + SafeStr(e.score.away)
            end if
            live = AsBool(e.score.live)
            detail = SafeStr(e.score.detail)
        end if
        out.Push({
            id: SafeStr(e.id)
            title: title
            event: SafeStr(e.event)
            league: SafeStr(e.league_label)
            leagueLogo: SafeStr(e.league_logo)
            logo: SafeStr(e.logo)
            teamA: SafeStr(e.team_a)
            teamB: SafeStr(e.team_b)
            date: SafeStr(e.date_iso)
            time: SafeStr(e.time)
            category: SafeStr(e.category)
            live: live
            score: score
            detail: detail
            tier: AsInt(e.tier)
            links: ParseGeeLinks(e.links)
            xtv: ParseGeeXtv(e.xtv)
        })
    end for
    return out
end function

function Sports_DayIso(offset as Integer) as String
    dt = CreateObject("roDateTime")
    dt.FromSeconds(dt.AsSeconds() + offset * 86400)
    dt.ToLocalTime()
    return dt.GetYear().ToStr() + "-" + Right("0" + dt.GetMonth().ToStr(), 2) + "-" + Right("0" + dt.GetDay().ToStr(), 2)
end function

function Sports_TimeText(e as Object) as String
    if e = invalid then return ""
    t = SafeStr(e.time)
    if Len(t) >= 5 then t = Left(t, 5)
    if e.live = true then return "EN VIVO " + SafeStr(e.detail)
    if e.score <> "" then return SafeStr(e.score) + "  " + SafeStr(e.detail)
    return t
end function

function Events_ToContent(events as Object) as Object
    content = EmptyContent()
    for each e in events
        item = CreateObject("roSGNode", "ContentNode")
        subText = SafeStr(e.league)
        if subText <> "" and e.time <> "" then subText = subText + " · " + Left(SafeStr(e.time), 5)
        item.AddFields({
            event: e
            title: e.title
            subtitle: subText
            poster: Tern(e.leagueLogo <> "", e.leagueLogo, e.logo)
            badge: Tern(e.live, "EN VIVO", Sports_TimeText(e))
        })
        content.AppendChild(item)
    end for
    return content
end function

function RowLabelFont() as Object
    f = CreateObject("roSGNode", "Font")
    f.uri = "font:MediumBoldSystemFont"
    f.size = 30
    return f
end function


function Film_IsAdult(f as Object) as Boolean
    if f = invalid then return false
    if AsBool(f.adult) then return true
    title = LCase(SafeStr(f.title))
    if Instr(1, title, "+18") > 0 or Instr(1, title, "adulto") > 0 then return true
    if f.genres <> invalid
        for each g in f.genres
            if Instr(1, LCase(SafeStr(g)), "adult") > 0 then return true
        end for
    end if
    return false
end function


function ParseBanners(json as String) as Object
    root = ParseJsonSafe(json)
    out = []
    if root = invalid or root.items = invalid then return out
    for each b in root.items
        ref = SafeStr(b.ref)
        video = SafeStr(b.videoUrl)
        if video = "" then video = ref
        if ref = "" and video = "" then continue for
        kind = Tern(AsBool(b.series), "series", "movie")
        film = {
            id: Tern(ref <> "", ref, "banner:" + SafeStr(b.id))
            title: NormalizeTitle(SafeStr(b.title))
            originalTitle: ""
            year: AsInt(b.year)
            durationMin: AsInt(b.durationMin)
            synopsis: SafeStr(b.synopsis)
            posterUrl: SafeStr(b.poster)
            videoUrl: video
            sourceUrl: "banner"
            score: AsFloat(b.rating)
            popularity: 0.0
            voteCount: 0
            tmdbId: 0
            groupId: ""
            seasons: []
            kind: kind
            genres: []
            adult: false
        }
        meta = []
        if film.year > 0 then meta.Push(film.year.ToStr())
        if film.score > 0 then meta.Push(Str(film.score).Trim())
        if film.durationMin > 0 then meta.Push(Film_DurationText(film.durationMin))
        out.Push({
            title: film.title
            synopsis: film.synopsis
            backdrop: SafeStr(b.backdrop)
            poster: SafeStr(b.poster)
            badge: SafeStr(b.badge)
            eyebrow: SafeStr(b.eyebrow)
            meta: meta.Join("  ·  ")
            film: film
        })
    end for
    return out
end function

function Film_HeroMeta(f as Object) as String
    parts = []
    if f = invalid then return ""
    if f.year > 0 then parts.Push(f.year.ToStr())
    if f.score > 0 then parts.Push(Str(f.score).Trim())
    if f.durationMin > 0 then parts.Push(Film_DurationText(f.durationMin))
    return parts.Join("  ·  ")
end function
