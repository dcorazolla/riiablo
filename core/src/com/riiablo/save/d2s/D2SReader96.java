package com.riiablo.save.d2s;

import io.netty.buffer.ByteBufUtil;
import java.util.Arrays;
import org.apache.logging.log4j.Logger;

import com.badlogic.gdx.utils.Array;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.io.BitInput;
import com.riiablo.io.ByteInput;
import com.riiablo.io.EndOfInput;
import com.riiablo.io.InvalidFormat;
import com.riiablo.io.SignatureMismatch;
import com.riiablo.io.UnsafeNarrowing;
import com.riiablo.item.Item;
import com.riiablo.item.ItemReader;
import com.riiablo.item.Stat;
import com.riiablo.log.Log;
import com.riiablo.log.LogManager;

public class D2SReader96 {
  private static final Logger log = LogManager.getLogger(D2SReader96.class);

  private static final int VERSION = D2S.VERSION_110;

  private static final byte[] SIGNATURE = D2S.SIGNATURE;
  private static final byte[] QUESTS_SIGNATURE = {0x57, 0x6F, 0x6F, 0x21};
  private static final byte[] WAYPOINTS_SIGNATURE = {'W', 'S'};
  private static final byte[] WAYPOINTS_DIFF_SIGNATURE = {0x02, 0x01};
  private static final byte[] NPCS_SIGNATURE = {0x01, 0x77};
  private static final byte[] STATS_SIGNATURE = {0x67, 0x66};
  private static final byte[] SKILLS_SIGNATURE = {0x69, 0x66};
  private static final byte[] ITEMS_SIGNATURE = {0x4A, 0x4D};
  private static final byte[] ITEMS_FOOTER_SIGNATURE = {0x4A, 0x4D, 0x00, 0x00};
  private static final byte[] MERC_SIGNATURE = {0x6A, 0x66};
  private static final byte[] GOLEM_SIGNATURE = {0x6B, 0x66};

  static final int HEADER_SIZE = 0x14F;
  static final int MERC_SIZE = 0x10;
  static final int QUESTS_SIZE = QUESTS_SIGNATURE.length + 4 + 2 + (D2S.QuestData.NUM_QUESTFLAGS * D2S.NUM_DIFFS);
  static final int WAYPOINTS_DIFF_SIZE = WAYPOINTS_DIFF_SIGNATURE.length + D2S.WaypointData.NUM_WAYPOINTFLAGS;
  static final int WAYPOINTS_SIZE = WAYPOINTS_SIGNATURE.length + 4 + 2 + (WAYPOINTS_DIFF_SIZE * D2S.NUM_DIFFS);
  static final int NPCS_SIZE = NPCS_SIGNATURE.length + 2 + (D2S.NPCData.NUM_GREETINGS * D2S.NPCData.NUM_INTROS * D2S.NUM_DIFFS);
  static final int SKILLS_SIZE = SKILLS_SIGNATURE.length + (D2S.SkillData.NUM_TREES * D2S.SkillData.NUM_SKILLS);

  public D2S readD2S(ByteInput in) {
    log.trace("Reading d2s...");
    log.trace("Validating d2s signature");
    in.readSignature(SIGNATURE);
    try {
      D2S d2s = new D2S();
      d2s.version = in.readSafe32u();
      log.debug("version: {}", d2s.version);
      if (d2s.version != VERSION) {
        throw new InvalidFormat(
            in,
            String.format("Unsupported version %d (%s), expected %d (%s)",
                d2s.version, D2S.getVersionString(d2s.version),
                VERSION, D2S.getVersionString(VERSION)));
      }
      return readHeader(in, d2s);
    } catch (UnsafeNarrowing t) {
      throw new InvalidFormat(in, t);
    }
  }

  private static int[] readInts(ByteInput in, int len) {
    int[] ints = new int[len];
    for (int i = 0; i < len; i++) ints[i] = in.read32();
    return ints;
  }

