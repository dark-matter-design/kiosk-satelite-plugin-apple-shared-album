"""Small DEX metadata reader for installable-entry contract checks."""
import struct


def _u32(data, offset):
    return struct.unpack_from('<I', data, offset)[0]


def _uleb128(data, offset):
    value = 0
    shift = 0
    for _ in range(5):
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7f) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
    raise ValueError('Invalid DEX ULEB128 value')


def _table(data, size_offset, item_size):
    size = _u32(data, size_offset)
    offset = _u32(data, size_offset + 4)
    if offset + size * item_size > len(data):
        raise ValueError('DEX table is outside the file')
    return size, offset


def class_interfaces(data):
    """Return {class descriptor: (access flags, interface descriptors)}."""
    if len(data) < 112 or data[:8] not in (b'dex\n035\0', b'dex\n037\0', b'dex\n038\0', b'dex\n039\0', b'dex\n040\0', b'dex\n041\0'):
        raise ValueError('Invalid or unsupported DEX header')
    string_count, string_offset = _table(data, 0x38, 4)
    strings = []
    for index in range(string_count):
        at = _u32(data, string_offset + index * 4)
        _, at = _uleb128(data, at)
        end = data.find(b'\0', at)
        if end < 0:
            raise ValueError('Unterminated DEX string')
        strings.append(data[at:end].decode('utf-8'))
    type_count, type_offset = _table(data, 0x40, 4)
    types = [strings[_u32(data, type_offset + index * 4)] for index in range(type_count)]
    class_count, class_offset = _table(data, 0x60, 32)
    result = {}
    for index in range(class_count):
        at = class_offset + index * 32
        class_index = _u32(data, at)
        access_flags = _u32(data, at + 4)
        interfaces_offset = _u32(data, at + 12)
        interfaces = []
        if interfaces_offset:
            count = _u32(data, interfaces_offset)
            if interfaces_offset + 4 + count * 2 > len(data):
                raise ValueError('DEX interface list is outside the file')
            for item in range(count):
                interfaces.append(types[struct.unpack_from('<H', data, interfaces_offset + 4 + item * 2)[0]])
        result[types[class_index]] = (access_flags, interfaces)
    return result
