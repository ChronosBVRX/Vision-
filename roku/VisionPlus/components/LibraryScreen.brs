sub init()
    m.tabs = m.top.FindNode("tabs")
    m.grid = m.top.FindNode("grid")
    m.sub = m.top.FindNode("sub")
    m.emptyLabel = m.top.FindNode("emptyLabel")
    m.films = []
    m.tabs.ObserveField("itemSelected", "OnTab")
    m.grid.ObserveField("itemSelected", "OnFilm")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    content = EmptyContent()
    for each title in ["Continuar viendo", "Favoritos", "Ver más tarde", "Historial"]
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: title })
        content.AppendChild(n)
    end for
    m.tabs.content = content
    m.tabs.SetFocus(true)
    LoadTab(0)
end sub

sub OnTab()
    LoadTab(m.tabs.itemSelected)
end sub

sub LoadTab(idx as Integer)
    if idx = 0
        m.films = Lib_Continue()
        m.sub.text = "Retoma donde lo dejaste"
        m.emptyLabel.text = "Todavía no hay nada que continuar. Abre una película o serie y vuelve aquí."
    else if idx = 1
        m.films = Lib_Favorites()
        m.sub.text = "Títulos que guardaste"
        m.emptyLabel.text = "No tienes favoritos. En una ficha pulsa «Añadir a favoritos»."
    else if idx = 2
        m.films = Lib_WatchLater()
        m.sub.text = "Para ver después"
        m.emptyLabel.text = "No tienes títulos en «Ver más tarde»."
    else
        m.films = Lib_HistoryFilms()
        m.sub.text = "Lo último que viste en este perfil"
        m.emptyLabel.text = "El historial está vacío."
    end if
    m.grid.content = EmptyContent()
    if m.films.Count() = 0
        m.emptyLabel.visible = true
        m.tabs.SetFocus(true)
        return
    end if
    m.emptyLabel.visible = false
    m.grid.content = Grid_ToContent(m.films)
    m.grid.SetFocus(true)
end sub

sub OnFilm()
    data = m.grid.itemSelected
    idx = 0
    if Type(data) = "roAssociativeArray" then idx = AsInt(data.itemIndex) else idx = AsInt(data)
    if idx < 0 or idx >= m.films.Count() then return
    f = m.films[idx]
    m.top.payload = { film: f, from: "library" }
    m.top.action = "detail"
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function
