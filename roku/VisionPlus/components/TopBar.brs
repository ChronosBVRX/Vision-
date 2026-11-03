' Barra superior de Vision+ TV: marca, pestañas, hora, perfil, búsqueda y ajustes.
' Es un solo nodo enfocable que mueve la selección con izquierda/derecha.

sub init()
    m.top.focusable = true
    m.top.width = 1920
    m.top.height = 124
    m.top.color = "0x0000008C"
    m.top.childRenderOrder = "renderFirst"
    m.tabHost = m.top.FindNode("tabHost")
    m.clock = m.top.FindNode("clock")
    m.profileBg = m.top.FindNode("profileBg")
    m.avatar = m.top.FindNode("avatar")
    m.avatarText = m.top.FindNode("avatarText")
    m.profileName = m.top.FindNode("profileName")
    m.searchBg = m.top.FindNode("searchBg")
    m.searchText = m.top.FindNode("searchText")
    m.settingsBg = m.top.FindNode("settingsBg")
    m.menuPanel = m.top.FindNode("menuPanel")
    m.menuTitle = m.top.FindNode("menuTitle")
    m.menuBgs = [m.top.FindNode("menuBg0"), m.top.FindNode("menuBg1"), m.top.FindNode("menuBg2")]
    m.menuLabels = [m.top.FindNode("menuLabel0"), m.top.FindNode("menuLabel1"), m.top.FindNode("menuLabel2")]
    m.menuOpen = false
    m.menuItems = []
    m.menuIdx = 0
    m.tabs = [
        { id: "home", label: "Inicio", x: 390, w: 130 }
        { id: "live", label: "En vivo", x: 530, w: 150 }
        { id: "games", label: "Juegos", x: 690, w: 140 }
        { id: "movies", label: "Películas", x: 840, w: 160 }
        { id: "series", label: "Series", x: 1010, w: 140 }
        { id: "library", label: "Mi lista", x: 1160, w: 150 }
    ]
    m.tabBgs = []
    m.tabLabels = []
    for i = 0 to m.tabs.Count() - 1
        t = m.tabs[i]
        bg = m.tabHost.CreateChild("Poster")
        bg.width = t.w
        bg.height = 56
        bg.translation = [t.x, 34]
        bg.loadDisplayMode = "scaleToFill"
        bg.visible = false
        m.tabBgs.Push(bg)
        lb = m.tabHost.CreateChild("Label")
        lb.text = t.label
        lb.translation = [t.x, 34]
        lb.width = t.w
        lb.height = 56
        lb.horizAlign = "center"
        lb.vertAlign = "center"
        lb.font = TbFont(24, true)
        lb.color = "0x9BA8BDFF"
        m.tabLabels.Push(lb)
    end for
    m.sel = 0
    m.activeId = "home"
    m.focused = false
    m.top.ObserveField("active", "OnActive")
    m.top.ObserveField("profile", "OnProfile")
    m.top.ObserveField("focusedChild", "OnFocusChanged")
    m.top.ObserveField("visible", "OnVisible")
    m.clockTimer = CreateObject("roSGNode", "Timer")
    m.clockTimer.duration = 20
    m.clockTimer.repeat = true
    m.clockTimer.ObserveField("fire", "OnClockTick")
    m.clockTimer.control = "start"
    RefreshProfile()
    UpdateClock()
    Render()
end sub

function TbFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:MediumBoldSystemFont" else f.uri = "font:MediumSystemFont"
    f.size = size
    return f
end function

sub OnVisible()
    if m.top.visible = true then RefreshProfile() : UpdateClock() : Render()
end sub

sub OnProfile()
    name = SafeStr(m.top.profile)
    if name <> ""
        m.profileName.text = name
        m.avatarText.text = Left(name, 1).Upper()
    end if
end sub

sub OnActive()
    CloseMenu()
    m.activeId = SafeStr(m.top.active)
    for i = 0 to m.tabs.Count() - 1
        if m.tabs[i].id = m.activeId then m.sel = i : exit for
    end for
    Render()
end sub

sub OnFocusChanged()
    m.focused = m.top.hasFocus()
    Render()
end sub

sub OnClockTick()
    UpdateClock()
end sub

sub UpdateClock()
    t = CreateObject("roDateTime")
    t.ToLocalTime()
    m.clock.text = Right("0" + t.GetHours().ToStr(), 2) + ":" + Right("0" + t.GetMinutes().ToStr(), 2)
end sub

sub RefreshProfile()
    name = Profile_Name(Profile_Active())
    m.profileName.text = name
    if name <> "" then m.avatarText.text = Left(name, 1).Upper()
end sub

