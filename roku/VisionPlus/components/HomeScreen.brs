sub init()
    m.rows = m.top.FindNode("rows")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.heroBackdrop = m.top.FindNode("heroBackdrop")
    m.heroTitle = m.top.FindNode("heroTitle")
    m.heroMeta = m.top.FindNode("heroMeta")
    m.heroText = m.top.FindNode("heroText")
    m.badge = m.top.FindNode("badge")
    m.badgeBg = m.top.FindNode("badgeBg")
    m.btnPlay = m.top.FindNode("btnPlay")
    m.btnDetails = m.top.FindNode("btnDetails")
    m.btnFav = m.top.FindNode("btnFav")
    m.heroTitle.font = HomeFont(64, true)
    m.heroMeta.font = HomeFont(22, true)
    m.heroText.font = HomeFont(24, false)
    m.badge.font = HomeFont(18, true)
    m.indexTask = invalid
    m.bannerTask = invalid
    m.trendTask = invalid
    m.indexRows = []
    m.trending = []
    m.heroItems = []
    m.heroIndex = 0
    m.heroFilm = invalid
    m.manualHero = false
    m.indexReady = false
    m.bannersReady = false
    m.rowsBuilt = false
    m.focusTimer = invalid
    m.rows.ObserveField("rowItemSelected", "OnRowSelected")
    m.rows.ObserveField("rowItemFocused", "OnRowFocused")
    m.btnPlay.ObserveField("action", "OnHeroPlay")
    m.btnDetails.ObserveField("action", "OnHeroDetails")
    m.btnFav.ObserveField("action", "OnHeroFav")
    m.top.ObserveField("load", "OnLoad")
    m.heroTimer = CreateObject("roSGNode", "Timer")
    m.heroTimer.duration = 9
    m.heroTimer.repeat = true
    m.heroTimer.ObserveField("fire", "OnHeroTick")
    m.heroTimer.control = "start"
end sub

function HomeFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function

sub OnLoad()
    m.loading.visible = true
    m.errorLabel.visible = false
    m.rowsBuilt = false
    m.indexReady = false
    m.bannersReady = false
    m.manualHero = false
    if m.indexTask <> invalid then m.indexTask.control = "STOP"
    m.indexTask = NewApiTask(VPS_Url("/v1/index?limitFilms=20&since=0"), "GET", "")
    m.indexTask.ObserveField("response", "OnIndex")
    m.indexTask.ObserveField("error", "OnIndexError")
    m.indexTask.control = "RUN"
    if m.bannerTask <> invalid then m.bannerTask.control = "STOP"
    m.bannerTask = NewApiTask(VPS_Url("/v1/banners"), "GET", "")
    m.bannerTask.ObserveField("response", "OnBanners")
    m.bannerTask.ObserveField("error", "OnBannersError")
    m.bannerTask.control = "RUN"
    if m.trendTask <> invalid then m.trendTask.control = "STOP"
    m.trendTask = NewApiTask(VPS_Url("/v1/trending?limit=20"), "GET", "")
    m.trendTask.ObserveField("response", "OnTrending")
    m.trendTask.ObserveField("error", "OnTrendingError")
    m.trendTask.control = "RUN"
end sub

sub OnIndex()
    if m.indexTask = invalid then return
    m.indexRows = ParseIndexRows(m.indexTask.response)
    m.indexReady = true
    m.loading.visible = false
    if m.indexRows.Count() = 0
        m.errorLabel.text = "Tu contenido no está disponible ahora mismo. Revisa la conexión e intenta de nuevo."
        m.errorLabel.visible = true
        return
    end if
    BuildRows()
    if m.heroItems.Count() = 0 then
        first = FirstFilm(m.indexRows)
        if first <> invalid then
            m.heroItems = [{ title: first.title, synopsis: first.synopsis, backdrop: first.posterUrl, poster: first.posterUrl, badge: "DESTACADO HOY", eyebrow: "PELÍCULA DESTACADA", meta: Film_Subtitle(first), film: first }]
            m.heroIndex = 0
            RenderHero()
        end if
    end if
    if m.heroItems.Count() > 0 then m.btnPlay.SetFocus(true) else m.rows.SetFocus(true)
end sub

sub OnIndexError()
    m.loading.visible = false
    m.errorLabel.text = "Error de red al cargar el catálogo: " + SafeStr(m.indexTask.error)
    m.errorLabel.visible = true
end sub

sub OnBanners()
    if m.bannerTask = invalid then return
    m.heroItems = ParseBanners(m.bannerTask.response)
    m.bannersReady = true
    if m.heroItems.Count() > 0
        if m.heroIndex >= m.heroItems.Count() then m.heroIndex = 0
        RenderHero()
    else if m.indexReady
        first = FirstFilm(m.indexRows)
        if first <> invalid then
            m.heroItems = [{ title: first.title, synopsis: first.synopsis, backdrop: first.posterUrl, poster: first.posterUrl, badge: "DESTACADO HOY", eyebrow: "PELÍCULA DESTACADA", meta: Film_Subtitle(first), film: first }]
            RenderHero()
        end if
    end if
