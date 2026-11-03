sub init()
    m.poster = m.top.FindNode("poster")
    m.title = m.top.FindNode("title")
    m.meta = m.top.FindNode("meta")
    m.badge = m.top.FindNode("badge")
    m.synopsis = m.top.FindNode("synopsis")
    m.actions = m.top.FindNode("actions")
    m.seasons = m.top.FindNode("seasons")
    m.seasonsLabel = m.top.FindNode("seasonsLabel")
    m.episodes = m.top.FindNode("episodes")
    m.status = m.top.FindNode("status")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.film = invalid
    m.seasonList = []
    m.episodesList = []
    m.currentSeason = invalid
    m.epTask = invalid
    m.pinChecked = false
    m.pendingPlay = invalid
    m.gateDialog = invalid
    m.xTask = invalid
    m.actions.ObserveField("itemSelected", "OnAction")
    m.seasons.ObserveField("itemSelected", "OnSeason")
    m.episodes.ObserveField("itemSelected", "OnEpisode")
    m.top.ObserveField("load", "OnRefresh")
end sub

sub OnRefresh()
    if m.top.film <> invalid then OnFilm()
end sub

sub OnFilm()
    film = m.top.film
    if film = invalid then return
    m.film = film
    m.poster.uri = Tern(film.posterUrl <> "", film.posterUrl, "pkg:/images/placeholder.png")
    m.title.text = film.title
    m.meta.text = Film_MetaText(film)
    m.badge.text = Tern(Film_IsSeries(film), "SERIE", Tern(Film_IsLive(film), "TV EN VIVO", "PELÍCULA"))
    m.synopsis.text = film.synopsis
    if m.synopsis.text = "" then m.synopsis.text = "Sin sinopsis disponible."
    m.status.text = ""
    m.pinChecked = false
    m.errorLabel.visible = false
    BuildActions()
    m.episodesList = []
    m.episodes.content = EmptyContent()
    m.episodes.visible = false
    m.seasons.visible = false
    m.seasonsLabel.visible = false
    if Film_IsSeries(film)
        BuildSeasons()
    else
        m.actions.SetFocus(true)
    end if
end sub

function Film_MetaText(f as Object) as String
    parts = []
    if f.year > 0 then parts.Push(f.year.ToStr())
    if f.durationMin > 0 then parts.Push(Film_DurationText(f.durationMin))
    if f.score > 0 then parts.Push("★ " + Str(f.score).Trim())
    if f.genres <> invalid and f.genres.Count() > 0 then parts.Push(f.genres.Join(" · "))
    return parts.Join("  ·  ")
end function

sub BuildActions()
    content = EmptyContent()
    for each title in ["▶  Reproducir", Tern(Lib_IsFavorite(m.film), "★  Quitar de favoritos", "☆  Añadir a favoritos"), Tern(Lib_IsWatchLater(m.film), "✓  Quitar de ver más tarde", "＋  Ver más tarde")]
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: title })
        content.AppendChild(n)
    end for
    m.actions.content = content
end sub

sub BuildSeasons()
    m.seasonList = m.film.seasons
    if m.seasonList = invalid or m.seasonList.Count() = 0
        m.seasonList = [{ n: 1, titleId: m.film.id, videoUrl: m.film.videoUrl }]
        m.seasons.visible = false
        m.seasonsLabel.visible = false
        LoadEpisodes(m.seasonList[0])
        m.actions.SetFocus(true)
        return
    end if
    content = EmptyContent()
    for each s in m.seasonList
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: "Temporada " + s.n.ToStr() })
        content.AppendChild(n)
    end for
    m.seasons.content = content
    m.seasons.visible = true
    m.seasonsLabel.visible = true
    m.currentSeason = m.seasonList[0]
    m.actions.SetFocus(true)
    LoadEpisodes(m.currentSeason)
end sub

sub OnSeason()
    idx = m.seasons.itemSelected
    if idx < 0 or idx >= m.seasonList.Count() then return
    m.currentSeason = m.seasonList[idx]
    LoadEpisodes(m.currentSeason)
end sub

sub LoadEpisodes(season as Object)
    if season = invalid then return
    m.loading.visible = true
    m.errorLabel.visible = false
    m.episodes.visible = false
    m.episodesList = []
    ref = SafeStr(season.titleId)
    if ref = "" then ref = SafeStr(m.film.id)
    if m.epTask <> invalid then m.epTask.control = "STOP"
    m.epTask = NewApiTask(VPS_Url("/v1/episodes?ref=" + EncodeUrl(ref)), "GET", "")
    m.epTask.ObserveField("response", "OnMirrorEpisodes")
    m.epTask.ObserveField("error", "OnMirrorError")
    m.epTask.control = "RUN"
