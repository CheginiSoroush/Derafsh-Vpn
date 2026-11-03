#!/usr/bin/env python3
"""Extract first X.509 cert from APK Signing Block (v2/v3) and print SHA-256."""
import struct, hashlib, sys

def read_lp(data, off):
    """read length-prefixed blob (uint32 length)"""
    (n,) = struct.unpack_from('<I', data, off)
    return data[off+4:off+4+n], off+4+n

def extract_cert(apk_path):
    with open(apk_path, 'rb') as f:
        data = f.read()
    magic = b'APK Sig Block 42'
    idx = data.rfind(magic)
    if idx < 0:
        raise SystemExit('No APK Signing Block found')
    # footer: [size8][magic]; block layout: [size8][pairs][size8][magic]
    (blk_size,) = struct.unpack_from('<Q', data, idx - 8)
    block_start = idx + 8 - blk_size  # leading size field begins here
    pos = block_start + 8
    pairs_end = idx - 8
    found = {}
    while pos < pairs_end:
        (plen,) = struct.unpack_from('<Q', data, pos)
        (pid,) = struct.unpack_from('<I', data, pos + 8)
        val = data[pos + 12: pos + 8 + plen]
        found[pid] = val
        pos += 8 + plen
    for bid in (0xf05368c0, 0x7109871a):  # v3 first, then v2
        if bid not in found:
            continue
        val = found[bid]
        signers, _ = read_lp(val, 0)          # sequence of signers
        signer, _ = read_lp(signers, 0)       # first signer
        signed_data, _o2 = read_lp(signer, 0) # signed data
        _digests, o3 = read_lp(signed_data, 0)
        certs_seq, _ = read_lp(signed_data, o3)  # sequence of certs
        cert, _ = read_lp(certs_seq, 0)          # first certificate DER
        return cert, bid
    raise SystemExit('No v2/v3 signer block')

cert, bid = extract_cert(sys.argv[1])
fp = hashlib.sha256(cert).hexdigest().upper()
kind = 'v3' if bid == 0xf05368c0 else 'v2'
print(f'scheme: {kind}')
fp_colon = ':'.join(fp[i:i+2] for i in range(0, len(fp), 2))
print(f'cert sha256: {fp_colon}')
