"""El portal XTV migró a los hosts/llave de Magis el 2026-11-12.

Los hosts históricos (fuxok/mptec, llave 568e...) contestan `portal200001` a
cualquier acuñación. El catálogo masnew y sus licencias siguen vivos en Magis:
metadatos con `portalCode=masnew` y play con `masmobile` por `v9/startPlayVOD`.
Estas aserciones evitan que una limpieza vuelva a dejar el VOD sin licencia.
"""

import json

import magis
import portal
import wire
import xtv


def test_worker_uses_the_magis_hosts_and_key():
    assert xtv.APP_ID == "com.msandroid.mobile"
    assert xtv.APK_VER == "60507"
    assert xtv.SPKG == "13"
    assert xtv.HOSTS == ["rhokqi.ryh1gifvn.com", "zgmnxa.ryh1gifvn.com"]
    assert xtv.PORTAL == "masnew"
    # El cable de la familia se sigue descifrando con la llave vigente.
    encrypted = wire.wire_encrypt('{"ok":true}', wire.MAGIS_KEY)
    assert json.loads(wire.wire_decrypt(encrypted, wire.MAGIS_KEY)) == {"ok": True}


def test_api_search_uses_the_same_portal_identity():
    assert portal.APP_ID == "com.msandroid.mobile"
    assert portal.APK_VER == "60507"
    assert portal.SPKG == "13"
    assert portal.HOSTS == ["rhokqi.ryh1gifvn.com", "zgmnxa.ryh1gifvn.com"]
    assert portal.PORTAL == "masnew"


def test_play_tenant_is_masmobile_not_masnew():
    """`masnew` da portal100001 en startPlayVOD; la licencia libre es de masmobile."""
    assert xtv.PORTAL == "masnew"       # metadatos/sesión
    assert magis.PORTAL == "masmobile"  # play
    assert magis.APP_ID == "com.msandroid.mobile"