end sub

sub OnBannersError()
    if m.indexReady and m.heroItems.Count() = 0
        first = FirstFilm(m.indexRows)
        if first <> invalid then
            m.heroItems = [{ title: first.title, synopsis: first.synopsis, backdrop: first.posterUrl, poster: first.posterUrl, badge: "DESTACADO HOY", eyebrow: "PELÍCULA DESTACADA", meta: Film_Subtitle(first), film: first }]
            RenderHero()
        end if
    end if
end sub

sub OnTrending()
    if m.trendTask = invalid then return
    m.trending = ParseSearchFilms(m.trendTask.response)
    if m.indexReady and not (m.rowsBuilt and m.rows.hasFocus()) then BuildRows()
end sub

sub OnTrendingError()
    ' La fila semanal es opcional.
end sub

sub BuildRows()
    if not m.indexReady then return
    rows = BuildHomeRows(m.indexRows, m.trending)
    if rows.Count() = 0 then return
    m.rows.content = Rows_ToContent(rows)
    sizes = []
    heights = []
    labels = []
    spacings = []
    for i = 0 to rows.Count() - 1
        sizes.Push([296, 510])
        heights.Push(560)
        labels.Push(true)
        spacings.Push([16, 0])
    end for
    m.rows.rowItemSize = sizes
    m.rows.rowHeights = heights
    m.rows.showRowLabel = labels
    m.rows.rowItemSpacing = spacings
    m.rows.rowLabelColor = "0xF4F7FCFF"
    m.rows.rowLabelFont = HomeFont(28, true)
    m.rowsBuilt = true
end sub

function BuildHomeRows(rows as Object, trending as Object) as Object
    out = []
    if Profile_IsKids()
        wanted = ["xtv-kids-destacados", "xtv-kids-all", "genero-infantil", "genero-familia", "genero-animacion", "xtv-anime-destacados", "tv-en-vivo"]
    else
        wanted = ["genero-accion", "genero-comedia", "genero-suspenso", "genero-drama", "genero-terror", "genero-crimen", "genero-animacion", "genero-romance", "genero-ciencia-ficcion", "tv-en-vivo"]
    end if
    cont = Lib_Continue()
    if cont.Count() > 0 then out.Push({ id: "continue", title: "Seguir viendo", films: cont, total: cont.Count() })
    top = FindRow(rows, "tendencias")
    if top <> invalid and top.films.Count() > 0
        out.Push({ id: "top10", title: "Top 10 en Vision+ Hoy", films: TopN(top.films, 10), total: top.total })
    end if
    pre = FindRow(rows, "estrenos")
    if pre <> invalid and pre.films.Count() > 0
        out.Push({ id: "premieres", title: "Novedades y Estrenos", films: pre.films, total: pre.total })
    end if
    for each id in ["tema-dia-de-muertos", "tema-para-reir", "tema-tendencias-mundo"]
        r = FindRow(rows, id)
        if r <> invalid then out.Push(r)
    end for
    if trending <> invalid and trending.Count() > 0
        out.Push({ id: "trending", title: "Populares de la semana", films: trending, total: trending.Count() })
    end if
    for each id in wanted
        r = FindRow(rows, id)
        if r <> invalid then out.Push(r)
    end for
    return out
end function

function FindRow(rows as Object, id as String) as Dynamic
    for each r in rows
        if r.id = id then return r
    end for
    return invalid
end function

function FirstFilm(rows as Object) as Dynamic
    if rows = invalid then return invalid
    for each r in rows
        if r.films <> invalid and r.films.Count() > 0 then return r.films[0]
    end for
    return invalid
end function

sub OnHeroTick()
    if m.manualHero then return
    if m.heroItems.Count() <= 1 then return
    m.heroIndex = (m.heroIndex + 1) mod m.heroItems.Count()
    RenderHero()
end sub

sub RenderHero()
    if m.heroItems.Count() = 0 then return
    if m.heroIndex < 0 or m.heroIndex >= m.heroItems.Count() then m.heroIndex = 0
    item = m.heroItems[m.heroIndex]
    m.heroFilm = item.film
    m.heroTitle.text = SafeStr(item.title)
    m.heroMeta.text = SafeStr(item.meta)
    m.heroText.text = SafeStr(item.synopsis)
    badge = SafeStr(item.eyebrow)
    if SafeStr(item.badge) <> "" then
        if badge <> "" then badge = badge + " · " + SafeStr(item.badge) else badge = SafeStr(item.badge)
    end if
    m.badge.text = UCase(badge)
    m.badgeBg.visible = (badge <> "")
    poster = SafeStr(item.poster)
    if poster = "" then poster = "pkg:/images/placeholder_wide.png"
    m.heroBackdrop.failedBitmapUri = poster
    if SafeStr(item.backdrop) <> ""
        m.heroBackdrop.uri = SafeStr(item.backdrop)
    else
        m.heroBackdrop.uri = poster
    end if
    UpdateFavButton(m.heroFilm)
