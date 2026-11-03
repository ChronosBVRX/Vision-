' Vision+ for Roku - cliente Xuper/XTV: sesión, cifrado 3DES y resolución de CDN.
' La reproducción no usa el motor X4K (no existe en Roku): pide la licencia al
' portal y reproduce el TS/HLS directo del CDN, igual que el port de webOS.

function X_KeyHex() as String
    return "e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8"
end function

function X_SnSecret() as String
    return "ntFT65w6itH!lHCPw7D=@qnsFC5adD28"
end function

function X_Hosts() as Object
    return ["fuxok.nguvmqhpk.com", "mptec.dhkrxuzcy.com", "eajmnp.hcgv1dt8.com"]
end function

function X_Headers() as Object
    return {
        "Content-Type": "application/json; charset=utf-8"
        "apk": "com.android.msandroid"
        "apkVer": "43404"
        "spkgVer": "2025-03-11 02:26:11_33_13_4.14.186-27270957"
        "User-Agent": "okhttp/4.12.0"
        "Cache-Control": "no-store"
    }
end function

function X_BaseBody(sn as String) as Object
    return {
        apkVersion: "43404"
        appId: "com.android.msandroid"
        appLanguage: "es"
        cpu: "arm64-v8a"
        deviceToken: ""
        hardwareInfo: "mt6769t"
        loginType: "3"
        model: "SM-A325M"
        product: "a32ub"
        reserve1: "000000000000"
        sn: sn
        sysVersion: "13"
    }
end function

function X_Encrypt(plain as String) as String
    keyHex = X_KeyHex()
    cipher = CreateObject("roEVPCipher")
    if cipher = invalid then return ""
    if cipher.Setup(true, "des-ede3", keyHex, "", 1) <> 0 then return ""
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(plain)
    enc = cipher.Process(ba)
    if enc = invalid then return ""
    b64 = enc.ToBase64String()
    b64ba = CreateObject("roByteArray")
    b64ba.FromAsciiString(b64)
    return BytesToHex(b64ba)
end function

function X_Decrypt(hex as String) as String
    if hex = "" then return ""
    cipher = CreateObject("roEVPCipher")
    if cipher = invalid then return ""
    if cipher.Setup(false, "des-ede3", X_KeyHex(), "", 1) <> 0 then return ""
    hexba = CreateObject("roByteArray")
    hexba.FromHexString(hex)
    if hexba.Count() = 0 then return ""
    b64 = hexba.ToAsciiString()
    enc = CreateObject("roByteArray")
    enc.FromBase64String(b64)
    if enc.Count() = 0 then return ""
    dec = cipher.Process(enc)
    if dec = invalid then return ""
    return dec.ToAsciiString()
end function

function X_HttpPost(url as String, wire as String) as Object
    req = CreateObject("roUrlTransfer")
    req.SetUrl(url)
    req.SetCertificatesFile("common:/certs/ca-bundle.crt")
    req.InitClientCertificates()
    req.EnableEncodings(true)
    headers = X_Headers()
    for each k in headers
        req.AddHeader(k, headers[k])
    end for
    port = CreateObject("roMessagePort")
    req.SetMessagePort(port)
    if not req.AsyncPostFromString(wire)
        return invalid
    end if
    msg = wait(20000, port)
    if msg = invalid then return invalid
    if Type(msg) <> "roUrlEvent" then return invalid
    return { code: msg.GetResponseCode(), body: msg.GetString() }
end function

function X_Api(path as String, body as Object) as Dynamic
    wire = X_Encrypt(FormatJson(body))
    if wire = "" then
        m.xError = "crypto"
        return invalid
    end if
    for each host in X_Hosts()
        res = X_HttpPost("https://" + host + "/api/portalCore" + path, wire)
        if res = invalid then
            m.xError = "red"
            continue for
        end if
        if res.code < 200 or res.code >= 300 then
            m.xError = "http_" + res.code.ToStr()
            continue for
        end if
        outer = ParseJsonSafe(res.body)
        if outer = invalid then
            m.xError = "respuesta_no_json"
            continue for
        end if
        rc = SafeStr(outer.returnCode)
        if rc <> "0"
            m.xError = "portal_" + rc + " " + SafeStr(outer.errorMessage)
            if Left(rc, 3) = "aaa" then m.xSessionStale = true
            return invalid
        end if
        data = outer.data
        if Type(data) = "roString" or Type(data) = "String"
            plain = X_Decrypt(data)
            if plain = "" then
                m.xError = "descifrado"
                return invalid
            end if
            return ParseJsonSafe(plain)
        end if
        if data = invalid then return {}
        return data
    end for
    return invalid
end function