  static D2S readHeader(ByteInput in, D2S d2s) {
    assert d2s.version == VERSION : "d2s.version(" + d2s.version + ") != VERSION(" + VERSION + ")";
    in = in.readSlice(HEADER_SIZE - SIGNATURE.length - 4);
    d2s.size = in.readSafe32u();
    d2s.checksum = in.read32();
    d2s.alternate = in.readSafe32u();
    d2s.name = in.readString(Riiablo.MAX_NAME_LENGTH + 1);
    try {
      Log.put("d2s.name", d2s.name);
      log.debug("name: \"{}\"", d2s.name);
      Log.tracef(log, "checksum: 0x%08X", d2s.checksum);
      d2s.flags = in.read32();
      Log.debugf(log, "flags: 0x%08X [%s]", d2s.flags, d2s.getFlagsString());
      d2s.charClass = in.readSafe8u();
      log.debug("charClass: {} ({})", d2s.charClass, CharacterClass.get(d2s.charClass));
      in.skipBytes(2); // unknown
      d2s.level = in.readSafe8u();
      in.skipBytes(4); // unknown
      d2s.timestamp = in.read32();
      in.skipBytes(4); // unknown
      d2s.hotkeys = readInts(in, D2S.NUM_HOTKEYS);
      if (log.isDebugEnabled()) log.debug("hotkeys: {}", Arrays.toString(d2s.hotkeys));
      d2s.actions = new int[D2S.NUM_ACTIONS][D2S.NUM_BUTTONS];
      for (int i = 0; i < D2S.NUM_ACTIONS; i++) {
        final int[] actions = d2s.actions[i] = readInts(in, D2S.NUM_BUTTONS);
        if (log.isDebugEnabled()) log.debug("actions[{}]: {}", i, Arrays.toString(actions));
      }
      d2s.composites = in.readBytes(COF.Component.NUM_COMPONENTS);
      if (log.isDebugEnabled()) log.debug("composites: {}", ByteBufUtil.hexDump(d2s.composites));
      d2s.colors = in.readBytes(COF.Component.NUM_COMPONENTS);
      if (log.isDebugEnabled()) log.debug("colors: {}", ByteBufUtil.hexDump(d2s.colors));
      d2s.towns = in.readBytes(Riiablo.MAX_DIFFS);
      if (log.isDebugEnabled()) log.debug("towns: {}", ByteBufUtil.hexDump(d2s.towns));
      d2s.mapSeed = in.read32();
      Log.debugf(log, "mapSeed: 0x%08X", d2s.mapSeed);
      try {
        Log.put("d2s.section", "merc");
        d2s.merc = readMercData(in);
      } finally {
        Log.remove("d2s.section");
      }
      in.skipBytes(144); // realm data (unused)
      assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    } finally {
      Log.remove("d2s.name");
    }
    return d2s;
  }

  static D2S.MercData readMercData(ByteInput in) {
    in = in.readSlice(MERC_SIZE);
    D2S.MercData merc = new D2S.MercData();
    merc.flags = in.read32();
    Log.debugf(log, "merc.flags: 0x%08X", merc.flags);
    merc.seed = in.read32();
    Log.debugf(log, "merc.seed: 0x%08X", merc.seed);
    merc.name = in.readSafe16u();
    log.debug("merc.name: {}", merc.name);
    merc.type = in.readSafe16u();
    log.debug("merc.type: {}", merc.type);
    merc.experience = in.readSafe32u();
    log.debug("merc.experience: {}", merc.experience);
    assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    return merc;
  }

