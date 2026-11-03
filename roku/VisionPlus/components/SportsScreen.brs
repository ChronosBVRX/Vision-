sub init()
    m.days = m.top.FindNode("days")
    m.grid = m.top.FindNode("grid")
    m.sub = m.top.FindNode("sub")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.optionsPanel = m.top.FindNode("optionsPanel")
    m.optionsTitle = m.top.FindNode("optionsTitle")
    m.options = m.top.FindNode("options")
    m.events = []
    m.optionsList = []
    m.task = invalid
    m.dayOffset = 0
    m.scope = "main"
    m.days.ObserveField("itemSelected", "OnDay")
    m.grid.ObserveField("itemSelected", "OnEvent")
    m.options.ObserveField("itemSelected", "OnOption")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    content = EmptyContent()
    for each title in ["Hoy", "Mañana", "+2 días", "+3 días"]
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: title })
        content.AppendChild(n)
    end for
    m.days.content = content
    m.days.SetFocus(true)
    m.optionsPanel.visible = false
    LoadDay(0)
end sub

sub OnDay()
    m.dayOffset = m.days.itemSelected
    LoadDay(m.dayOffset)
end sub

sub LoadDay(offset as Integer)
    m.loading.visible = true
    m.errorLabel.visible = false
    m.grid.content = EmptyContent()
    if m.task <> invalid then m.task.control = "STOP"
    iso = Sports_DayIso(offset)
    m.sub.text = "Agenda del " + iso + " · hora de México"
    m.scope = "main"
    m.task = NewApiTask(VPS_Url("/v1/gee38/events?day=" + iso + "&days=0&scope=main"), "GET", "")
    m.task.ObserveField("response", "OnEvents")
    m.task.ObserveField("error", "OnError")
    m.task.control = "RUN"
end sub

sub OnEvents()
    m.loading.visible = false
    m.events = ParseGeeEvents(m.task.response)
    if m.events.Count() = 0 and m.scope = "main"
        m.scope = "all"
        iso = Sports_DayIso(m.dayOffset)
        m.task = NewApiTask(VPS_Url("/v1/gee38/events?day=" + iso + "&days=0&scope=all"), "GET", "")
        m.task.ObserveField("response", "OnEvents")
        m.task.ObserveField("error", "OnError")
        m.task.control = "RUN"
        m.loading.visible = true
        return
    end if
    if m.events.Count() = 0
        m.errorLabel.text = "No hay eventos para este día. Prueba otra fecha."
        m.errorLabel.visible = true
        return
    end if
    m.grid.content = Events_ToContent(m.events)
    m.sub.text = m.events.Count().ToStr() + " eventos · elige uno para ver sus señales"
    m.grid.SetFocus(true)
end sub

sub OnError()
    m.loading.visible = false
    m.errorLabel.text = "No se pudo cargar la agenda: " + SafeStr(m.task.error)
    m.errorLabel.visible = true
end sub

sub OnEvent()
    data = m.grid.itemSelected
    idx = 0
    if Type(data) = "roAssociativeArray" then idx = AsInt(data.itemIndex) else idx = AsInt(data)
    if idx < 0 or idx >= m.events.Count() then return
    ShowOptions(m.events[idx])
end sub

sub ShowOptions(e as Object)
    m.optionsList = []
    content = EmptyContent()
    for each x in e.xtv
        n = CreateObject("roSGNode", "ContentNode")
        label = "TV · " + SafeStr(x.name)
        if x.recommended then label = label + "  ·  RECOMENDADO"
        n.AddFields({ Title: label })
        content.AppendChild(n)
        m.optionsList.Push({ type: "xtv", name: x.name, code: x.code })
    end for
    for each l in e.links
        if l.alive = false then continue for
        if LCase(SafeStr(l.type)) <> "hls" then continue for
        if SafeStr(l.drm) <> "" then continue for
        if SafeStr(l.url) = "" then continue for
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: "Señal · " + SafeStr(l.name) })
        content.AppendChild(n)
        m.optionsList.Push({ type: "url", name: l.name, url: l.url, headers: l.headers })
    end for
    if m.optionsList.Count() = 0
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: "Sin señales compatibles con Roku" })
        content.AppendChild(n)
    end if
    m.optionsTitle.text = e.title
    m.options.content = content
    m.optionsPanel.visible = true
    m.options.SetFocus(true)
end sub

sub OnOption()
    idx = m.options.itemSelected
    if idx < 0 or idx >= m.optionsList.Count() then return
    opt = m.optionsList[idx]
    if opt.type = "xtv"
        m.top.payload = {
            kind: "live"
            channelCode: opt.code
            columnId: 76182
            title: opt.name
            logo: ""
            number: ""
        }
        m.top.action = "play"
    else if opt.type = "url"
        m.top.payload = {
            kind: "url"
            url: opt.url
            headers: opt.headers
            title: opt.name
            live: true
        }
        m.top.action = "play"
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "back"
        if m.optionsPanel.visible
            m.optionsPanel.visible = false
            m.grid.SetFocus(true)
        else
            m.top.action = "home"
        end if
        return true
    end if
    return false
end function
