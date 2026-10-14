#!/usr/bin/env python3
"""Verify the selected Player Roles profile against an authorized local game ELF.

Detects ELF32 ARM / ELF64 AArch64, checks the complete pinned binary, all
function and layout signatures, and the relocated vtable entries. Uses only
Python's standard library; the original game is never extracted or packaged.
"""
import argparse
import hashlib
from pathlib import Path
import re
import struct


class Elf:
    def __init__(self, raw):
        self.raw = raw
        if raw[:4] != b'\x7fELF' or raw[5] != 1:
            raise ValueError('Expected a little-endian ELF')
        machine = struct.unpack_from('<H', raw, 18)[0]
        if raw[4] == 2 and machine == 183:
            self.architecture, self.pointer_size = 'arm64', 8
            phoff, shoff = struct.unpack_from('<QQ', raw, 32)
            phsize, phcount, shsize, shcount = struct.unpack_from('<HHHH', raw, 54)
        elif raw[4] == 1 and machine == 40:
            self.architecture, self.pointer_size = 'arm32', 4
            phoff, shoff = struct.unpack_from('<II', raw, 28)
            phsize, phcount, shsize, shcount = struct.unpack_from('<HHHH', raw, 42)
        else:
            raise ValueError('Expected ELF32 ARM or ELF64 AArch64')
        self.segments = []
        for index in range(phcount):
            position = phoff + phsize * index
            if self.pointer_size == 8:
                kind, _, offset, address, _, length, _, _ = struct.unpack_from('<IIQQQQQQ', raw, position)
            else:
                kind, offset, address, _, length, _, _, _ = struct.unpack_from('<IIIIIIII', raw, position)
            if kind == 1:
                self.segments.append((address, offset, length))
        self.relocations = {}
        for index in range(shcount):
            position = shoff + shsize * index
            if self.pointer_size == 8:
                _, kind, _, _, offset, length, _, _, _, entry_size = struct.unpack_from('<IIQQQQIIQQ', raw, position)
                if kind == 4:  # SHT_RELA: AArch64 uses explicit addends.
                    for cursor in range(offset, offset + length, entry_size):
                        target, info, addend = struct.unpack_from('<QQq', raw, cursor)
                        if info & 0xffffffff == 1027:  # R_AARCH64_RELATIVE
                            self.relocations[target] = addend
            else:
                _, kind, _, _, offset, length, _, _, _, entry_size = struct.unpack_from('<IIIIIIIIII', raw, position)
                if kind == 9:  # SHT_REL: ARM stores the addend at the target.
                    for cursor in range(offset, offset + length, entry_size):
                        target, info = struct.unpack_from('<II', raw, cursor)
                        if info & 0xff == 23:  # R_ARM_RELATIVE
                            self.relocations[target] = self.word(target)

    def bytes_at(self, address, size):
        for virtual, offset, length in self.segments:
            if virtual <= address and address + size <= virtual + length:
                position = offset + address - virtual
                return self.raw[position:position + size]
        raise ValueError(f'Address outside file-backed PT_LOAD: {address:#x}')

    def word(self, address):
        return int.from_bytes(self.bytes_at(address, self.pointer_size), 'little')

    def pointer(self, address):
        return self.relocations.get(address, self.word(address))


def verify(library):
    raw = library.read_bytes()
    elf = Elf(raw)
    root = Path(__file__).resolve().parents[1]
    source = (root / 'examples/player-roles/profiles' / (elf.architecture + '.h')).read_text()
    expected = re.search(r'gameSha256 = "([0-9a-f]{64})"', source).group(1)
    expected_size = int(re.search(r'gameSize = (\d+)', source).group(1))
    if len(raw) != expected_size or hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError(f'{elf.architecture.upper()} original game size/SHA256 mismatch')
    bindings = re.findall(r'constexpr Binding (\w+)\{(0x[0-9a-f]+), "([0-9a-f]{32})"\}', source)
    if len(bindings) != 23:
        raise ValueError('Expected exactly 23 named function bindings')
    for name, address, signature in bindings:
        rva = int(address, 16)
        if elf.pointer_size == 4 and not rva & 1:
            raise ValueError(f'{name}: missing Thumb bit')
        if elf.bytes_at(rva & ~1, 16).hex() != signature:
            raise ValueError(f'Function signature mismatch: {name}')
    evidence = re.findall(r'\{(0x[0-9a-f]+), "([0-9a-f]{32})"\}', source.split('constexpr Binding layoutEvidence[] = {', 1)[1].split('};', 1)[0])
    if not evidence:
        raise ValueError('Missing data-layout instruction evidence')
    for address, signature in evidence:
        if elf.bytes_at(int(address, 16), 16).hex() != signature:
            raise ValueError(f'Layout signature mismatch: {address}')
    vtables = re.findall(r'\{(0x[0-9a-f]+), (\d+), (0x[0-9a-f]+)\}', source)
    if len(vtables) != 7:
        raise ValueError('Expected seven vtable slot checks')
    for table, slot, target in vtables:
        address = int(table, 16) + int(slot) * elf.pointer_size
        if elf.pointer(address) != int(target, 16):
            raise ValueError(f'Vtable mismatch: {table} / slot {slot}')
    print(f'{elf.architecture.upper()} profile: OK — SHA256, {len(bindings)} functions, '
          f'{len(evidence)} layout signatures, {len(vtables)} vtable slots')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('library', type=Path)
    args = parser.parse_args()
    try:
        verify(args.library)
    except (ValueError, IndexError, struct.error) as error:
        raise SystemExit(str(error))