  static D2S readRemaining(D2S d2s, ByteInput in, ItemReader itemReader) {
    try {
      Log.put("d2s.name", d2s.name);
      Log.put("d2s.section", "quests");
      d2s.quests = readQuestData(in);
      Log.put("d2s.section", "waypoints");
      d2s.waypoints = readWaypointData(in);
      Log.put("d2s.section", "npcs");
      d2s.npcs = readNPCData(in);
      Log.put("d2s.section", "stats");
      d2s.stats = readStatData(in);
      try {
        in.skipUntil(SKILLS_SIGNATURE);
      } catch (EndOfInput t) {
        throw new InvalidFormat(
            in,
            "skills section " + ByteBufUtil.hexDump(SKILLS_SIGNATURE) + " is missing!",
            t);
      }
      Log.put("d2s.section", "skills");
      d2s.skills = readSkillData(in);
      Log.put("d2s.section", "items");
      d2s.items = readItemData(in, itemReader);
      try {
        log.trace("Validating items footer signature");
        itemReader.skipUntil(in.realign());
        in.readSignature(ITEMS_FOOTER_SIGNATURE);
      } catch (EndOfInput t) {
        throw new InvalidFormat(
            in,
            "items footer " + ByteBufUtil.hexDump(ITEMS_FOOTER_SIGNATURE) + " is missing!",
            t);
      }
      try {
        in.skipUntil(MERC_SIGNATURE);
      } catch (EndOfInput t) {
        throw new InvalidFormat(
            in,
            "merc section " + ByteBufUtil.hexDump(MERC_SIGNATURE) + " is missing!",
            t);
      }
      Log.put("d2s.section", "merc");
      d2s.merc = readMercData(d2s.merc, in, itemReader);
      try {
        in.skipUntil(GOLEM_SIGNATURE);
      } catch (EndOfInput t) {
        throw new InvalidFormat(
            in, "golem section (" + ByteBufUtil.hexDump(GOLEM_SIGNATURE) + ") is missing!",
            t);
      }
      Log.put("d2s.section", "golem");
      d2s.golem = readGolemData(in, itemReader);
    } finally {
      Log.remove("d2s.section");
      Log.remove("d2s.name");
    }
    return d2s;
  }

  static D2S.QuestData readQuestData(ByteInput in) {
    log.trace("Validating quests signature");
    in.readSignature(QUESTS_SIGNATURE);
    D2S.QuestData quests = new D2S.QuestData();
    final int version = in.readSafe32u();
    log.trace("quests.version: {}", version);
    if (version != 6) throw new InvalidFormat(in, "quests.version(" + version + ") != " + 6);
    final short size = in.readSafe16u();
    assert size == QUESTS_SIZE : "quests.size(" + size + ") != QUESTS_SIZE(" + QUESTS_SIZE + ")";
    in = in.readSlice(size - QUESTS_SIGNATURE.length - 4 - 2);
    final byte[][] flags = quests.flags = new byte[D2S.NUM_DIFFS][];
    for (int i = 0; i < D2S.NUM_DIFFS; i++) {
      flags[i] = in.readBytes(D2S.QuestData.NUM_QUESTFLAGS);
      if (log.isDebugEnabled()) {
        Log.debugf(log, "quests.flags[%.4s]: %s",
            Riiablo.getDifficultyString(i),
            ByteBufUtil.hexDump(flags[i]));
      }
    }
    assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    return quests;
  }

  static D2S.WaypointData readWaypointData(ByteInput in) {
    log.trace("Validating waypoints signature");
    in.readSignature(WAYPOINTS_SIGNATURE);
    D2S.WaypointData waypoints = new D2S.WaypointData();
    final int version = in.readSafe32u();
    log.trace("waypoints.version: {}", version);
    if (version != 1) throw new InvalidFormat(in, "waypoints.version(" + version + ") != " + 1);
    final short size = in.readSafe16u();
    assert size == WAYPOINTS_SIZE : "waypoints.size(" + size + ") != WAYPOINTS_SIZE(" + WAYPOINTS_SIZE + ")";
    in = in.readSlice(size - WAYPOINTS_SIGNATURE.length - 4 - 2);
    final byte[][] flags = waypoints.flags = new byte[D2S.NUM_DIFFS][];
    for (int i = 0; i < D2S.NUM_DIFFS; i++) {
      flags[i] = readWaypointFlags(in);
      if (log.isDebugEnabled()) {
        Log.debugf(log, "waypoints.flags[%.4s]: %s",
            Riiablo.getDifficultyString(i),
            ByteBufUtil.hexDump(flags[i]));
      }
    }
    assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    return waypoints;
  }

