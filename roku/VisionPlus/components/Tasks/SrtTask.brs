sub init()
    m.top.functionName = "RunSrt"
end sub

sub RunSrt()
    url = m.top.url
    if url = "" or url = invalid
        m.top.cues = []
        return
    end if
    m.top.cues = Srt_Fetch(url)
end sub
