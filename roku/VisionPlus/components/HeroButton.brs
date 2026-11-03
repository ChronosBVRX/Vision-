sub init()
    m.top.focusable = true
    m.top.width = 320
    m.top.height = 88
    m.top.color = "0x00000000"
    m.top.childRenderOrder = "renderFirst"
    m.bg = m.top.FindNode("bg")
    m.icon = m.top.FindNode("icon")
    m.text = m.top.FindNode("text")
    m.focused = false
    m.top.ObserveField("label", "Render")
    m.top.ObserveField("icon", "Render")
    m.top.ObserveField("primary", "Render")
    m.top.ObserveField("focusedChild", "OnFocus")
    Render()
end sub

sub OnFocus()
    m.focused = m.top.hasFocus()
    Render()
end sub

sub Render()
    primary = (m.top.primary = true)
    if primary
        if m.focused
            m.bg.uri = "pkg:/images/pill_blue_focus.png"
        else
            m.bg.uri = "pkg:/images/pill_blue.png"
        end if
    else
        if m.focused
            m.bg.uri = "pkg:/images/pill_light_focus.png"
        else
            m.bg.uri = "pkg:/images/pill_light.png"
        end if
    end if
    m.text.text = SafeStr(m.top.label)
    if SafeStr(m.top.icon) <> ""
        m.icon.uri = SafeStr(m.top.icon)
        m.icon.visible = true
        m.text.translation = [62, 0]
        m.text.width = 236
    else
        m.icon.visible = false
        m.text.translation = [24, 0]
        m.text.width = 272
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "OK"
        m.top.action = "selected"
        return true
    end if
    return false
end function
