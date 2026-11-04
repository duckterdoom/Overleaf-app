/**
 * Pure TypeScript stored (uncompressed) ZIP generator.
 * Used to package dropped folder entries or multiple files into a standard ZIP
 * for direct streaming import to the backend without external dependencies.
 */

const crcTable = new Uint32Array(256);
for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
        c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    crcTable[i] = c;
}

function crc32(buf: Uint8Array): number {
    let crc = 0xffffffff;
    for (let i = 0; i < buf.length; i++) {
        crc = crcTable[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
}

export function createStoredZip(
    files: { name: string; data: Uint8Array }[],
): Uint8Array {
    const encoder = new TextEncoder();
    const parts: Uint8Array[] = [];
    const central: Uint8Array[] = [];
    let offset = 0;

    for (const f of files) {
        // Normalize name: no leading slashes, replace backslashes
        const cleanName = f.name.replace(/\\/g, "/").replace(/^\/+/, "");
        const nameBytes = encoder.encode(cleanName);
        const crc = crc32(f.data);
        const size = f.data.length;

        // Local file header (30 bytes + name)
        const local = new Uint8Array(30 + nameBytes.length);
        const lView = new DataView(local.buffer);
        lView.setUint32(0, 0x04034b50, true);
        lView.setUint16(4, 20, true); // version needed: 2.0
        lView.setUint16(6, 0x0800, true); // flags: UTF-8
        lView.setUint16(8, 0, true); // method: 0 = stored
        lView.setUint16(10, 0, true); // mod time
        lView.setUint16(12, 0, true); // mod date
        lView.setUint32(14, crc, true);
        lView.setUint32(18, size, true);
        lView.setUint32(22, size, true);
        lView.setUint16(26, nameBytes.length, true);
        lView.setUint16(28, 0, true); // extra length
        local.set(nameBytes, 30);

        parts.push(local);
        parts.push(f.data);

        // Central directory entry (46 bytes + name)
        const cEntry = new Uint8Array(46 + nameBytes.length);
        const cView = new DataView(cEntry.buffer);
        cView.setUint32(0, 0x02014b50, true);
        cView.setUint16(4, 20, true); // version made by
        cView.setUint16(6, 20, true); // version needed
        cView.setUint16(8, 0x0800, true); // flags: UTF-8
        cView.setUint16(10, 0, true); // method: 0 = stored
        cView.setUint16(12, 0, true);
        cView.setUint16(14, 0, true);
        cView.setUint32(16, crc, true);
        cView.setUint32(20, size, true);
        cView.setUint32(24, size, true);
        cView.setUint16(28, nameBytes.length, true);
        cView.setUint16(30, 0, true); // extra length
        cView.setUint16(32, 0, true); // comment length
        cView.setUint16(34, 0, true); // disk start
        cView.setUint16(36, 0, true); // internal attributes
        cView.setUint32(38, 0, true); // external attributes
        cView.setUint32(42, offset, true); // local header relative offset
        cEntry.set(nameBytes, 46);

        central.push(cEntry);
        offset += local.length + f.data.length;
    }

    const centralStart = offset;
    let centralSize = 0;
    for (const c of central) {
        parts.push(c);
        centralSize += c.length;
    }

    // End of central directory record (22 bytes)
    const eocd = new Uint8Array(22);
    const eView = new DataView(eocd.buffer);
    eView.setUint32(0, 0x06054b50, true);
    eView.setUint16(4, 0, true); // disk number
    eView.setUint16(6, 0, true); // central dir disk
    eView.setUint16(8, files.length, true); // entries on disk
    eView.setUint16(10, files.length, true); // total entries
    eView.setUint32(12, centralSize, true); // central dir size
    eView.setUint32(16, centralStart, true); // central dir offset
    eView.setUint16(20, 0, true); // comment length
    parts.push(eocd);

    const totalLen = parts.reduce((acc, p) => acc + p.length, 0);
    const result = new Uint8Array(totalLen);
    let pos = 0;
    for (const p of parts) {
        result.set(p, pos);
        pos += p.length;
    }
    return result;
}
