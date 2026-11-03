sub init()
    m.options = m.top.FindNode("options")
    m.detailTitle = m.top.FindNode("detailTitle")
    m.detail = m.top.FindNode("detail")
    m.status = m.top.FindNode("status")
    m.items = []
    m.options.ObserveField("itemSelected", "OnOption")
    m.top.ObserveField("load", "OnLoad")
end sub

sub OnLoad()
    BuildOptions()
    ShowInfo("Vision+", "Selecciona una opción con el mando.\n\nPerfil activo: " + Profile_Name(Profile_Active()) + "\n" + X_DebugStatus())
    m.options.SetFocus(true)
end sub

sub BuildOptions()
    m.items = []
    content = EmptyContent()
    items = [
        "Cuenta Xuper"
        "Perfil: " + Profile_Name(Profile_Active())
        Tern(Pin_Has(), "Cambiar PIN +18", "Configurar PIN +18")
        "Borrar historial del perfil"
        "Diagnóstico del equipo"
        "Acerca de Vision+"
        "Actualizar el canal"
    ]
    m.items = ["account", "profile", "pin", "clear", "diag", "about", "update"]
    for each title in items
        n = CreateObject("roSGNode", "ContentNode")
        n.AddFields({ Title: title })
        content.AppendChild(n)
    end for
    m.options.content = content
end sub

sub OnOption()
    idx = m.options.itemSelected
    if idx < 0 or idx >= m.items.Count() then return
    id = m.items[idx]
    if id = "account"
        ShowAccount()
    else if id = "profile"
        nextId = Profile_Next()
        BuildOptions()
        ShowInfo("Perfil cambiado", "Ahora usas el perfil " + Profile_Name(nextId) + ".\n\nTu historial, favoritos y «ver más tarde» son independientes por perfil.")
        m.status.text = "Perfil activo: " + Profile_Name(nextId)
        m.top.action = "profileChanged"
    else if id = "pin"
        if Pin_Has()
            OpenPin("current", "PIN actual", "Escribe tu PIN de 4 dígitos.")
        else
            OpenPin("new", "Crear PIN +18", "Escribe un PIN numérico de 4 dígitos.")
        end if
    else if id = "clear"
        ConfirmClear()
    else if id = "diag"
        ShowDiagnostics()
    else if id = "about"
        ShowAbout()
    else if id = "update"
        ShowUpdate()
    end if
end sub

sub ShowInfo(title as String, text as String)
    m.detailTitle.text = title
    m.detail.text = text
end sub

sub ShowAccount()
    s = X_Session()
    if not X_SessionReady()
        ShowInfo("Cuenta Xuper", "Sin sesión activa. Pulsa OK para crear una sesión nueva.")
    else
        ShowInfo("Cuenta Xuper", "Usuario: " + SafeStr(s.userId) + "\nPortal: " + SafeStr(s.portalCode) + "\nSN: " + SafeStr(s.sn) + "\nCreada: " + SafeStr(s.at) + "\n\nPulsa OK para renovar la sesión.")
    end if
    if m.xtask <> invalid then m.xtask.control = "STOP"
    m.xtask = NewXuperTask("session", {})
    m.xtask.ObserveField("result", "OnSession")
    m.xtask.ObserveField("error", "OnSessionError")
    m.xtask.control = "RUN"
    m.status.text = "Validando sesión..."
end sub

sub OnSession()
    m.status.text = "Sesión lista: " + X_DebugStatus()
    s = X_Session()
    ShowInfo("Cuenta Xuper", "Usuario: " + SafeStr(s.userId) + "\nPortal: " + SafeStr(s.portalCode) + "\nSN: " + SafeStr(s.sn) + "\n\nSesión validada correctamente.")
end sub

sub OnSessionError()
    err = ""
    if m.xtask <> invalid then err = SafeStr(m.xtask.error)
    m.status.text = "No se pudo renovar la sesión: " + err
    ShowInfo("Cuenta Xuper", "El portal no respondió. Revisa la conexión y vuelve a intentarlo.")
end sub

sub OpenPin(stage as String, title as String, message as String)
    remain = Pin_LockRemain()
    if remain > 0
        m.status.text = "Demasiados intentos. Espera " + remain.ToStr() + " segundos."
        return
    end if
    dlg = CreateObject("roSGNode", "KeyboardDialog")
    dlg.title = title
    dlg.text = ""
    dlg.buttons = ["Aceptar", "Cancelar"]
    m.pinStage = stage
    m.pinDialog = dlg
    dlg.ObserveField("wasClosed", "OnPinClosed")
    m.top.GetScene().dialog = dlg
    m.status.text = message
