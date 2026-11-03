sub init()
    m.border = m.top.FindNode("focusBorder")
    m.still = m.top.FindNode("still")
    m.title = m.top.FindNode("title")
    m.number = m.top.FindNode("number")
    m.title.font = EpFont(21, true)
    m.number.font = EpFont(17, true)
    m.top.scaleRotateCenter = [150,107]
    m.top.ObserveField("itemContent", "OnContent")
    m.top.ObserveField("itemHasFocus", "OnFocus")
end sub

sub OnContent()
    item = m.top.itemContent
    if item = invalid then return
    poster = ""
    if item.poster <> invalid then poster = SafeStr(item.poster)
    if poster = "" then poster = "pkg:/images/placeholder_wide.png"
    m.still.uri = poster
    m.title.text = SafeStr(item.title)
    m.number.text = SafeStr(item.badge)
end sub

sub OnFocus()
    focused = (m.top.itemHasFocus = true)
    m.border.visible = focused
    if focused then m.top.scale = [1.03, 1.03] else m.top.scale = [1.0, 1.0]
end sub


function EpFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function
