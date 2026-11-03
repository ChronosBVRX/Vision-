sub init()
    m.cats = m.top.FindNode("cats")
    m.grid = m.top.FindNode("grid")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.status = m.top.FindNode("status")
    m.catTask = invalid
    m.chTask = invalid
    m.epgTask = invalid
    m.categories = []
    m.channels = []
    m.epg = {}
    m.currentColumn = 0
    m.generation = 0
    m.cats.ObserveField("itemSelected", "OnCategory")
    m.grid.ObserveField("itemSelected", "OnChannel")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    m.loading.visible = true
    m.errorLabel.visible = false
    m.categories = []
    m.channels = []
    m.epg = {}
    m.currentColumn = 0
    m.generation = m.generation + 1
    if m.catTask <> invalid then m.catTask.control = "STOP"
    m.catTask = NewXuperTask("liveCategories", {})
    m.catTask.ObserveField("result", "OnCategories")
    m.catTask.ObserveField("error", "OnError")
    m.catTask.control = "RUN"
end sub

sub OnCategories()
    if m.catTask = invalid then return
    m.categories = ParseLiveCategories(m.catTask.result)
    if m.categories.Count() = 0
        LoadFallbackChannels()
        return
    end if
    m.cats.content = Categories_ToContent(m.categories)
    m.cats.SetFocus(true)
    m.currentColumn = m.categories[0].id
    m.status.text = m.categories.Count().ToStr() + " categorías · elige una"
    LoadChannels(m.currentColumn)
end sub

sub OnCategory()
    idx = m.cats.itemSelected
    if idx < 0 or idx >= m.categories.Count() then return
    m.currentColumn = m.categories[idx].id
    m.status.text = m.categories[idx].name
    LoadChannels(m.currentColumn)
end sub

sub LoadChannels(columnId as Integer)
    m.loading.visible = true
    m.errorLabel.visible = false
    m.generation = m.generation + 1
    gen = m.generation
    if m.chTask <> invalid then m.chTask.control = "STOP"
    m.chTask = NewXuperTask("liveChannels", { columnId: columnId })
    m.chTask.ObserveField("result", "OnChannels")
    m.chTask.ObserveField("error", "OnError")
    m.chTask.control = "RUN"
    m.pendingGen = gen
end sub

sub OnChannels()
    if m.chTask = invalid then return
    m.channels = ParseLiveChannels(m.chTask.result)
    m.loading.visible = false
    if m.channels.Count() = 0
        m.errorLabel.text = "El portal no devolvió canales para esta categoría."
        m.errorLabel.visible = true
        return
    end if
    m.epg = {}
    RenderChannels()
    m.status.text = m.channels.Count().ToStr() + " canales · " + m.categories.Count().ToStr() + " categorías"
    m.grid.SetFocus(true)
    LoadEpg()
end sub

sub RenderChannels()
    m.grid.content = Channels_ToContent(m.channels, m.epg)
end sub

sub LoadEpg()
    if m.epgTask <> invalid then m.epgTask.control = "STOP"
    list = []
    for i = 0 to m.channels.Count() - 1
        if i >= 120 then exit for
        list.Push({ code: m.channels[i].code, name: m.channels[i].name })
    end for
    if list.Count() = 0 then return
    body = FormatJson({ channels: list })
    m.epgTask = NewApiTask(VPS_Url("/v1/epg/bulk"), "POST", body)
    m.epgTask.ObserveField("response", "OnEpg")
    m.epgTask.control = "RUN"
end sub

sub OnEpg()
    if m.epgTask = invalid then return
    m.epg = ParseEpgBulk(m.epgTask.response)
    if m.epg.Count() > 0 then RenderChannels()
end sub

sub OnChannel()
    data = m.grid.itemSelected
    idx = 0
    if Type(data) = "roAssociativeArray" then
        idx = AsInt(data.itemIndex)
    else
        idx = AsInt(data)
    end if
    if idx < 0 or idx >= m.channels.Count() then return
    ch = m.channels[idx]
    m.top.payload = {
        kind: "live"
        channelCode: ch.code
        columnId: m.currentColumn
        title: ch.name
        logo: ch.logo
        number: Tern(ch.number > 0, ch.number.ToStr(), "")
        group: ch.group
        channels: m.channels
        channelIndex: idx
    }
    m.top.action = "play"
end sub

sub LoadFallbackChannels()
    m.status.text = "Portal lento · cargando lista de respaldo del VPS"
    m.catTask = NewApiTask(VPS_Url("/v1/index?limitFilms=120&since=0"), "GET", "")
    m.catTask.ObserveField("response", "OnFallback")
    m.catTask.ObserveField("error", "OnError")
    m.catTask.control = "RUN"
end sub

sub OnFallback()
    rows = ParseIndexRows(m.catTask.response)
    m.categories = [{ id: 76182, name: "Todos los canales", code: "" }]
    m.cats.content = Categories_ToContent(m.categories)
    m.channels = []
    for each row in rows
        if row.id = "tv-en-vivo"
            for each f in row.films
                m.channels.Push({
                    code: ChannelCodeFromRef(f.videoUrl)
                    name: f.title
                    logo: f.posterUrl
                    number: 0
                    group: "TV en vivo"
                    playUrl: ""
                })
            end for
        end if
    end for
    m.loading.visible = false
    if m.channels.Count() = 0
        m.errorLabel.text = "No se pudo cargar la TV en vivo. Revisa la conexión."
        m.errorLabel.visible = true
        return
    end if
    RenderChannels()
    m.grid.SetFocus(true)
end sub

sub OnError()
    m.loading.visible = false
    if m.catTask <> invalid and m.catTask.error <> ""
        m.errorLabel.text = "No se pudo abrir la TV en vivo: " + SafeStr(m.catTask.error)
        m.errorLabel.visible = true
    else if m.chTask <> invalid and m.chTask.error <> ""
        m.errorLabel.text = "No se pudo abrir la categoría: " + SafeStr(m.chTask.error)
        m.errorLabel.visible = true
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function
