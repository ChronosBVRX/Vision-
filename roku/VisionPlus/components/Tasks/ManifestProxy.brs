' Proxy HLS local: reescribe la ventana del manifiesto para que el nodo Video
' de Roku reciba una lista estable. Los segmentos se piden directo al CDN con
' las cabeceras que ya viajan en el manifiesto.
sub init()
    m.top.functionName = "RunProxy"
end sub

function ProxyPort() as Integer
    return 8888
end function

sub RunProxy()
    srcUrl = m.top.srcUrl
    headers = m.top.headers
    if srcUrl = "" or srcUrl = invalid
        m.top.error = "sin_url"
        return
    end if
    mp = CreateObject("roMessagePort")
    listener = CreateObject("roStreamSocket")
    listener.SetMessagePort(mp)
    addr = CreateObject("roSocketAddress")
    addr.SetPort(ProxyPort())
    listener.SetAddress(addr)
    listener.NotifyReadable(true)
    if not listener.Listen(4)
        m.top.error = "socket_fallo"
        return
    end if
    m.buf = CreateObject("roByteArray")
    m.buf[4095] = 0
    m.srcUrl = srcUrl
    m.headers = headers
    m.segKeys = []
    m.segBlk = {}
    m.idxOf = {}
    m.hiNum = -1.0
    m.nextIdx = 0
    m.headerTpl = ""
    m.top.ready = true
    while true
        wait(250, mp)
        if listener.IsReadable()
            c = listener.Accept()
            if c <> invalid then ServeOne(c)
        end if
    end while
end sub

sub ServeOne(c as Object)
    got = false
    for i = 1 to 100
        if c.IsReadable()
            n = c.Receive(m.buf, 0, 4096)
            if n > 0
                got = true
                exit for
            else if n = 0
                exit for
            end if
        end if
        sleep(5)
    end for
    if got
        body = AccumulateAndBuild()
        SendManifest(c, body)
    end if
    c.Close()
end sub

function AccumulateAndBuild() as String
    windowN = 6
    maxKeep = 24
    for attempt = 1 to 4
        body = FetchManifest(m.srcUrl, m.headers)
        if body <> "" then IngestManifest(body)
    end for
    while m.segKeys.Count() > maxKeep
        oldKey = m.segKeys.Shift()
        m.segBlk.Delete(oldKey)
        m.idxOf.Delete(oldKey)
    end while
    n = m.segKeys.Count()
    if n = 0 then return ""
    startI = n - windowN
    if startI < 0 then startI = 0
    firstKey = m.segKeys[startI]
    seqBase = m.idxOf[firstKey]
    out = m.headerTpl
    out = out + "#EXT-X-MEDIA-SEQUENCE:" + seqBase.ToStr() + Chr(10)
    for i = startI to n - 1
        out = out + m.segBlk[m.segKeys[i]]
    end for
    return out
end function

sub IngestManifest(body as String)
    lines = body.Split(Chr(10))
    header = ""
    pending = ""
    sawSeg = false
    for each raw in lines
        ln = raw
        if Right(ln, 1) = Chr(13) then ln = Left(ln, Len(ln) - 1)
        if ln <> ""
            if Left(ln, 7) = "#EXTM3U" or Left(ln, 7) = "#EXT-X-"
                if not sawSeg and Left(ln, 22) <> "#EXT-X-MEDIA-SEQUENCE:"
                    header = header + ln + Chr(10)
                end if
            else if Instr(1, ln, ".ts") > 0
                sawSeg = true
                absLine = AbsUrl(ln)
                block = pending + absLine + Chr(10)
                pending = ""
                key = SegKey(absLine)
                if key <> "" then AddSeg(key, block)
            else
                pending = pending + ln + Chr(10)
            end if
        end if
    end for
    if header <> "" then m.headerTpl = header
end sub

sub AddSeg(key as String, block as String)
    num = Val(key)
    if num <= m.hiNum then return
    m.segBlk[key] = block
    m.idxOf[key] = m.nextIdx
    m.segKeys.Push(key)
    m.nextIdx = m.nextIdx + 1
    m.hiNum = num
end sub

function AbsUrl(line as String) as String
    if Left(line, 7) = "http://" or Left(line, 8) = "https://" then return line
    base = m.srcUrl
    p = 0
    for i = Len(base) to 1 step -1
        if Mid(base, i, 1) = "/"
            p = i
            exit for
        end if
    end for
    if p <= 0 then return line
    if Left(line, 1) = "/"
        scheme = "http://"
        if Left(base, 8) = "https://" then scheme = "https://"
        host = base
        if Left(host, 8) = "https://" then host = Mid(host, 9)
        if Left(host, 7) = "http://" then host = Mid(host, 8)
        slash = Instr(1, host, "/")
        if slash > 0 then host = Left(host, slash - 1)
        return scheme + host + line
    end if
    return Left(base, p) + line
end function

function SegKey(urlLine as String) as String
    p = Instr(1, urlLine, ".ts")
    if p <= 0 then return ""
    base = Left(urlLine, p - 1)
    for j = Len(base) to 1 step -1
        if Mid(base, j, 1) = "_" then return Mid(base, j + 1)
    end for
    return ""
end function

function FetchManifest(url as String, headers as Object) as String
    ut = CreateObject("roUrlTransfer")
    ut.SetUrl(url)
    ut.SetCertificatesFile("common:/certs/ca-bundle.crt")
    ut.InitClientCertificates()
    if headers <> invalid
        for each k in headers
            ut.AddHeader(k, SafeStr(headers[k]))
        end for
    end if
    ut.AddHeader("Cache-Control", "no-cache")
    ut.SetMinimumTransferRate(100, 8)
    body = ut.GetToString()
    if body = invalid then return ""
    if Instr(1, body, "#EXTM3U") = 0 then return ""
    return body
end function

sub SendManifest(c as Object, body as String)
    crlf = Chr(13) + Chr(10)
    if body = ""
        out = "HTTP/1.1 503 Service Unavailable" + crlf + "Content-Length: 0" + crlf + "Connection: close" + crlf + crlf
    else
        head = "HTTP/1.1 200 OK" + crlf
        head = head + "Content-Type: application/vnd.apple.mpegurl" + crlf
        head = head + "Content-Length: " + Len(body).ToStr() + crlf
        head = head + "Cache-Control: no-cache" + crlf
        head = head + "Connection: close" + crlf + crlf
        out = head + body
    end if
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(out)
    total = ba.Count()
    sent = 0
    while sent < total
        n = c.Send(ba, sent, total - sent)
        if n <= 0 then exit while
        sent = sent + n
    end while
end sub
