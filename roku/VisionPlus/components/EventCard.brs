sub init()
    m.border = m.top.FindNode("focusBorder")
    m.logo = m.top.FindNode("logo")
    m.title = m.top.FindNode("title")
    m.subtitle = m.top.FindNode("subtitle")
    m.badge = m.top.FindNode("badge")
    m.title.font = EcFont(24, true)
    m.subtitle.font = EcFont(19, false)
    m.badge.font = EcFont(18, true)
    m.top.scaleRotateCenter = [190,110]
    m.top.ObserveField("itemContent", "OnContent")
    m.top.ObserveField("itemHasFocus", "OnFocus")
end sub

sub OnContent()
    item = m.top.itemContent
    if item = invalid then return
    poster = ""
    if item.poster <> invalid then poster = SafeStr(item.poster)
    if poster = "" then poster = "pkg:/images/placeholder.png"
    m.logo.uri = poster
    m.title.text = SafeStr(item.title)
    m.subtitle.text = SafeStr(item.subtitle)
    m.badge.text = SafeStr(item.badge)
end sub

sub OnFocus()
    focused = (m.top.itemHasFocus = true)
    m.border.visible = focused
    if focused then m.top.scale = [1.03, 1.03] else m.top.scale = [1.0, 1.0]
end sub


function EcFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function