end sub

sub OnRowFocused()
    data = m.rows.rowItemFocused
    if data = invalid or data.Count() < 2 then return
    r = AsInt(data[0])
    i = AsInt(data[1])
    if r < 0 or r >= m.rows.content.GetChildCount() then return
    row = m.rows.content.GetChild(r)
    if row = invalid or i < 0 or i >= row.GetChildCount() then return
    item = row.GetChild(i)
    if item = invalid then return
    film = item.film
    if film = invalid then return
    m.manualHero = true
    if m.focusTimer <> invalid then m.focusTimer.control = "stop"
    m.focusTimer = CreateObject("roSGNode", "Timer")
    m.focusTimer.duration = 0.18
    m.focusTimer.repeat = false
    m.focusTimer.ObserveField("fire", "OnFocusSettled")
    m.focusTimer.control = "start"
    m.pendingFocusFilm = film
end sub

sub OnFocusSettled()
    film = m.pendingFocusFilm
    if film = invalid then return
    m.heroFilm = film
    m.heroTitle.text = film.title
    m.heroMeta.text = Film_HeroMeta(film)
    m.heroText.text = film.synopsis
    m.badge.text = UCase(Tern(Film_IsSeries(film), "SERIE", Tern(Film_IsLive(film), "TV EN VIVO", "PELÍCULA")))
    m.badgeBg.visible = true
    poster = Tern(film.posterUrl <> "", film.posterUrl, "pkg:/images/placeholder_wide.png")
    m.heroBackdrop.failedBitmapUri = poster
    m.heroBackdrop.uri = poster
    UpdateFavButton(film)
end sub

sub OnRowSelected()
    data = m.rows.rowItemSelected
    if data = invalid or data.Count() < 2 then return
    r = AsInt(data[0])
    i = AsInt(data[1])
    if r < 0 or r >= m.rows.content.GetChildCount() then return
    row = m.rows.content.GetChild(r)
    if row = invalid or i < 0 or i >= row.GetChildCount() then return
    item = row.GetChild(i)
    if item = invalid or item.film = invalid then return
    OpenFilm(item.film)
end sub

sub OpenFilm(film as Object)
    if Film_IsLive(film)
        m.top.payload = {
            kind: "live"
            channelCode: ChannelCodeFromRef(SafeStr(film.videoUrl))
            columnId: 76182
            title: film.title
            logo: film.posterUrl
            number: ""
        }
        m.top.action = "play"
        return
    end if
    m.top.payload = { film: film, from: "home" }
    m.top.action = "detail"
end sub

sub OnHeroPlay()
    if SafeStr(m.btnPlay.action) <> "selected" then return
    m.btnPlay.action = ""
    if m.heroFilm = invalid then return
    if Film_IsLive(m.heroFilm)
        OpenFilm(m.heroFilm)
        return
    end if
    if Film_IsSeries(m.heroFilm)
        m.top.payload = { film: m.heroFilm, from: "home" }
        m.top.action = "detail"
        return
    end if
    m.top.payload = {
        kind: "vod"
        film: m.heroFilm
        contentId: Film_Cid(m.heroFilm)
        seriesContentId: ""
        title: m.heroFilm.title
        startPos: 0
        columnId: 87026
    }
    m.top.action = "play"
end sub

sub OnHeroDetails()
    if SafeStr(m.btnDetails.action) <> "selected" then return
    m.btnDetails.action = ""
    if m.heroFilm = invalid then return
    m.top.payload = { film: m.heroFilm, from: "home" }
    m.top.action = "detail"
end sub

sub OnHeroFav()
    if SafeStr(m.btnFav.action) <> "selected" then return
    m.btnFav.action = ""
    if m.heroFilm = invalid then return
    Lib_ToggleFavorite(m.heroFilm)
    UpdateFavButton(m.heroFilm)
end sub

sub UpdateFavButton(film as Object)
    if film <> invalid and Lib_IsFavorite(film)
        m.btnFav.label = "En Favoritos"
        m.btnFav.icon = "pkg:/images/icon_heart_filled.png"
    else
        m.btnFav.label = "Favorito"
        m.btnFav.icon = "pkg:/images/icon_heart.png"
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function


function TopN(films as Object, n as Integer) as Object
    out = []
    for each f in films
        if out.Count() >= n then exit for
        out.Push(f)
    end for
    return out
end function