function X_Session() as Object
    raw = DeviceRead("xuper_session")
    if raw = "" then return {}
    s = ParseJsonSafe(raw)
    if s = invalid then return {}
    return s
end function

function X_SessionReady() as Boolean
    s = X_Session()
    return IsValidStr(SafeStr(s.userToken)) and IsValidStr(SafeStr(s.userId)) and IsValidStr(SafeStr(s.sn))
end function

function X_SaveSession(sn as String, snToken as String, userId as String, userToken as String) as Boolean
    return DeviceWrite("xuper_session", FormatJson({
        sn: sn
        snToken: snToken
        userId: userId
        userToken: userToken
        portalCode: "masnew"
        at: NowSeconds()
    }))
end function

function X_ResetSession() as Boolean
    return DeviceDelete("xuper_session")
end function

function X_EnsureSession(force as Boolean) as Boolean
    m.xError = ""
    m.xSessionStale = false
    s = X_Session()
    if not force and X_SessionReady()
        age = NowSeconds() - AsInt(s.at)
        if age >= 0 and age < 6 * 3600 then return true
    end if
    sn = SafeStr(s.sn)
    snToken = SafeStr(s.snToken)
    if force then snToken = ""
    if snToken = ""
        d = X_Api("/v3/snToken", {
            channel: "default"
            macAddr: "02:00:00:00:00:00"
            snToken: ""
            portalCode: "masnew"
        })
        if d = invalid then return false
        snToken = SafeStr(d.snToken)
        if snToken = "" then
            m.xError = "sin_snToken"
            return false
        end if
        sn = MD5Hex(snToken + X_SnSecret())
    end if
    activeBody = X_BaseBody(sn)
    activeBody.snToken = snToken
    activeBody.macAddr = "02:00:00:00:00:00"
    activeBody.channel = "default"
    activeBody.portalCode = "masnew"
    activeBody.sn = sn
    a = X_Api("/v8/active", activeBody)
    if a = invalid then
        ' Un snToken viejo puede estar muerto: se pide uno nuevo una vez.
        if not force
            return X_EnsureSession(true)
        end if
        return false
    end if
    userId = SafeStr(a.userId)
    userToken = SafeStr(a.userToken)
    if userId = "" or userToken = "" then
        m.xError = "active_sin_sesion"
        return false
    end if
    X_SaveSession(sn, snToken, userId, userToken)
    return true
end function

function X_PortalCode() as String
    s = X_Session()
    p = SafeStr(s.portalCode)
    if p = "" then return "masnew"
    return p
end function

function X_UserId() as String
    return SafeStr(X_Session().userId)
end function

function X_UserToken() as String
    return SafeStr(X_Session().userToken)
end function

function X_CommonBody() as Object
    b = X_BaseBody(SafeStr(X_Session().sn))
    b.portalCode = X_PortalCode()
    b.userId = X_UserId()
    b.userToken = X_UserToken()
    return b
end function

' ---------------------------------------------------------------------------
' Llamadas de catálogo / reproducción
' ---------------------------------------------------------------------------

function X_LiveCategories() as Object
    if not X_EnsureSession(false) then return invalid
    body = X_CommonBody()
    body.columnId = 76175
    body.pageNum = 1
    body.pageSize = 100
    d = X_Api("/v3/getColumnContents", body)
    if d = invalid then return invalid
    return FormatJson(d)
end function

function X_LiveChannels(columnId as Integer) as Object
    if not X_EnsureSession(false) then return invalid
    body = X_CommonBody()
    body.columnId = columnId
    body.dataVersion = ""
    body.pageNum = 1
    body.pageSize = 3000
    body.expireTimeStr = ""
    d = X_Api("/v6/getLiveData", body)
    if d = invalid then return invalid
    return FormatJson(d)
end function

function X_Episodes(contentId as String) as Object
    if not X_EnsureSession(false) then return invalid
    for each typ in ["0", "1"]
        body = X_CommonBody()
        body.contentId = contentId
        body.type = typ
        body.sortType = ""
        body.language = "es"
        d = X_Api("/v4/getItemData", body)
        if d <> invalid and d.assetData <> invalid and d.assetData.simpleProgramList <> invalid
            if d.assetData.simpleProgramList.Count() > 0 then return FormatJson(d.assetData.simpleProgramList)
        end if
    end for
    return "[]"
end function

function X_SlbBody() as Object
    b = X_CommonBody()
    b.appParams = ""
    b.appVer = "43404"
    b.encMediaSupported = 1
    b.hasPay = "0"
    b.lang = "es"
    b.liveCodeList = [X_PortalCode() + "_live"]
    b.pipFlag = "0"
    b.type = "merge"
    b.userIdentity = "1"
    return b
end function

