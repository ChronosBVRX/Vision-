sub init()
    m.rows = m.top.FindNode("rows")
    m.title = m.top.FindNode("screenTitle")
    m.sub = m.top.FindNode("screenSub")
    m.loading = m.top.FindNode("loading")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.task = invalid
    m.dataRows = []
    m.isKids = false
    m.rows.ObserveField("rowItemSelected", "OnRowSelected")
    m.rows.ObserveField("rowItemFocused", "OnRowFocused")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    name = m.top.sectionName
    if name = "" then return
    if m.top.sectionTitle <> "" then m.title.text = m.top.sectionTitle
    m.loading.visible = true
    m.errorLabel.visible = false
    m.sub.text = "Catálogo Vision+"
    m.isKids = (name = "kids")
    if m.task <> invalid then m.task.control = "STOP"
    if m.isKids
        m.task = NewApiTask(VPS_Url("/v1/index?limitFilms=20&since=0"), "GET", "")
    else
        m.task = NewApiTask(VPS_Url("/v1/section/" + EncodeUrl(name) + "?limit=40"), "GET", "")
    end if
    m.task.ObserveField("response", "OnRows")
    m.task.ObserveField("error", "OnError")
    m.task.control = "RUN"
end sub

sub OnRows()
    if m.isKids
        rows = KidsRows(ParseIndexRows(m.task.response))
    else
        rows = ParseSectionRows(m.task.response)
    end if
    m.loading.visible = false
    if rows.Count() = 0
        m.errorLabel.text = "No llegó contenido para esta sección. Inténtalo de nuevo."
        m.errorLabel.visible = true
        return
    end if
    m.dataRows = rows
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
    m.rows.rowLabelFont = RowLabelFont()
    m.rows.SetFocus(true)
end sub

function KidsRows(rows as Object) as Object
    out = []
    for each row in rows
        id = SafeStr(row.id)
        if Left(id, 9) = "xtv-kids-" or Left(id, 11) = "magis-kids-" or id = "genero-infantil" or id = "genero-familia" or id = "genero-animacion"
            out.Push(row)
        end if
    end for
    if out.Count() = 0 then return rows
    return out
end function

sub OnError()
    m.loading.visible = false
    m.errorLabel.text = "Error de red: " + SafeStr(m.task.error)
    m.errorLabel.visible = true
end sub

sub OnRowSelected()
    data = m.rows.rowItemSelected
    if data = invalid then return
    if data.Count() < 2 then return
    r = AsInt(data[0])
    i = AsInt(data[1])
    if r < 0 or r >= m.dataRows.Count() then return
    films = m.dataRows[r].films
    if i < 0 or i >= films.Count() then return
    m.top.payload = { film: films[i], from: "section" }
    m.top.action = "detail"
end sub

sub OnRowFocused()
    data = m.rows.rowItemFocused
    if data = invalid then return
    if data.Count() < 2 then return
    r = AsInt(data[0])
    i = AsInt(data[1])
    if r < 0 or r >= m.dataRows.Count() then return
    row = m.dataRows[r]
    m.sub.text = row.title + "  ·  " + row.films.Count().ToStr() + " títulos"
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function