  static byte[] readWaypointFlags(ByteInput in) {
    in = in.readSlice(WAYPOINTS_DIFF_SIZE);
    log.trace("Validating waypoints.diff signature");
    in.readSignature(WAYPOINTS_DIFF_SIGNATURE);
    final byte[] flags = in.readBytes(D2S.WaypointData.NUM_WAYPOINTFLAGS);
    assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    return flags;
  }

  static D2S.NPCData readNPCData(ByteInput in) {
    log.trace("Validating npcs signature");
    in.readSignature(NPCS_SIGNATURE);
    D2S.NPCData npcs = new D2S.NPCData();
    final short size = in.readSafe16u();
    assert size == NPCS_SIZE : "npcs.size(" + size + ") != NPCS_SIZE(" + NPCS_SIZE + ")";
    in = in.readSlice(size - NPCS_SIGNATURE.length - 2);
    final byte[][][] flags = npcs.flags = new byte[D2S.NPCData.NUM_GREETINGS][D2S.NUM_DIFFS][];
    for (int i = 0; i < D2S.NPCData.NUM_GREETINGS; i++) {
      for (int j = 0; j < D2S.NUM_DIFFS; j++) {
        flags[i][j] = in.readBytes(D2S.NPCData.NUM_INTROS);
        if (log.isDebugEnabled()) {
          Log.debugf(log, "npcs.flags[%s][%.4s]: %s",
              D2S.NPCData.getGreetingString(i),
              Riiablo.getDifficultyString(j),
              ByteBufUtil.hexDump(flags[i][j]));
        }
      }
    }
    assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    return npcs;
  }

  static D2S.StatData readStatData(ByteInput in) {
    log.trace("Validating stats signature");
    in.readSignature(STATS_SIGNATURE);
    D2S.StatData stats = new D2S.StatData();
    BitInput bits = in.unalign();
    for (short id; (id = bits.read15u(9)) != Stat.NONE;) {
      switch (id) {
        case 0:
          stats.strength = bits.read31u(numStatBits(id));
          log.trace("stats.strength: {}", stats.strength);
          break;
        case 1:
          stats.energy = bits.read31u(numStatBits(id));
          log.trace("stats.energy: {}", stats.energy);
          break;
        case 2:
          stats.dexterity = bits.read31u(numStatBits(id));
          log.trace("stats.dexterity: {}", stats.dexterity);
          break;
        case 3:
          stats.vitality = bits.read31u(numStatBits(id));
          log.trace("stats.vitality: {}", stats.vitality);
          break;
        case 4:
          stats.statpts = bits.read31u(numStatBits(id));
          log.trace("stats.statpts: {}", stats.statpts);
          break;
        case 5:
          stats.newskills = bits.read31u(numStatBits(id));
          log.trace("stats.newskills: {}", stats.newskills);
          break;
        case 6:
          stats.hitpoints = bits.read31u(numStatBits(id));
          log.trace("stats.hitpoints: {}", stats.hitpoints);
          break;
        case 7:
          stats.maxhp = bits.read31u(numStatBits(id));
          log.trace("stats.maxhp: {}", stats.maxhp);
          break;
        case 8:
          stats.mana = bits.read31u(numStatBits(id));
          log.trace("stats.mana: {}", stats.mana);
          break;
        case 9:
          stats.maxmana = bits.read31u(numStatBits(id));
          log.trace("stats.maxmana: {}", stats.maxmana);
          break;
        case 10:
          stats.stamina = bits.read31u(numStatBits(id));
          log.trace("stats.stamina: {}", stats.stamina);
          break;
        case 11:
          stats.maxstamina = bits.read31u(numStatBits(id));
          log.trace("stats.maxstamina: {}", stats.maxstamina);
          break;
        case 12:
          stats.level = bits.read31u(numStatBits(id));
          log.trace("stats.level: {}", stats.level);
          break;
        case 13:
          stats.experience = bits.read63u(numStatBits(id));
          log.trace("stats.experience: {}", stats.experience);
          break;
        case 14:
          stats.gold = bits.read31u(numStatBits(id));
          log.trace("stats.gold: {}", stats.gold);
          break;
        case 15:
          stats.goldbank = bits.read31u(numStatBits(id));
          log.trace("stats.goldbank: {}", stats.goldbank);
          break;
        default:
          throw new InvalidFormat(in, "Unexpected stat id: " + id);
      }
    }

    bits.align();
    return stats;
  }

