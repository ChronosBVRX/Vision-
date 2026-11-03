sub init()
    m.grid = m.top.FindNode("grid")
    m.detail = m.top.FindNode("detail")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.games = []
    m.task = invalid
    m.grid.ObserveField("itemSelected", "OnGame")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    m.loading.visible = true
    m.errorLabel.visible = false
    m.detail.text = "Selecciona un juego para ver cómo se juega."
    if m.task <> invalid then m.task.control = "STOP"
    m.task = NewApiTask(VPS_Url("/v1/game/catalog"), "GET", "")
    m.task.ObserveField("response", "OnGames")
    m.task.ObserveField("error", "OnError")
    m.task.control = "RUN"
end sub

sub OnGames()
    if m.task = invalid then return
    root = ParseJsonSafe(m.task.response)
    m.games = []
    if root <> invalid and root.games <> invalid then
        for each g in root.games
            accent = SafeStr(g.accent)
            if Left(accent, 1) = "#" then accent = "0x" + Mid(accent, 2) + "FF"
            if accent = "" then accent = "0x2D7DF6FF"
            m.games.Push({
                id: SafeStr(g.id)
                title: SafeStr(g.title)
                tagline: SafeStr(g.tagline)
                description: SafeStr(g.description)
                minPlayers: AsInt(g.minPlayers)
                maxPlayers: AsInt(g.maxPlayers)
                approxMinutes: AsInt(g.approxMinutes)
                accent: accent
            })
        end for
    end if
    m.loading.visible = false
    if m.games.Count() = 0
        m.errorLabel.text = "Vision+ Play no respondió. Inténtalo más tarde."
        m.errorLabel.visible = true
        return
    end if
    content = EmptyContent()
    for each g in m.games
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ game: g, title: g.title })
        content.AppendChild(n)
    end for
    m.grid.content = content
    m.grid.SetFocus(true)
end sub

sub OnGame()
    data = m.grid.itemSelected
    idx = AsInt(data)
    if Type(data) = "roAssociativeArray" then idx = AsInt(data.itemIndex)
    if idx < 0 or idx >= m.games.Count() then return
    g = m.games[idx]
    m.detail.text = g.description + Chr(10) + Chr(10) + "Abre Vision+ en tu teléfono para crear la sala y unir a los jugadores."
end sub

sub OnError()
    m.loading.visible = false
    m.errorLabel.text = "No se pudo cargar Vision+ Play: " + SafeStr(m.task.error)
    m.errorLabel.visible = true
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function
