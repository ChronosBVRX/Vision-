sub init()
    m.title = m.top.FindNode("title")
    m.tagline = m.top.FindNode("tagline")
    m.meta = m.top.FindNode("meta")
    m.accent = m.top.FindNode("accent")
    m.focusBorder = m.top.FindNode("focusBorder")
    m.title.font = GcFont(26, true)
    m.tagline.font = GcFont(19, false)
    m.meta.font = GcFont(17, true)
    m.top.scaleRotateCenter = [190,110]
    m.top.ObserveField("itemContent", "OnContent")
    m.top.ObserveField("itemHasFocus", "OnFocus")
end sub

function GcFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function

sub OnContent()
    item = m.top.itemContent
    if item = invalid then return
    g = item.game
    if g = invalid then return
    m.title.text = SafeStr(g.title)
    m.tagline.text = SafeStr(g.tagline)
    m.meta.text = SafeStr(g.minPlayers) + "-" + SafeStr(g.maxPlayers) + " jugadores  ·  " + SafeStr(g.approxMinutes) + " min"
    accent = SafeStr(g.accent)
    if accent <> "" then m.accent.color = accent
end sub

sub OnFocus()
    focused = (m.top.itemHasFocus = true)
    m.focusBorder.visible = focused
    if focused then m.top.scale = [1.03, 1.03] else m.top.scale = [1.0, 1.0]
end sub
