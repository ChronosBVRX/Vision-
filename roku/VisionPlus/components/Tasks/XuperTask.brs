sub init()
    m.top.functionName = "RunXuper"
end sub

sub RunXuper()
    m.xError = ""
    m.xSessionStale = false
    op = m.top.op
    args = m.top.args
    if args = invalid then args = {}
    result = invalid
    for attempt = 1 to 2
        result = RunOp(op, args)
        if result <> invalid then exit for
        if m.xSessionStale and attempt = 1
            X_EnsureSession(true)
        else
            exit for
        end if
    end for
    if result = invalid
        m.top.error = Tern(m.xError = "", "operacion_fallida", m.xError)
        return
    end if
    if Type(result) = "roString" or Type(result) = "String"
        m.top.result = result
    else
        m.top.result = FormatJson(result)
    end if
end sub

function RunOp(op as String, args as Object) as Dynamic
    if op = "session"
        if X_EnsureSession(true) then return { ok: true, status: X_DebugStatus() }
        return invalid
    else if op = "reset"
        X_ResetSession()
        return { ok: true }
    else if op = "debug"
        return { ok: X_SessionReady(), status: X_DebugStatus() }
    else if op = "liveCategories"
        return X_LiveCategories()
    else if op = "liveChannels"
        return X_LiveChannels(AsInt(args.columnId))
    else if op = "episodes"
        return X_Episodes(SafeStr(args.contentId))
    else if op = "resolveLive"
        return X_ResolveLive(SafeStr(args.channelCode), AsInt(args.columnId))
    else if op = "resolveVod"
        return X_ResolveVod(SafeStr(args.contentId), SafeStr(args.seriesContentId), AsInt(args.columnId))
    end if
    m.xError = "op_desconocida"
    return invalid
end function
