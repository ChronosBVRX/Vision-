' Vision+ for Roku - subtítulos SRT externos.

function Srt_Hms(h as String, mn as String, s as String, ms as String) as Float
    return Val(h) * 3600.0 + Val(mn) * 60.0 + Val(s) + Val(ms) / 1000.0
end function

function Srt_Parse(text as String) as Object
    cues = []
    if text = "" then return cues
    blockRx = CreateObject("roRegex", "\r?\n\r?\n+", "")
    lineRx = CreateObject("roRegex", "\r?\n", "")
    tsRx = CreateObject("roRegex", "(\d+):(\d+):(\d+)[,.](\d+)\s*-->\s*(\d+):(\d+):(\d+)[,.](\d+)", "")
    tagRx = CreateObject("roRegex", "<[^>]+>|\{[^}]*\}", "")
    blocks = blockRx.Split(text)
    for each block in blocks
        if block = invalid or Len(block) = 0 then continue for
        lines = lineRx.Split(block)
        tsIdx = -1
        for i = 0 to lines.Count() - 1
            if Instr(1, lines[i], "-->") > 0
                tsIdx = i
                exit for
            end if
        end for
        if tsIdx < 0 then continue for
        mm = tsRx.Match(lines[tsIdx])
        if mm.Count() < 9 then continue for
        startS = Srt_Hms(mm[1], mm[2], mm[3], mm[4])
        endS = Srt_Hms(mm[5], mm[6], mm[7], mm[8])
        if endS <= startS then continue for
        txt = ""
        for j = tsIdx + 1 to lines.Count() - 1
            ln = lines[j]
            if ln <> invalid and Len(ln) > 0
                if txt <> "" then txt = txt + Chr(10)
                txt = txt + ln
            end if
        end for
        txt = tagRx.ReplaceAll(txt, "")
        if txt <> "" then cues.Push({ s: startS, e: endS, t: txt })
    end for
    return cues
end function

function Srt_Fetch(url as String) as Object
    if url = "" then return []
    req = CreateObject("roUrlTransfer")
    req.SetUrl(url)
    req.SetCertificatesFile("common:/certs/ca-bundle.crt")
    req.InitClientCertificates()
    req.AddHeader("User-Agent", "Ranger/4.9.4-17294ac0")
    body = req.GetToString()
    if body = invalid or body = "" then return []
    return Srt_Parse(body)
end function

function Srt_TextAt(cues as Object, atPos as Integer, offset as Float) as String
    if cues = invalid or cues.Count() = 0 then return ""
    look = atPos - offset
    if look < 0 then look = 0
    for each c in cues
        if look >= c.s and look <= c.e then return c.t
    end for
    return ""
end function