end sub

sub OnMirrorEpisodes()
    if m.epTask = invalid then return
    m.loading.visible = false
    eps = []
    root = ParseJsonSafe(m.epTask.response)
    if root <> invalid and root.status = "ok" and root.episodes <> invalid
        eps = Episodes_FromVps(root.episodes)
    end if
    if eps.Count() > 0
        ShowEpisodes(eps)
        return
    end if
    ' El espejo del VPS puede estar frío: se pide al portal con la sesión del Roku.
    cid = ""
    if m.currentSeason <> invalid then cid = Film_Cid({ videoUrl: SafeStr(m.currentSeason.videoUrl), id: SafeStr(m.currentSeason.titleId) })
    if cid = "" then cid = Film_Cid(m.film)
    if m.xTask <> invalid then m.xTask.control = "STOP"
    m.xTask = NewXuperTask("episodes", { contentId: cid })
    m.xTask.ObserveField("result", "OnPortalEpisodes")
    m.xTask.ObserveField("error", "OnPortalError")
    m.xTask.control = "RUN"
    m.loading.visible = true
end sub

sub OnMirrorError()
    m.loading.visible = false
    ' Se intenta el portal, no se rinde con el espejo.
    cid = Film_Cid(m.film)
    if m.currentSeason <> invalid then cid = Film_Cid({ videoUrl: SafeStr(m.currentSeason.videoUrl), id: SafeStr(m.currentSeason.titleId) })
    m.xTask = NewXuperTask("episodes", { contentId: cid })
    m.xTask.ObserveField("result", "OnPortalEpisodes")
    m.xTask.ObserveField("error", "OnPortalError")
    m.xTask.control = "RUN"
    m.loading.visible = true
end sub

sub OnPortalEpisodes()
    if m.xTask = invalid then return
    m.loading.visible = false
    arr = ParseJsonSafe(m.xTask.result)
    eps = Episodes_FromPortal(arr)
    if eps.Count() = 0
        m.errorLabel.text = "No se pudieron cargar los capítulos de esta temporada."
        m.errorLabel.visible = true
        return
    end if
    ShowEpisodes(eps)
end sub

sub OnPortalError()
    m.loading.visible = false
    err = ""
    if m.xTask <> invalid then err = SafeStr(m.xTask.error)
    m.errorLabel.text = "No se pudieron cargar los capítulos: " + err
    m.errorLabel.visible = true
end sub

function Episodes_FromVps(arr as Object) as Object
    out = []
    if arr = invalid then return out
    for each e in arr
        cid = SafeStr(e.contentId)
        if cid = "" then continue for
        num = AsInt(e.seriesNumber)
        if num <= 0 then num = AsInt(e.episodeNumber)
        if num <= 0 then num = out.Count() + 1
        name = SafeStr(e.title)
        if name = "" then name = SafeStr(e.name)
        if name = "" then name = "Episodio " + num.ToStr()
        still = SafeStr(e.still)
        out.Push({ contentId: cid, number: num, name: name, overview: SafeStr(e.overview), still: still })
    end for
    return SortEpisodes(out)
end function

function Episodes_FromPortal(arr as Object) as Object
    out = []
    if arr = invalid then return out
    for each e in arr
        cid = SafeStr(e.contentId)
        if cid = "" then continue for
        num = AsInt(e.seriesNumber)
        if num <= 0 then num = AsInt(e.episodeNumber)
        if num <= 0 then num = out.Count() + 1
        name = SafeStr(e.name)
        if name = "" then name = "Episodio " + num.ToStr()
        out.Push({ contentId: cid, number: num, name: name, overview: "", still: "" })
    end for
    return SortEpisodes(out)
end function

function SortEpisodes(eps as Object) as Object
    if eps.Count() < 2 then return eps
    for i = 0 to eps.Count() - 2
        for j = 0 to eps.Count() - 2 - i
            if AsInt(eps[j].number) > AsInt(eps[j + 1].number)
                tmp = eps[j]
                eps[j] = eps[j + 1]
                eps[j + 1] = tmp
            end if
        end for
    end for
    return eps
end function

sub ShowEpisodes(eps as Object)
    m.episodesList = eps
    content = EmptyContent()
    for each e in eps
        item = CreateObject("roSGNode", "ContentNode")
        item.AddFields({
            episode: e
            title: e.name
            poster: e.still
            badge: e.number.ToStr()
        })
        content.AppendChild(item)
    end for
    m.episodes.content = content
    m.episodes.visible = true
    m.status.text = eps.Count().ToStr() + " capítulos"
    m.episodes.SetFocus(true)
