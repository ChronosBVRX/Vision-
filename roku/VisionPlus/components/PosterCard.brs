sub init()
    m.poster = m.top.FindNode("poster")
    m.title = m.top.FindNode("title")
    m.badge = m.top.FindNode("badge")
    m.badgeBg = m.top.FindNode("badgeBg")
    m.scoreGroup = m.top.FindNode("scoreGroup")
    m.score = m.top.FindNode("score")
    m.rankLabel = m.top.FindNode("rankLabel")
    m.rankShadow = m.top.FindNode("rankShadow")
    m.focusBorder = m.top.FindNode("focusBorder")
    m.top.scaleRotateCenter = [148, 220]
    m.title.font = PcFont(24, false)
    m.badge.font = PcFont(17, true)
    m.score.font = PcFont(18, true)
    m.rankLabel.font = PcFont(112, true)
    m.rankShadow.font = PcFont(112, true)
    m.top.ObserveField("itemContent", "OnContent")
    m.top.ObserveField("itemHasFocus", "OnFocus")
end sub

sub OnContent()
    item = m.top.itemContent
    if item = invalid then return
    poster = ""
    if item.poster <> invalid then poster = SafeStr(item.poster)
    if poster = "" then poster = "pkg:/images/placeholder.png"
    m.poster.failedBitmapUri = "pkg:/images/placeholder.png"
    m.poster.uri = poster
    m.title.text = SafeStr(item.title)
    badge = SafeStr(item.badge)
    m.badge.text = badge
    m.badge.visible = (badge <> "")
    m.badgeBg.visible = (badge <> "")
    score = SafeStr(item.score)
    m.scoreGroup.visible = (score <> "")
    m.score.text = score
    rank = 0
    if item.rank <> invalid then rank = AsInt(item.rank)
    if rank > 0
        m.rankLabel.text = rank.ToStr()
        m.rankShadow.text = rank.ToStr()
        m.rankLabel.visible = true
        m.rankShadow.visible = true
    else
        m.rankLabel.visible = false
        m.rankShadow.visible = false
    end if
end sub

sub OnFocus()
    focused = (m.top.itemHasFocus = true)
    m.focusBorder.visible = focused
    if focused
        m.top.scale = [1.035, 1.035]
        m.title.color = "0xFFFFFFFF"
    else
        m.top.scale = [1.0, 1.0]
        m.title.color = "0xF4F7FCFF"
    end if
end sub


function PcFont(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function
