sub init()
    m.video = m.top.FindNode("video")
    m.osd = m.top.FindNode("osd")
    m.title = m.top.FindNode("title")
    m.stateLabel = m.top.FindNode("stateLabel")
    m.hint = m.top.FindNode("hint")
    m.progressBg = m.top.FindNode("progressBg")
    m.progressFill = m.top.FindNode("progressFill")
    m.timeLabel = m.top.FindNode("timeLabel")
    m.subGroup = m.top.FindNode("subGroup")
    m.subLabel = m.top.FindNode("subLabel")
    m.subShadow = m.top.FindNode("subShadow")
    m.spinner = m.top.FindNode("spinner")
    m.errorLabel = m.top.FindNode("errorLabel")
    m.status = m.top.FindNode("status")
    m.video.ObserveField("state", "OnVideoState")
    m.video.ObserveField("position", "OnPosition")
    m.top.ObserveField("load", "OnPlayItem")
    m.xTask = invalid
    m.proxyTask = invalid
    m.srtTask = invalid
    m.variants = []
    m.variantIdx = 0
    m.subs = []
    m.subCues = []
    m.subIdx = -1
    m.subOffset = 0.0
    m.live = false
    m.pendingSeek = 0
    m.didSeek = false
    m.lastSave = 0
    m.duration = 0
    m.film = invalid
    m.episode = invalid
    m.osdTimer = invalid
    m.ended = false
end sub

sub OnPlayItem()
    item = m.top.playItem
    if item = invalid then return
    StopPlayback()
    m.live = AsBool(item.live) or SafeStr(item.kind) = "live"
    if SafeStr(item.kind) = "url"
        m.live = AsBool(item.live)
        m.film = invalid
        m.episode = invalid
        m.title.text = SafeStr(item.title)
        m.spinner.visible = true
        m.status.text = "Abriendo señal..."
        StartUrl(SafeStr(item.url), item.headers, m.live, Tern(m.live, "hls", "hls"))
        return
    end if
    m.film = item.film
    m.episode = item.episode
    m.channelList = Tern(item.channels = invalid, [], item.channels)
    m.channelIdx = AsInt(item.channelIndex)
    m.columnId = Tern(AsInt(item.columnId) > 0, AsInt(item.columnId), 76182)
    m.pendingSeek = AsInt(item.startPos)
    m.didSeek = false
    m.ended = false
    m.title.text = SafeStr(item.title)
    if m.title.text = "" then m.title.text = "Vision+"
    m.stateLabel.text = Tern(m.live, "EN VIVO", "CARGANDO")
    m.spinner.visible = true
    m.errorLabel.visible = false
    m.progressBg.visible = not m.live
    m.progressFill.visible = not m.live
    m.status.text = Tern(m.live, "Conectando al canal...", "Preparando la reproducción...")
    m.subCues = []
    m.subs = []
    m.subIdx = -1
    m.subGroup.visible = false
    m.video.control = "stop"
    m.video.content = invalid
    m.top.SetFocus(true)
    ShowOsd()
    if m.live
        ResolveLive(item)
    else
        ResolveVod(item)
    end if
end sub

sub ResolveLive(item as Object)
    if m.xTask <> invalid then m.xTask.control = "STOP"
    m.xTask = NewXuperTask("resolveLive", { channelCode: SafeStr(item.channelCode), columnId: AsInt(item.columnId) })
    m.xTask.ObserveField("result", "OnResolvedLive")
    m.xTask.ObserveField("error", "OnResolveError")
    m.xTask.control = "RUN"
end sub

sub OnResolvedLive()
    if m.xTask = invalid then return
    res = ParseJsonSafe(m.xTask.result)
    if res = invalid or SafeStr(res.url) = ""
        ShowError("El canal no devolvió una dirección reproducible.")
        return
    end if
    m.title.text = Tern(SafeStr(res.title) <> "", SafeStr(res.title), m.title.text)
    m.status.text = "Abriendo señal en vivo..."
    StartProxy(res.url, res.headers)
end sub

sub StartProxy(srcUrl as String, headers as Object)
    if m.proxyTask <> invalid then m.proxyTask.control = "STOP"
    m.proxyTask = CreateObject("roSGNode", "ManifestProxy")
    m.proxyTask.srcUrl = srcUrl
    m.proxyTask.headers = headers
    m.proxyTask.ObserveField("ready", "OnProxyReady")
    m.proxyTask.ObserveField("error", "OnProxyError")
    m.proxyTask.control = "RUN"
    m.directUrl = srcUrl
    m.directHeaders = headers
end sub

sub OnProxyReady()
    if m.proxyTask = invalid or m.proxyTask.ready <> true then return
    StartUrl("http://127.0.0.1:8888/live.m3u8", m.directHeaders, true, "hls")
end sub

sub OnProxyError()
    ' Si el socket local falla, se intenta la señal directa.
    StartUrl(m.directUrl, m.directHeaders, true, "hls")
end sub