  private static int numStatBits(int stat) {
    if (Riiablo.files != null) {
      return Riiablo.files.ItemStatCost.get(stat).CSvBits;
    }

    log.warn("Riiablo.files is null -- defaulting to internal table");
    switch (stat) {
      case 0: case 1: case 2: case 3: case 4:
        return 10;
      case 5:
        return 8;
      case 6: case 7: case 8: case 9: case 10: case 11:
        return 21;
      case 12:
        return 7;
      case 13:
        return 32;
      case 14: case 15:
        return 25;
      default:
        return 0;
    }
  }

  static D2S.SkillData readSkillData(ByteInput in) {
    log.trace("Validating skills signature");
    in.readSignature(SKILLS_SIGNATURE);
    in = in.readSlice(SKILLS_SIZE - SKILLS_SIGNATURE.length);
    D2S.SkillData skills = new D2S.SkillData();
    skills.skills = in.readBytes(D2S.SkillData.NUM_TREES * D2S.SkillData.NUM_SKILLS);
    if (log.isDebugEnabled()) {
      for (int i = 0, j = 0; i < D2S.SkillData.NUM_TREES; i++, j += D2S.SkillData.NUM_SKILLS) {
        Log.debugf(log, "skills.skills[%d] = %s", i, ByteBufUtil.hexDump(skills.skills, j, D2S.SkillData.NUM_SKILLS));
      }
    }
    assert in.bytesRemaining() == 0 : "in.bytesRemaining(" + in.bytesRemaining() + ") > " + 0;
    return skills;
  }

  static D2S.ItemData readItemData(ByteInput in, ItemReader itemReader) {
    log.trace("Validating items signature");
    in.readSignature(ITEMS_SIGNATURE);
    D2S.ItemData items = new D2S.ItemData();
    final short size = in.readSafe16u();
    Array<Item> itemList = items.items = new Array<>(size);
    log.trace("Reading {} items...", size);
    int errors = 0;
    for (int i = 0; i < size; i++) {
      try {
        Log.put("item", String.valueOf(i));
        final Item item = itemReader.readItem(in);
        itemList.add(item);
      } catch (SignatureMismatch t) {
        log.warn(t);
        i--;
        itemReader.skipUntil(in.realign());
      } catch (InvalidFormat t) {
        log.warn(t);
        errors++;
        itemReader.skipUntil(in.realign());
      } finally {
        Log.remove("item");
      }
    }
    if (errors > 0) {
      log.warn("{} items could not be loaded due to formatting errors.", errors);
    }
    return items;
  }

  static D2S.MercData readMercData(D2S.MercData merc, ByteInput in, ItemReader itemReader) {
    log.trace("Validating merc signature");
    in.readSignature(MERC_SIGNATURE);
    if (merc.seed == 0) return merc;
    // fixme: this is throwing end of stream exception -- something within ByteInput or BitInput isn't back tracking bytes read
    merc.items = readItemData(in, itemReader);
    return merc;
  }

  static D2S.GolemData readGolemData(ByteInput in, ItemReader itemReader) {
    log.trace("Validating golem signature");
    in.readSignature(GOLEM_SIGNATURE);
    D2S.GolemData golem = new D2S.GolemData();
    final BitInput bits = in.unalign();
    golem.exists = bits.readBoolean();
    log.trace("golem.exists: {}", golem.exists);
    if (golem.exists) {
      log.trace("Reading golem item...");
      golem.item = itemReader.readItem(bits.align());
    }
    return golem;
  }
}