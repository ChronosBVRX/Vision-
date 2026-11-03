sub init()
    m.top.functionName = "RunRequest"
end sub

sub RunRequest()
    url = m.top.url
    if url = "" then
        m.top.error = "sin_url"
        return
    end if
    req = CreateObject("roUrlTransfer")
    req.SetUrl(url)
    req.SetCertificatesFile("common:/certs/ca-bundle.crt")
    req.InitClientCertificates()
    req.EnableEncodings(true)
    req.SetMinimumTransferRate(100, 10)
    headers = m.top.headers
    if headers <> invalid
        for each k in headers
            req.AddHeader(k, SafeStr(headers[k]))
        end for
    end if
    port = CreateObject("roMessagePort")
    req.SetMessagePort(port)
    method = UCase(m.top.method)
    ok = false
    if method = "POST"
        ok = req.AsyncPostFromString(m.top.body)
    else
        ok = req.AsyncGetToString()
    end if
    if not ok
        m.top.error = "async_fallo"
        return
    end if
    msg = wait(30000, port)
    if msg = invalid
        req.AsyncCancel()
        m.top.error = "timeout"
        return
    end if
    if Type(msg) <> "roUrlEvent"
        m.top.error = "evento_inesperado"
        return
    end if
    m.top.status = msg.GetResponseCode()
    body = msg.GetString()
    if body = invalid then body = ""
    m.top.response = body
    if m.top.status < 200 or m.top.status >= 300 then m.top.error = "http_" + m.top.status.ToStr()
end sub