sub ResolveVod(item as Object)
    if m.xTask <> invalid then m.xTask.control = "STOP"
    m.xTask = NewXuperTask("resolveVod", {
        contentId: SafeStr(item.contentId)
        seriesContentId: SafeStr(item.seriesContentId)
        columnId: Tern(AsInt(item.columnId) > 0, AsInt(item.columnId), 0)
    })
    m.xTask.ObserveField("result", "OnResolvedVod")
    m.xTask.ObserveField("error", "OnResolveError")
    m.xTask.control = "RUN"
end sub

sub OnResolvedVod()
    if m.xTask = invalid then return
    res = ParseJsonSafe(m.xTask.result)
    if res = invalid or res.variants = invalid or res.variants.Count() = 0
        ShowError("No hay una pista compatible para este título.")
        return
    end if
    m.variants = res.variants
    m.variantIdx = 0
    m.subs = Tern(res.subs = invalid, [], res.subs)
    m.duration = AsInt(res.duration)
    if m.duration <= 0 and m.film <> invalid and m.film.durationMin > 0 then m.duration = m.film.durationMin * 60
    StartVariant()
end sub

sub StartVariant()
    if m.variantIdx < 0 or m.variantIdx >= m.variants.Count()
        ShowError("Se agotaron las pistas disponibles.")
        return
    end if
    v = m.variants[m.variantIdx]
    m.currentVariant = v
    m.didSeek = false
    m.status.text = "Pista " + SafeStr(v.codec) + " " + SafeStr(v.quality)
    StartUrl(v.url, v.headers, false, StreamFormat(v.format))
end sub

function StreamFormat(raw as String) as String
    f = LCase(raw)
    if f = "ts" or f = "mp4" or f = "mkv" or f = "hls" then return f
    return "hls"
end function

sub StartUrl(url as String, headers as Object, live as Boolean, fmt as String)
    if url = "" then
        ShowError("La dirección de reproducción llegó vacía.")
        return
    end if
    if headers <> invalid
        agent = CreateObject("roHttpAgent")
        agent.SetHeaders(headers)
        m.video.SetHttpAgent(agent)
    end if
    content = CreateObject("roSGNode", "ContentNode")
    content.url = url
    content.streamformat = fmt
    content.live = live
    if not live
        if m.duration > 0 then content.length = m.duration
    end if
    m.video.content = content
    m.video.control = "play"
    m.spinner.visible = true
    m.errorLabel.visible = false
    m.stateLabel.text = Tern(live, "EN VIVO", "CARGANDO")
    ShowOsd()
end sub

sub OnVideoState()
    state = SafeStr(m.video.state)
    m.top.state = state
    if state = "playing"
        m.spinner.visible = false
        m.errorLabel.visible = false
        m.stateLabel.text = Tern(m.live, "EN VIVO", "REPRODUCIENDO")
        if m.pendingSeek > 0 and not m.didSeek
            m.didSeek = true
            m.video.seek = m.pendingSeek
        end if
    else if state = "buffering"
        m.spinner.visible = true
        m.stateLabel.text = "CARGANDO"
    else if state = "paused"
        m.stateLabel.text = "PAUSA"
        ShowOsd()
    else if state = "finished"
        SaveProgress(true)
        m.stateLabel.text = "FIN"
        m.spinner.visible = false
    else if state = "error"
        m.spinner.visible = false
        if not m.live and m.variantIdx + 1 < m.variants.Count()
            m.variantIdx = m.variantIdx + 1
            StartVariant()
        else
            ShowError("Error de reproducción: " + SafeStr(m.video.errorMsg))
        end if
    end if
end sub

sub OnPosition()
    posn = AsInt(m.video.position)
    if m.live then
        m.timeLabel.text = "EN VIVO"
    else
        dur = m.duration
        if m.video.duration <> invalid and AsInt(m.video.duration) > 0 then dur = AsInt(m.video.duration)
        if dur > 0
            frac = posn / dur
            if frac < 0 then frac = 0
            if frac > 1 then frac = 1
            m.progressFill.width = Int(1800 * frac)
            m.timeLabel.text = FmtTime(posn) + " / " + FmtTime(dur)
        end if
    end if
    if m.subIdx >= 0 and m.subCues.Count() > 0
        txt = Srt_TextAt(m.subCues, posn, m.subOffset)
        m.subLabel.text = txt
        m.subShadow.text = txt
        m.subGroup.visible = (txt <> "")
    end if
    if not m.live and NowSeconds() - m.lastSave >= 12
        SaveProgress(false)
    end if
end sub

sub Zap(delta as Integer)
    if m.channelList = invalid or m.channelList.Count() = 0 then return
    idx = m.channelIdx + delta
    if idx < 0 then idx = m.channelList.Count() - 1
    if idx >= m.channelList.Count() then idx = 0
    m.channelIdx = idx
    ch = m.channelList[idx]
    m.live = true
    m.film = invalid
    m.episode = invalid
    m.title.text = SafeStr(ch.name)
    m.stateLabel.text = "EN VIVO"
    m.status.text = "Canal " + SafeStr(ch.number)
    m.spinner.visible = true
    m.errorLabel.visible = false
    if m.proxyTask <> invalid then m.proxyTask.control = "STOP"
    m.proxyTask = invalid
    m.video.control = "stop"
    m.video.content = invalid
    ResolveLive({ channelCode: ch.code, columnId: m.columnId, title: ch.name })
