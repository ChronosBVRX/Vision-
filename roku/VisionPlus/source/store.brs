' Vision+ for Roku - almacenamiento local, perfiles, biblioteca y control parental.

function DeviceSection() as Object
    return CreateObject("roRegistrySection", "visionplus_device")
end function

function DeviceRead(key as String) as String
    reg = DeviceSection()
    if not reg.Exists(key) then return ""
    return reg.Read(key)
end function

function DeviceWrite(key as String, value as String) as Boolean
    reg = DeviceSection()
    reg.Write(key, value)
    return reg.Flush()
end function

function DeviceDelete(key as String) as Boolean
    reg = DeviceSection()
    if reg.Exists(key) then reg.Delete(key)
    return reg.Flush()
end function

function ProfileSection() as Object
    return CreateObject("roRegistrySection", "vp_" + Profile_Active())
end function

function ProfilePath(key as String) as String
    return "cachefs:/visionplus_" + Profile_Active() + "_" + key + ".json"
end function

function ProfileRead(key as String) as String
    path = ProfilePath(key)
    value = ReadAsciiFile(path)
    if value <> "" and value <> invalid then return value
    ' Migración de versiones que guardaban la biblioteca en el registro.
    reg = ProfileSection()
    if reg.Exists(key) then return reg.Read(key)
    return ""
end function

function ProfileWrite(key as String, value as String) as Boolean
    if WriteAsciiFile(ProfilePath(key), value) then return true
    reg = ProfileSection()
    reg.Write(key, value)
    return reg.Flush()
end function

function ProfileDelete(key as String) as Boolean
    fs = CreateObject("roFileSystem")
    if fs <> invalid and fs.Exists(ProfilePath(key)) then fs.Delete(ProfilePath(key))
    reg = ProfileSection()
    if reg.Exists(key) then reg.Delete(key)
    return reg.Flush()
end function

function Profile_List() as Object
    return [
        { id: "principal", name: "Principal", kids: false }
        { id: "infantil", name: "Infantil", kids: true }
        { id: "invitado", name: "Invitado", kids: false }
        { id: "perfil4", name: "Perfil 4", kids: false }
        { id: "perfil5", name: "Perfil 5", kids: false }
    ]
end function

function Profile_Active() as String
    p = DeviceRead("active_profile")
    for each item in Profile_List()
        if item.id = p then return p
    end for
    return "principal"
end function

function Profile_Set(id as String) as Boolean
    for each item in Profile_List()
        if item.id = id
            DeviceWrite("active_profile", id)
            return true
        end if
    end for
    return false
end function

function Profile_Next() as String
    list = Profile_List()
    current = Profile_Active()
    idx = 0
    for i = 0 to list.Count() - 1
        if list[i].id = current then idx = i
    end for
    nxt = list[(idx + 1) mod list.Count()]
    Profile_Set(nxt.id)
    return nxt.id
end function

function Profile_Name(id as String) as String
    for each item in Profile_List()
        if item.id = id then return item.name
    end for
    return "Principal"
end function

function Profile_IsKids() as Boolean
    return Profile_Active() = "infantil"
end function

' ---------------------------------------------------------------------------
' Ajustes y PIN
' ---------------------------------------------------------------------------

function Settings_Get(key as String, fallback as String) as String
    v = DeviceRead("set_" + key)
    if v = "" then return fallback
    return v
end function

function Settings_Set(key as String, value as String) as Boolean
    return DeviceWrite("set_" + key, value)
end function

function Pin_Has() as Boolean
    return DeviceRead("pin_hash") <> ""
end function

function Pin_Set(pin as String) as Boolean
    if Len(pin) <> 4 then return false
    salt = RandomHex(16)
    hash = Sha256Hex("visionplus|pin|v1|" + salt + "|" + pin)
    if hash = "" then return false
    DeviceWrite("pin_salt", salt)
    DeviceWrite("pin_hash", hash)
    DeviceWrite("pin_fail", "0")
    DeviceWrite("pin_lock", "0")
    return true
end function

function Pin_Check(pin as String) as Boolean
    if not Pin_Has() then return false
    salt = DeviceRead("pin_salt")
    expected = DeviceRead("pin_hash")
    actual = Sha256Hex("visionplus|pin|v1|" + salt + "|" + pin)
    return actual <> "" and actual = expected
end function

function Pin_LockRemain() as Integer
    untilValue = AsInt(DeviceRead("pin_lock"))
    remain = untilValue - NowSeconds()
    if remain <= 0 then return 0
    return remain
end function

function Pin_Fail() as Integer
    count = AsInt(DeviceRead("pin_fail")) + 1
    if count >= 5
        DeviceWrite("pin_fail", "0")
        DeviceWrite("pin_lock", (NowSeconds() + 30).ToStr())
    else
        DeviceWrite("pin_fail", count.ToStr())
    end if
    return Pin_LockRemain()
end function

function Pin_ClearFail() as Boolean
    DeviceWrite("pin_fail", "0")
    DeviceWrite("pin_lock", "0")
    return true
end function

' ---------------------------------------------------------------------------
' Biblioteca: favoritos, ver más tarde, historial y seguir viendo
' ---------------------------------------------------------------------------