function X_LivePlayBody(channelCode as String, columnId as Integer) as Object
    b = X_CommonBody()
    b.channelCode = channelCode
    b.columnId = columnId
    b.type = "live"
    return b
end function

function X_VodPlayBody(contentId as String, seriesContentId as String, columnId as Integer) as Object
    b = X_CommonBody()
    b.authType = Tern(seriesContentId = "", "account", "")
    b.columnId = Tern(columnId > 0, columnId, 87026)
    b.contentId = contentId
    b.seriesContentId = seriesContentId
    b.startTime = 0
    b.type = "1"
    if seriesContentId = "" then b.episodeNumberList = [1]
    return b
end function

function X_HostOf(mainAddr as String) as String
    h = SafeStr(mainAddr)
    if Left(h, 8) = "https://" then h = Mid(h, 9)
    if Left(h, 7) = "http://" then h = Mid(h, 8)
    p = Instr(1, h, "/")
    if p > 0 then h = Left(h, p - 1)
    return h
end function

function X_WithScheme(mainAddr as String) as String
    h = SafeStr(mainAddr)
    if Left(h, 8) = "https://" or Left(h, 7) = "http://" then
        while Right(h, 1) = "/"
            h = Left(h, Len(h) - 1)
        end while
        return h
    end if
    while Right(h, 1) = "/"
        h = Left(h, Len(h) - 1)
    end while
    return "https://" + h
end function

function X_HasCfl(item as Object) as Boolean
    url = LCase(SafeStr(item.url))
    return Instr(1, url, "sign_type=cfl") > 0 or Instr(1, url, "link=cf") > 0
end function

function X_ChooseLiveCdn(slb as Object) as Dynamic
    if slb = invalid or slb.cdn_list = invalid then return invalid
    first = invalid
    for each cdn in slb.cdn_list
        if SafeStr(cdn.tag) <> "live" then continue for
        if cdn.url_list = invalid or cdn.url_list.Count() = 0 then continue for
        entry = { host: X_HostOf(cdn.main_addr), auth: SafeStr(cdn.url_list[0].url), type: SafeStr(cdn.cdn_type) }
        if first = invalid then first = entry
        if SafeStr(cdn.cdn_type) = "4" then return entry
    end for
    return first
end function

function X_ChooseVodCdn(slb as Object) as Dynamic
    if slb = invalid or slb.cdn_list = invalid then return invalid
    first = invalid
    for each cdn in slb.cdn_list
        if SafeStr(cdn.tag) <> "vod" then continue for
        if cdn.url_list = invalid or cdn.url_list.Count() = 0 then continue for
        entry = { base: X_WithScheme(cdn.main_addr), auth: SafeStr(cdn.url_list[0].url) }
        if first = invalid then first = entry
        if X_HasCfl(cdn.url_list[0]) then return entry
    end for
    return first
end function

function X_StreamHeaders(auth as String, license as String) as Object
    return {
        "Content-Auth": auth
        "Content-License": license
        "User-Agent": "Ranger/4.9.4-17294ac0"
        "App": "com.android.msandroid"
        "App-Version": "43404"
        "Accept": "*/*"
    }
end function

function X_ResolveLive(channelCode as String, columnId as Integer) as Object
    if not X_EnsureSession(false) then return invalid
    slb = X_Api("/v15/getSlbInfo", X_SlbBody())
    if slb = invalid then return invalid
    play = X_Api("/v4/startPlayLive", X_LivePlayBody(channelCode, columnId))
    if play = invalid or play.liveAddressList = invalid or play.liveAddressList.Count() = 0
        m.xError = "live_sin_direccion"
        return invalid
    end if
    cdn = X_ChooseLiveCdn(slb)
    if cdn = invalid then
        m.xError = "live_sin_cdn"
        return invalid
    end if
    addr = play.liveAddressList[0]
    playCode = SafeStr(addr.playCode)
    license = SafeStr(addr.license)
    if playCode = "" or license = "" then
        m.xError = "live_sin_licencia"
        return invalid
    end if
    return {
        url: "http://" + cdn.host + "/live/" + playCode + ".m3u8"
        headers: X_StreamHeaders(cdn.auth, license)
        title: SafeStr(play.name)
        live: true
        playCode: playCode
    }
end function

function X_CodecLabel(raw as Dynamic) as String
    v = UCase(SafeStr(raw))
    if Instr(1, v, "H265") > 0 or Instr(1, v, "HEVC") > 0 then return "H.265"
    if Instr(1, v, "H264") > 0 or Instr(1, v, "AVC") > 0 then return "H.264"
    if Instr(1, v, "MPEG2") > 0 then return "MPEG-2"
    return SafeStr(raw)
end function

