"""Crypto del wire de la familia Xuper (DESede/ECB/PKCS7 + hex/base64 por carácter).

Portado y verificado contra los scripts de investigación (hydra_wire.py, 2026):
el key string se decodifica con el decoder propio de la familia (y7a) y se corta
a 24 bytes; el cuerpo va `hex(base64(3DES(json)))`.

Llaves:
  XTV   ("masnew"):     568e194f-d170-4369-96e8-4a457f641880
  Magis ("masmobile"):  37a759c3-fc2b-413d-b424-0bb0e1505a5e
"""

import base64

from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad, unpad

_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
_TABLE = [-1] * 256
for _i, _ch in enumerate(_ALPHABET):
    _TABLE[ord(_ch)] = _i

XTV_KEY = "568e194f-d170-4369-96e8-4a457f641880"
MAGIS_KEY = "37a759c3-fc2b-413d-b424-0bb0e1505a5e"


def y7a_decode(s: str) -> bytes:
    """Réplica de y7.a (BASE64Decoder de la familia): chars inválidos -> 0xFF."""
    data = [ord(ch) & 0xFF for ch in s]
    out = bytearray()
    i = 0
    while i < len(data):
        grp = data[i:i + 4]
        i += 4
        length = len(grp)
        if length == 4 and grp[3] == 61:  # '='
            length = 3
        if length >= 3 and grp[2] == 61:
            length = 2
        v = []
        for k in range(4):
            if k < length:
                t = _TABLE[grp[k]]
                v.append(t if t >= 0 else 0xFF)
            else:
                v.append(0xFF)
        a, b, c, d = v
        out.append((((a << 2) & 0xFC) | ((b >> 4) & 3)) & 0xFF)
        if length >= 3:
            out.append((((b << 4) & 0xF0) | ((c >> 2) & 0x0F)) & 0xFF)
        if length >= 4:
            out.append((((c << 6) & 0xC0) | (d & 0x3F)) & 0xFF)
    return bytes(out)


def key24(key_str: str) -> bytes:
    return y7a_decode(key_str)[:24]


def wire_encrypt(plaintext_json: str, key_str: str = XTV_KEY) -> str:
    key = key24(key_str)
    ct = DES3.new(key, DES3.MODE_ECB).encrypt(pad(plaintext_json.encode("utf-8"), 8))
    b64 = base64.b64encode(ct).decode("ascii")
    return "".join("%02x" % ord(ch) for ch in b64)


def wire_decrypt(wire: str, key_str: str = XTV_KEY) -> str:
    key = key24(key_str)
    raw_b64 = "".join(chr(int(wire[i:i + 2], 16)) for i in range(0, len(wire), 2))
    ct = y7a_decode(raw_b64)
    return unpad(DES3.new(key, DES3.MODE_ECB).decrypt(ct), 8).decode("utf-8")