function Lib_List(key as String) as Object
    raw = ProfileRead(key)
    if raw = "" then return []
    data = ParseJsonSafe(raw)
    if data = invalid or Type(data) <> "roArray" then return []
    return data
end function

function Lib_Save(key as String, list as Object) as Boolean
    return ProfileWrite(key, FormatJson(list))
end function

function Lib_Has(key as String, film as Object) as Boolean
    if film = invalid then return false
    for each item in Lib_List(key)
        if SafeStr(item.id) = SafeStr(film.id) then return true
    end for
    return false
end function

function Lib_Toggle(key as String, film as Object) as Boolean
    if film = invalid then return false
    list = Lib_List(key)
    out = []
    found = false
    for each item in list
        if SafeStr(item.id) = SafeStr(film.id)
            found = true
        else
            out.Push(item)
        end if
    end for
    if not found then out = ListPrepend(out, Film_Mini(film))
    Lib_Save(key, out)
    return not found
end function

function Lib_IsFavorite(film as Object) as Boolean
    return Lib_Has("favorites", film)
end function

function Lib_ToggleFavorite(film as Object) as Boolean
    return Lib_Toggle("favorites", film)
end function

function Lib_IsWatchLater(film as Object) as Boolean
    return Lib_Has("watchlater", film)
end function

function Lib_ToggleWatchLater(film as Object) as Boolean
    return Lib_Toggle("watchlater", film)
end function

function Lib_Favorites() as Object
    out = []
    for each item in Lib_List("favorites")
        f = Film_FromMini(item)
        if f <> invalid then out.Push(f)
    end for
    return out
end function

function Lib_WatchLater() as Object
    out = []
    for each item in Lib_List("watchlater")
        f = Film_FromMini(item)
        if f <> invalid then out.Push(f)
    end for
    return out
end function

function Lib_HistoryKey(film as Object, ep as Object) as String
    key = SafeStr(film.id)
    if ep <> invalid and SafeStr(ep.contentId) <> "" then key = key + "|" + SafeStr(ep.contentId)
    return key
end function

function Lib_AddHistory(film as Object, positionSec as Integer, dur as Integer, ep as Dynamic) as Boolean
    if film = invalid then return false
    key = Lib_HistoryKey(film, ep)
    list = Lib_List("history")
    out = []
    for each item in list
        oldFilm = Film_FromMini(item.film)
        if oldFilm <> invalid and SafeStr(item.key) <> key
            out.Push(item)
        end if
    end for
    rec = {
        key: key
        film: Film_Mini(film)
        position: positionSec
        dur: dur
        ep: Tern(ep = invalid, {}, ep)
        at: NowSeconds()
    }
    out = ListPrepend(out, rec)
    while out.Count() > 40
        out.Delete(out.Count() - 1)
    end while
    return Lib_Save("history", out)
end function

function Lib_History() as Object
    return Lib_List("history")
end function

function Lib_HistoryFilms() as Object
    out = []
    for each rec in Lib_List("history")
        f = Film_FromMini(rec.film)
        if f <> invalid
            f._resumePos = AsInt(rec.position)
            f._resumeDur = AsInt(rec.dur)
            f._resumeEp = rec.ep
            out.Push(f)
        end if
    end for
    return out
end function

function Lib_Continue() as Object
    out = []
    seen = {}
    for each rec in Lib_List("history")
        f = Film_FromMini(rec.film)
        if f = invalid then continue for
        key = SafeStr(f.id)
        if seen[key] <> invalid then continue for
        posn = AsInt(rec.position)
        dur = AsInt(rec.dur)
        if posn >= 30 and (dur <= 0 or posn < Int(dur * 0.95))
            seen[key] = true
            f._resumePos = posn
            f._resumeDur = dur
            f._resumeEp = rec.ep
            out.Push(f)
            if out.Count() >= 12 then exit for
        end if
    end for
    return out
end function

function Lib_ProgressFor(film as Object, ep as Dynamic) as Object
    if film = invalid then return invalid
    key = Lib_HistoryKey(film, ep)
    for each rec in Lib_List("history")
        if SafeStr(rec.key) = key then return rec
    end for
    return invalid
end function

function Lib_ClearHistory() as Boolean
    return ProfileDelete("history")
end function

function Lib_SearchAdd(term as String) as Boolean
    t = term.Trim()
    if t = "" then return false
    list = Lib_List("searches")
    out = []
    for each item in list
        if LCase(SafeStr(item)) <> LCase(t) then out.Push(item)
    end for
    out = ListPrepend(out, t)
    while out.Count() > 12
        out.Delete(out.Count() - 1)
    end while
    return Lib_Save("searches", out)
end function

function Lib_Searches() as Object
    return Lib_List("searches")
end function

function Lib_ClearAll() as Boolean
    for each key in ["favorites", "watchlater", "history", "searches"]
        ProfileDelete(key)
    end for
    return true
end function

function ListPrepend(list as Object, item as Dynamic) as Object
    out = [item]
    for each old in list
        out.Push(old)
    end for
    return out
end function

function IsDigits(value as String) as Boolean
    if Len(value) = 0 then return false
    for i = 1 to Len(value)
        c = Mid(value, i, 1)
        if c < "0" or c > "9" then return false
    end for
    return true
end function
