sub init()
    m.grid = m.top.FindNode("grid")
    m.queryLabel = m.top.FindNode("queryLabel")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.task = invalid
    m.films = []
    m.grid.ObserveField("itemSelected", "OnFilm")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    m.grid.content = EmptyContent()
    m.queryLabel.text = ""
    m.errorLabel.visible = false
    OpenKeyboard()
end sub

sub OpenKeyboard()
    dlg = CreateObject("roSGNode", "KeyboardDialog")
    dlg.title = "Buscar en Vision+"
    dlg.text = ""
    dlg.buttons = ["Buscar", "Cancelar"]
    dlg.ObserveField("wasClosed", "OnKeyboardClosed")
    m.keyboard = dlg
    m.top.GetScene().dialog = dlg
end sub

sub OnKeyboardClosed()
    if m.keyboard = invalid then return
    text = SafeStr(m.keyboard.text).Trim()
    m.keyboard.UnobserveField("wasClosed")
    m.keyboard = invalid
    if text = ""
        m.top.action = "home"
        return
    end if
    m.queryLabel.text = "Resultados para: " + text
    Lib_SearchAdd(text)
    DoSearch(text)
end sub

sub DoSearch(text as String)
    if m.task <> invalid then m.task.control = "STOP"
    m.loading.visible = true
    m.errorLabel.visible = false
    m.task = NewApiTask(VPS_Url("/v1/search?q=" + EncodeUrl(text) + "&limit=60"), "GET", "")
    m.task.ObserveField("response", "OnResults")
    m.task.ObserveField("error", "OnError")
    m.task.control = "RUN"
end sub

sub OnResults()
    m.loading.visible = false
    m.films = ParseSearchFilms(m.task.response)
    if m.films.Count() = 0
        m.errorLabel.text = "No encontramos títulos con esa búsqueda. Prueba con otro nombre."
        m.errorLabel.visible = true
        return
    end if
    m.grid.content = Grid_ToContent(m.films)
    m.grid.SetFocus(true)
end sub

sub OnError()
    m.loading.visible = false
    m.errorLabel.text = "Error de búsqueda: " + SafeStr(m.task.error)
    m.errorLabel.visible = true
end sub

sub OnFilm()
    data = m.grid.itemSelected
    idx = 0
    if Type(data) = "roAssociativeArray" then idx = AsInt(data.itemIndex) else idx = AsInt(data)
    if idx < 0 or idx >= m.films.Count() then return
    m.top.payload = { film: m.films[idx], from: "search" }
    m.top.action = "detail"
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function