sub Render()
    for i = 0 to m.tabs.Count() - 1
        active = (m.tabs[i].id = m.activeId)
        focused = (m.focused and m.sel = i)
        if focused and active
            m.tabBgs[i].uri = "pkg:/images/pill_focus_selected.png"
            m.tabBgs[i].visible = true
        else if focused
            m.tabBgs[i].uri = "pkg:/images/pill_focus.png"
            m.tabBgs[i].visible = true
        else if active
            m.tabBgs[i].uri = "pkg:/images/pill_selected.png"
            m.tabBgs[i].visible = true
        else
            m.tabBgs[i].visible = false
        end if
        if active or focused then
            m.tabLabels[i].color = "0xFFFFFFFF"
        else
            m.tabLabels[i].color = "0x9BA8BDFF"
        end if
    end for
    if m.focused and m.sel = 6
        m.profileBg.uri = "pkg:/images/pill_focus.png"
        m.profileName.color = "0xFFFFFFFF"
    else
        m.profileBg.uri = "pkg:/images/pill_dark.png"
        m.profileName.color = "0xFFFFFFFF"
    end if
    if m.focused and m.sel = 7
        m.searchBg.uri = "pkg:/images/pill_focus.png"
    else
        m.searchBg.uri = "pkg:/images/pill_dark.png"
    end if
    if m.focused and m.sel = 8
        m.settingsBg.uri = "pkg:/images/settings_circle_focus.png"
    else
        m.settingsBg.uri = "pkg:/images/settings_circle.png"
    end if
end sub

sub OpenMenu(kind as String)
    m.menuKind = kind
    if kind = "live"
        m.menuTitle.text = "EN VIVO"
        m.menuItems = [
            { label: "TV en vivo", action: "live", payload: invalid }
            { label: "Deportes en vivo", action: "sports", payload: invalid }
        ]
    else if kind = "series"
        m.menuTitle.text = "SERIES"
        m.menuItems = [
            { label: "Series", action: "section", payload: { name: "series", title: "Series" } }
            { label: "Anime", action: "section", payload: { name: "anime", title: "Anime" } }
            { label: "Novelas", action: "section", payload: { name: "novelas", title: "Novelas" } }
        ]
    else
        m.menuTitle.text = "MI LISTA"
        m.menuItems = [
            { label: "Mi lista", action: "library", payload: invalid }
            { label: "Descargas (no disponible en Roku)", action: "", payload: invalid }
        ]
    end if
    m.menuIdx = 0
    m.menuOpen = true
    m.menuPanel.visible = true
    x = 360
    if m.sel < m.tabs.Count() then x = m.tabs[m.sel].x
    if x + 520 > 1896 then x = 1376
    m.menuPanel.translation = [x, 96]
    RenderMenu()
end sub

sub CloseMenu()
    m.menuOpen = false
    m.menuPanel.visible = false
end sub

sub RenderMenu()
    for i = 0 to 2
        if i < m.menuItems.Count()
            m.menuBgs[i].visible = true
            m.menuLabels[i].visible = true
            m.menuLabels[i].text = m.menuItems[i].label
            if i = m.menuIdx
                m.menuBgs[i].uri = "pkg:/images/pill_selected.png"
                m.menuLabels[i].color = "0xFFFFFFFF"
            else
                m.menuBgs[i].uri = "pkg:/images/pill_dark.png"
                m.menuLabels[i].color = "0x9BA8BDFF"
            end if
        else
            m.menuBgs[i].visible = false
            m.menuLabels[i].visible = false
        end if
    end for
end sub

sub SelectMenu()
    if m.menuIdx < 0 or m.menuIdx >= m.menuItems.Count() then return
    item = m.menuItems[m.menuIdx]
    CloseMenu()
    if SafeStr(item.action) = "" then return
    m.top.action = SafeStr(item.action)
    m.top.payload = item.payload
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if m.menuOpen
        if key = "up"
            if m.menuIdx > 0 then m.menuIdx = m.menuIdx - 1
            RenderMenu()
            return true
        else if key = "down"
            if m.menuIdx < m.menuItems.Count() - 1 then m.menuIdx = m.menuIdx + 1
            RenderMenu()
            return true
        else if key = "OK"
            SelectMenu()
            return true
        else if key = "back"
            CloseMenu()
            return true
        else if key = "left" or key = "right"
            CloseMenu()
            return true
        end if
    end if
    total = m.tabs.Count() + 3
    if key = "left"
        m.sel = (m.sel - 1 + total) mod total
        Render()
        return true
    else if key = "right"
        m.sel = (m.sel + 1) mod total
        Render()
        return true
    else if key = "down"
        m.top.action = "content"
        return true
    else if key = "OK"
        Activate()
        return true
    end if
    return false
end function

sub Activate()
    if m.sel < m.tabs.Count()
        id = m.tabs[m.sel].id
        if id = "home"
            m.top.action = "home"
        else if id = "live"
            OpenMenu("live")
        else if id = "games"
            m.top.action = "games"
        else if id = "movies"
            m.top.action = "section"
            m.top.payload = { name: "movies", title: "Películas" }
        else if id = "series"
            OpenMenu("series")
        else if id = "library"
            OpenMenu("library")
        end if
    else if m.sel = 6
        m.top.action = "settings"
        m.top.payload = { tab: "profiles" }
    else if m.sel = 7
        m.top.action = "search"
    else if m.sel = 8
        m.top.action = "settings"
    end if
end sub
