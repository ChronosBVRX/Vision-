sub init()
    m.border = m.top.FindNode("focusBorder")
    m.logo = m.top.FindNode("logo")
    m.name = m.top.FindNode("name")
    m.number = m.top.FindNode("number")
    m.epg = m.top.FindNode("epg")
    m.name.font = CcFont(24, true)
    m.epg.font = CcFont(19, false)
    m.number.font = CcFont(18, true)
    m.top.scaleRotateCenter = [150,105]
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
    m.name.text = SafeStr(item.title)
    m.number.text = SafeStr(item.badge)
    m.epg.text = SafeStr(item.subtitle)
end sub

sub OnFocus()
    focused = (m.top.itemHasFocus = true)
    m.border.visible = focused
    if focused then m.top.scale = [1.03, 1.03] else m.top.scale = [1.0, 1.0]
end sub


function CcFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function