end sub

sub OnPinClosed()
    if m.pinDialog = invalid then return
    text = SafeStr(m.pinDialog.text).Trim()
    m.pinDialog.UnobserveField("wasClosed")
    m.pinDialog = invalid
    if text = ""
        m.status.text = "PIN cancelado."
        return
    end if
    if Len(text) <> 4 or not IsDigits(text)
        m.status.text = "El PIN debe tener exactamente 4 dígitos."
        return
    end if
    if m.pinStage = "current"
        if Pin_Check(text)
            Pin_ClearFail()
            OpenPin("new", "PIN nuevo", "Escribe el PIN nuevo de 4 dígitos.")
        else
            remain = Pin_Fail()
            if remain > 0 then m.status.text = "PIN incorrecto. Espera " + remain.ToStr() + " segundos." else m.status.text = "PIN incorrecto."
        end if
        return
    end if
    if Pin_Set(text)
        BuildOptions()
        m.status.text = "PIN +18 guardado correctamente."
        ShowInfo("Control parental", "PIN configurado. Se pedirá al reproducir títulos que el catálogo marque como +18.")
    else
        m.status.text = "No se pudo guardar el PIN."
    end if
end sub

sub ConfirmClear()
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Borrar historial"
    dlg.message = "¿Quieres eliminar el historial de este perfil? Los favoritos y «ver más tarde» no se tocan."
    dlg.buttons = ["Cancelar", "Sí, borrar"]
    m.confirm = dlg
    dlg.ObserveField("buttonSelected", "OnClearChoice")
    dlg.ObserveField("wasClosed", "OnClearClosed")
    m.top.GetScene().dialog = dlg
end sub

sub OnClearChoice()
    if m.confirm = invalid then return
    if m.confirm.buttonSelected = 1
        Lib_ClearHistory()
        m.status.text = "Historial borrado."
        ShowInfo("Historial", "El historial de este perfil quedó vacío.")
    end if
    CloseConfirm()
end sub

sub OnClearClosed()
    CloseConfirm()
end sub

sub CloseConfirm()
    if m.confirm = invalid then return
    m.confirm.UnobserveField("buttonSelected")
    m.confirm.UnobserveField("wasClosed")
    m.confirm = invalid
    m.top.SetFocus(true)
    m.options.SetFocus(true)
end sub

sub ShowDiagnostics()
    di = CreateObject("roDeviceInfo")
    ai = CreateObject("roAppInfo")
    model = "Roku"
    os = "?"
    mode = "?"
    if di <> invalid
        model = SafeStr(di.GetModelDisplayName())
        v = di.GetOSVersion()
        if v <> invalid then os = SafeStr(v.major) + "." + SafeStr(v.minor) + "." + SafeStr(v.revision)
        mode = SafeStr(di.GetVideoMode())
    end if
    ShowInfo("Diagnóstico", "Modelo: " + model + "\nRoku OS: " + os + "\nSalida: " + mode + "\nCanal: " + SafeStr(ai.GetValue("major_version")) + "." + SafeStr(ai.GetValue("minor_version")) + "." + SafeStr(ai.GetValue("build_version")) + "\nPerfil: " + Profile_Name(Profile_Active()) + "\n\n" + X_DebugStatus() + "\nVPS: " + VPS_Base())
end sub

sub ShowAbout()
    ShowInfo("Acerca de Vision+", "Vision+ para Roku\nVersión 1.0.0\n\nCliente SceneGraph para el catálogo y la TV en vivo de Vision+. La reproducción se resuelve contra el portal Xuper y el CDN, sin el motor X4K de Android.\n\n© 2026 ChronosBVRX. Todos los derechos reservados.\n\nEste canal es para uso personal. No redistribuyas las claves ni el catálogo.")
end sub

sub ShowUpdate()
    ShowInfo("Actualizar", "Roku no permite que un canal sideloadeado se actualice solo.\n\nPara actualizar:\n1. Descarga el ZIP nuevo de Vision+.\n2. Entra a la IP del Roku en el navegador.\n3. Instala el ZIP en el mismo canal de desarrollo.\n\nLa versión instalada es la 1.0.0.")
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back"
        m.top.action = "home"
        return true
    end if
    return false
end function