end sub

sub OnAction()
    idx = m.actions.itemSelected
    if idx = 0
        PlayCurrent()
    else if idx = 1
        Lib_ToggleFavorite(m.film)
        BuildActions()
        m.actions.SetFocus(true)
    else if idx = 2
        Lib_ToggleWatchLater(m.film)
        BuildActions()
        m.actions.SetFocus(true)
    end if
end sub

sub PlayCurrent()
    if m.film = invalid then return
    if Film_IsAdult(m.film) and Pin_Has() and not m.pinChecked
        OpenPlayGate({ kind: "movie" })
        return
    end if
    if Film_IsSeries(m.film) and m.episodesList.Count() > 0
        chosen = m.episodesList[0]
        if m.film._resumeEp <> invalid
            for each e in m.episodesList
                if SafeStr(e.contentId) = SafeStr(m.film._resumeEp.contentId)
                    chosen = e
                    exit for
                end if
            end for
        end if
        for each e in m.episodesList
            rec = Lib_ProgressFor(m.film, e)
            if rec <> invalid and AsInt(rec.position) > 30
                chosen = e
                exit for
            end if
        end for
        PlayEpisode(chosen)
        return
    end if
    startPos = 0
    if m.film._resumePos <> invalid then startPos = AsInt(m.film._resumePos)
    m.top.payload = {
        kind: "vod"
        film: m.film
        contentId: Film_Cid(m.film)
        seriesContentId: ""
        title: m.film.title
        startPos: startPos
        columnId: 87026
    }
    m.top.action = "play"
end sub

sub OnEpisode()
    data = m.episodes.itemSelected
    idx = 0
    if Type(data) = "roAssociativeArray" then idx = AsInt(data.itemIndex) else idx = AsInt(data)
    if idx < 0 or idx >= m.episodesList.Count() then return
    PlayEpisode(m.episodesList[idx])
end sub

sub PlayEpisode(e as Object)
    if m.film = invalid or e = invalid then return
    if Film_IsAdult(m.film) and Pin_Has() and not m.pinChecked
        OpenPlayGate({ kind: "episode", episode: e })
        return
    end if
    startPos = 0
    rec = Lib_ProgressFor(m.film, e)
    if rec <> invalid then startPos = AsInt(rec.position)
    seriesCid = Film_Cid(m.film)
    if m.currentSeason <> invalid
        seriesCid = Film_Cid({ videoUrl: SafeStr(m.currentSeason.videoUrl), id: SafeStr(m.currentSeason.titleId) })
    end if
    m.top.payload = {
        kind: "vod"
        film: m.film
        contentId: e.contentId
        seriesContentId: seriesCid
        episode: e
        title: m.film.title + " · " + e.name
        startPos: startPos
        columnId: 87026
    }
    m.top.action = "play"
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "back"
        return true
    end if
    return false
end function


sub OpenPlayGate(pending as Object)
    dlg = CreateObject("roSGNode", "KeyboardDialog")
    dlg.title = "Contenido +18"
    dlg.text = ""
    dlg.buttons = ["Aceptar", "Cancelar"]
    m.pendingPlay = pending
    m.gateDialog = dlg
    dlg.ObserveField("wasClosed", "OnGateClosed")
    m.top.GetScene().dialog = dlg
    m.status.text = "Escribe tu PIN +18 para reproducir."
end sub

sub OnGateClosed()
    if m.gateDialog = invalid then return
    text = SafeStr(m.gateDialog.text).Trim()
    m.gateDialog.UnobserveField("wasClosed")
    m.gateDialog.close = true
    m.gateDialog = invalid
    if text = ""
        m.pendingPlay = invalid
        m.status.text = "Reproducción cancelada."
        return
    end if
    if not Pin_Check(text)
        remain = Pin_Fail()
        if remain > 0 then m.status.text = "PIN incorrecto. Espera " + remain.ToStr() + " segundos." else m.status.text = "PIN incorrecto."
        m.pendingPlay = invalid
        return
    end if
    Pin_ClearFail()
    m.pinChecked = true
    pending = m.pendingPlay
    m.pendingPlay = invalid
    if pending = invalid then return
    if SafeStr(pending.kind) = "movie"
        PlayCurrent()
    else if SafeStr(pending.kind) = "episode" and pending.episode <> invalid
        PlayEpisode(pending.episode)
    end if
end sub
