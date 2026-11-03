sub init()
    m.screens = {
        home: m.top.FindNode("homeScreen")
        section: m.top.FindNode("sectionScreen")
        live: m.top.FindNode("liveScreen")
        search: m.top.FindNode("searchScreen")
        detail: m.top.FindNode("detailScreen")
        player: m.top.FindNode("playerScreen")
        library: m.top.FindNode("libraryScreen")
        sports: m.top.FindNode("sportsScreen")
        settings: m.top.FindNode("settingsScreen")
        games: m.top.FindNode("gamesScreen")
    }
    for each name in m.screens
        m.screens[name].ObserveField("action", "OnAction")
    end for
    m.topBar = m.top.FindNode("topBar")
    m.topBar.ObserveField("action", "OnTopBarAction")
    m.current = "home"
    m.previous = "home"
    m.playerFrom = "home"
    ShowHome()
end sub

sub ShowOnly(name as String)
    for each key in m.screens
        m.screens[key].visible = (key = name)
    end for
    m.current = name
    m.topBar.visible = (name <> "detail" and name <> "player")
    m.topBar.active = ActiveFor(name)
    m.topBar.profile = Profile_Name(Profile_Active())
    screen = m.screens[name]
    screen.SetFocus(true)
end sub

sub ShowHome()
    ShowOnly("home")
    m.screens.home.load = m.screens.home.load + 1
    m.previous = "home"
end sub

sub ShowExisting(name as String)
    if name = "" then name = "home"
    if name = "detail"
        m.screens.detail.visible = true
        for each key in m.screens
            if key <> "detail" then m.screens[key].visible = false
        end for
        m.current = "detail"
        m.topBar.visible = false
        m.screens.detail.SetFocus(true)
        actions = m.screens.detail.FindNode("actions")
        if actions <> invalid then actions.SetFocus(true)
        return
    end if
    ShowOnly(name)
end sub

sub OnAction()
    ' Se busca la pantalla que dejó una acción pendiente.
    for each key in m.screens
        screen = m.screens[key]
        if screen.action <> ""
            HandleAction(key, screen, screen.action, screen.payload)
            screen.action = ""
        end if
    end for
end sub

sub HandleAction(from as String, screen as Object, action as String, payload as Dynamic)
    if action = "home"
        ShowHome()
    else if action = "section"
        if payload = invalid then return
        m.previous = m.current
        m.screens.section.sectionName = SafeStr(payload.name)
        m.screens.section.sectionTitle = SafeStr(payload.title)
        ShowOnly("section")
        m.screens.section.load = m.screens.section.load + 1
    else if action = "live"
        m.previous = m.current
        ShowOnly("live")
        m.screens.live.load = m.screens.live.load + 1
    else if action = "search"
        m.previous = m.current
        ShowOnly("search")
        m.screens.search.load = m.screens.search.load + 1
    else if action = "library"
        m.previous = m.current
        ShowOnly("library")
        m.screens.library.load = m.screens.library.load + 1
    else if action = "sports"
        m.previous = m.current
        ShowOnly("sports")
        m.screens.sports.load = m.screens.sports.load + 1
    else if action = "games"
        m.previous = m.current
        ShowOnly("games")
        m.screens.games.load = m.screens.games.load + 1
    else if action = "settings"
        m.previous = m.current
        ShowOnly("settings")
        m.screens.settings.load = m.screens.settings.load + 1
    else if action = "detail"
        if payload = invalid or payload.film = invalid then return
        m.previous = m.current
        m.screens.detail.film = payload.film
        m.screens.detail.from = SafeStr(payload.from)
        ShowOnly("detail")
        m.screens.detail.load = m.screens.detail.load + 1
    else if action = "play"
        if payload = invalid then return
        m.playerFrom = m.current
        m.screens.player.playItem = payload
        ShowOnly("player")
        m.screens.player.load = m.screens.player.load + 1
    else if action = "playerClosed"
        ShowExisting(m.playerFrom)
    else if action = "back"
        if m.current = "detail"
            ShowExisting(m.screens.detail.from)
        else
            ShowHome()
        end if
    else if action = "profileChanged"
        ShowHome()
    end if
end sub


function ActiveFor(name as String) as String
    if name = "home" then return "home"
    if name = "live" or name = "sports" then return "live"
    if name = "games" then return "games"
    if name = "library" then return "library"
    if name = "section"
        s = SafeStr(m.screens.section.sectionName)
        if s = "movies" then return "movies"
        if s = "series" or s = "anime" or s = "novelas" then return "series"
    end if
    return ""
end function

sub OnTopBarAction()
    action = SafeStr(m.topBar.action)
    payload = m.topBar.payload
    m.topBar.action = ""
    if action = "content"
        FocusContent()
    else if action = "home"
        ShowHome()
    else if action = "live"
        m.previous = m.current
        ShowOnly("live")
        m.screens.live.load = m.screens.live.load + 1
    else if action = "games"
        m.previous = m.current
        ShowOnly("games")
        m.screens.games.load = m.screens.games.load + 1
    else if action = "section"
        if payload = invalid then return
        m.previous = m.current
        m.screens.section.sectionName = SafeStr(payload.name)
        m.screens.section.sectionTitle = SafeStr(payload.title)
        ShowOnly("section")
        m.screens.section.load = m.screens.section.load + 1
    else if action = "library"
        m.previous = m.current
        ShowOnly("library")
        m.screens.library.load = m.screens.library.load + 1
    else if action = "search"
        m.previous = m.current
        ShowOnly("search")
        m.screens.search.load = m.screens.search.load + 1
    else if action = "settings"
        m.previous = m.current
        ShowOnly("settings")
        m.screens.settings.load = m.screens.settings.load + 1
    end if
end sub

sub FocusContent()
    if m.current = "home"
        b = m.screens.home.FindNode("btnPlay")
        if b <> invalid then b.SetFocus(true)
    else if m.current = "section"
        m.screens.section.FindNode("rows").SetFocus(true)
    else if m.current = "live"
        m.screens.live.FindNode("cats").SetFocus(true)
    else if m.current = "search"
        m.screens.search.FindNode("grid").SetFocus(true)
    else if m.current = "library"
        m.screens.library.FindNode("tabs").SetFocus(true)
    else if m.current = "sports"
        m.screens.sports.FindNode("days").SetFocus(true)
    else if m.current = "settings"
        m.screens.settings.FindNode("options").SetFocus(true)
    else if m.current = "games"
        m.screens.games.FindNode("grid").SetFocus(true)
    end if
end sub
