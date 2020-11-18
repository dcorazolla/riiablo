// automatically generated by the FlatBuffers compiler, do not modify

package com.riiablo.net.packet.bnls;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class Realm extends Table {
  public static Realm getRootAsRealm(ByteBuffer _bb) { return getRootAsRealm(_bb, new Realm()); }
  public static Realm getRootAsRealm(ByteBuffer _bb, Realm obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; vtable_start = bb_pos - bb.getInt(bb_pos); vtable_size = bb.getShort(vtable_start); }
  public Realm __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String name() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer nameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  public String desc() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer descAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public ByteBuffer descInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 6, 1); }

  public static int createRealm(FlatBufferBuilder builder,
      int nameOffset,
      int descOffset) {
    builder.startObject(2);
    Realm.addDesc(builder, descOffset);
    Realm.addName(builder, nameOffset);
    return Realm.endRealm(builder);
  }

  public static void startRealm(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(0, nameOffset, 0); }
  public static void addDesc(FlatBufferBuilder builder, int descOffset) { builder.addOffset(1, descOffset, 0); }
  public static int endRealm(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}