end sub

sub SaveProgress(finished as Boolean)
    if m.film = invalid then return
    posn = AsInt(m.video.position)
    dur = m.duration
    if m.video.duration <> invalid and AsInt(m.video.duration) > 0 then dur = AsInt(m.video.duration)
    if finished then posn = dur
    if posn > 0 or finished
        Lib_AddHistory(m.film, posn, dur, m.episode)
        m.lastSave = NowSeconds()
    end if
end sub

sub ShowError(text as String)
    m.errorLabel.text = text
    m.errorLabel.visible = true
    m.spinner.visible = false
    m.stateLabel.text = "ERROR"
    m.status.text = "Atrás para volver"
end sub

sub OnResolveError()
    err = ""
    if m.xTask <> invalid then err = SafeStr(m.xTask.error)
    if Instr(1, err, "codec_no_compatible") > 0
        ShowError("Este Roku no puede decodificar la pista que ofrece el portal (H.265/HEVC). Prueba otro título o usa un Roku 4K compatible.")
    else
        ShowError("No se pudo resolver la reproducción: " + err)
    end if
end sub

sub ShowOsd()
    m.osd.visible = true
    if m.osdTimer <> invalid then m.osdTimer.control = "stop"
    m.osdTimer = CreateObject("roSGNode", "Timer")
    m.osdTimer.duration = 5
    m.osdTimer.repeat = false
    m.osdTimer.ObserveField("fire", "HideOsd")
    m.osdTimer.control = "start"
end sub

sub HideOsd()
    m.osd.visible = false
end sub

sub CycleSub()
    if m.subs.Count() = 0
        m.status.text = "Este título no tiene subtítulos externos."
        return
    end if
    m.subIdx = m.subIdx + 1
    if m.subIdx >= m.subs.Count()
        m.subIdx = -1
        m.subCues = []
        m.subGroup.visible = false
        m.status.text = "Subtítulos: Off"
        return
    end if
    s = m.subs[m.subIdx]
    m.status.text = "Subtítulos: " + SafeStr(s.lang)
    if m.srtTask <> invalid then m.srtTask.control = "STOP"
    m.srtTask = CreateObject("roSGNode", "SrtTask")
    m.srtTask.url = SafeStr(s.url)
    m.srtTask.ObserveField("cues", "OnSubCues")
    m.srtTask.control = "RUN"
end sub

sub OnSubCues()
    if m.srtTask = invalid then return
    m.subCues = m.srtTask.cues
    if m.subCues = invalid then m.subCues = []
end sub

function FmtTime(seconds as Integer) as String
    if seconds < 0 then seconds = 0
    h = seconds \ 3600
    mn = (seconds mod 3600) \ 60
    s = seconds mod 60
    if h > 0 then return h.ToStr() + ":" + Right("0" + mn.ToStr(), 2) + ":" + Right("0" + s.ToStr(), 2)
    return mn.ToStr() + ":" + Right("0" + s.ToStr(), 2)
end function

sub StopPlayback()
    SaveProgress(false)
    if m.xTask <> invalid then m.xTask.control = "STOP"
    if m.proxyTask <> invalid then m.proxyTask.control = "STOP"
    if m.srtTask <> invalid then m.srtTask.control = "STOP"
    m.xTask = invalid
    m.proxyTask = invalid
    m.srtTask = invalid
    m.video.control = "stop"
    m.video.content = invalid
    m.spinner.visible = false
    m.subGroup.visible = false
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "back"
        StopPlayback()
        m.top.action = "playerClosed"
        return true
    else if key = "OK"
        if m.video.state = "paused"
            m.video.control = "resume"
            m.stateLabel.text = Tern(m.live, "EN VIVO", "REPRODUCIENDO")
        else
            m.video.control = "pause"
            m.stateLabel.text = "PAUSA"
        end if
        ShowOsd()
        return true
    else if key = "left" and not m.live
        seekPos = AsInt(m.video.position) - 10
        if seekPos < 0 then seekPos = 0
        m.video.seek = seekPos
        ShowOsd()
        return true
    else if key = "right" and not m.live
        m.video.seek = AsInt(m.video.position) + 10
        ShowOsd()
        return true
    else if key = "up"
        if m.live and m.channelList <> invalid and m.channelList.Count() > 1
            Zap(-1)
        else
            CycleSub()
        end if
        ShowOsd()
        return true
    else if key = "down"
        if m.live and m.channelList <> invalid and m.channelList.Count() > 1
            Zap(1)
        else
            ShowOsd()
        end if
        return true
    else if key = "play"
        m.video.control = "play"
        return true
    else if key = "pause"
        m.video.control = "pause"
        return true
    end if
    return false
end function