function X_CanPlay(codec as String) as Boolean
    c = UCase(codec)
    di = CreateObject("roDeviceInfo")
    if di = invalid then return true
    if c = "H.264" or Instr(1, c, "264") > 0
        a = di.CanDecodeVideo({ codec: "mpeg4 avc" })
        if a <> invalid and a.result <> invalid then return a.result
    else if c = "H.265" or Instr(1, c, "265") > 0
        a = di.CanDecodeVideo({ codec: "hevc" })
        if a <> invalid and a.result <> invalid then return a.result
    else if c = "MPEG-2"
        a = di.CanDecodeVideo({ codec: "mpeg2" })
        if a <> invalid and a.result <> invalid then return a.result
    end if
    return true
end function

function X_VodDuration(mv as Object, ep as Object) as Integer
    for each src in [mv, ep]
        if src = invalid then continue for
        for each key in ["duration", "timeLen", "totalTime", "time", "playTime", "length"]
            v = src[key]
            if v <> invalid
                n = AsInt(v)
                if n > 0 then return n
            end if
        end for
    end for
    return 0
end function

function X_SubsFromEpisode(ep as Object) as Object
    out = []
    if ep = invalid or ep.subtitleList = invalid then return out
    for each s in ep.subtitleList
        if s.file = invalid or s.file.Count() = 0 then continue for
        for each file in s.file
            url = SafeStr(file.url)
            if url <> ""
                out.Push({ lang: SafeStr(s.language), url: url })
                exit for
            end if
        end for
    end for
    return out
end function

function X_ResolveVod(contentId as String, seriesContentId as String, columnId as Integer) as Object
    if not X_EnsureSession(false) then return invalid
    slb = X_Api("/v15/getSlbInfo", X_SlbBody())
    if slb = invalid then return invalid
    play = X_Api("/v10/startPlayVOD", X_VodPlayBody(contentId, seriesContentId, columnId))
    if play = invalid or play.episodeList = invalid or play.episodeList.Count() = 0
        m.xError = "vod_sin_episodio"
        return invalid
    end if
    ep = play.episodeList[0]
    if ep.totalMovieList = invalid then
        m.xError = "vod_sin_pistas"
        return invalid
    end if
    cdn = X_ChooseVodCdn(slb)
    if cdn = invalid then
        m.xError = "vod_sin_cdn"
        return invalid
    end if
    variants = []
    seen = {}
    for each tm in ep.totalMovieList
        if tm.movieList = invalid then continue for
        for each mv in tm.movieList
            mc = SafeStr(mv.contentId)
            if mc = "" then continue for
            if mv.licenseList = invalid or mv.licenseList.Count() = 0 then continue for
            license = SafeStr(mv.licenseList[0].license)
            if license = "" then continue for
            if seen[mc] <> invalid then continue for
            seen[mc] = true
            codec = X_CodecLabel(mv.encodeFormat)
            if codec = "" then codec = X_CodecLabel(tm.encodeFormat)
            fmt = SafeStr(mv.videoFormat)
            if fmt = "" then fmt = "ts"
            url = cdn.base + "/vod/" + mc + "_media." + fmt
            variants.Push({
                mediaCode: mc
                url: url
                headers: X_StreamHeaders(cdn.auth, license)
                codec: codec
                quality: SafeStr(mv.quality)
                duration: X_VodDuration(mv, ep)
                format: fmt
            })
        end for
    end for
    if variants.Count() = 0 then
        m.xError = "vod_sin_variantes"
        return invalid
    end if
    ' H.264 primero; después HEVC; luego lo desconocido. El reproductor además
    ' descarta las variantes que el Roku diga que no puede decodificar.
    out = []
    for each v in variants
        if X_CanPlay(v.codec) then out.Push(v)
    end for
    if out.Count() = 0
        m.xError = "codec_no_compatible"
        return invalid
    end if
    if out.Count() > 1
    for i = 0 to out.Count() - 2
        for j = 0 to out.Count() - 2 - i
            a = LCase(out[j].codec)
            b = LCase(out[j + 1].codec)
            if (Instr(1, a, "265") > 0) and (Instr(1, b, "264") > 0)
                tmp = out[j] : out[j] = out[j + 1] : out[j + 1] = tmp
            end if
        end for
    end for
    end if
    return {
        variants: out
        subs: X_SubsFromEpisode(ep)
        title: SafeStr(play.name)
        duration: X_VodDuration(invalid, ep)
        live: false
    }
end function

function X_DebugStatus() as String
    s = X_Session()
    if not X_SessionReady() then return "Sin sesión Xuper"
    return "Xuper: usuario " + SafeStr(s.userId) + " · portal " + SafeStr(s.portalCode) + " · sn " + Left(SafeStr(s.sn), 10) + "…"
end function
