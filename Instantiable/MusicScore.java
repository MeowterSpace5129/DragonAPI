/*******************************************************************************
 * @author Reika Kalseki
 *
 * Copyright 2017
 *
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.DragonAPI.Instantiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import Reika.DragonAPI.Libraries.ReikaNBTHelper.NBTTypes;
import Reika.DragonAPI.Libraries.MathSci.ReikaMusicHelper.MusicKey;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;


public class MusicScore {

	private final TreeMap<Integer, NoteData>[] music;
	private final int channelCount;
	private int length;

	public MusicScore(int channels) {
		channelCount = channels;
		music = new TreeMap[channels];
	}

	public void addNote(int time, int channel, MusicKey note, int voice, int vol, int len, boolean perc) {
		this.addNote(channel, time, new Note(note, voice, vol, len, perc));
	}

	private void addNote(int channel, int time, Note note) {
		if (music[channel] == null) {
			music[channel] = new TreeMap();
		}
		NoteData c = music[channel].get(time);
		if (c == null) {
			c = new NoteData(time);
			music[channel].put(time, c);
		}
		else {
			//ReikaJavaLibrary.pConsole("Adding "+note+" @ C"+channel+" : "+time+" to "+c);
		}
		c.add(note);
		length = Math.max(length, time);
	}

	public Collection<Note> getNotes(int time) {
		Collection<Note> li = new ArrayList();
		for (int i = 0; i < channelCount; i++) {
			NoteData n = music[i] != null ? music[i].get(time) : null;
			if (n != null) {
				li.addAll(n.notes.values());
			}
		}
		return li;
	}

	public Collection<Note> getNotes(int channel, int time) {
		NoteData c = music[channel] != null ? music[channel].get(time) : null;
		return c != null ? Collections.unmodifiableCollection(c.notes.values()) : null;
	}

	public void backspace(int channel) {
		if (music[channel] != null && !music[channel].isEmpty())
			music[channel].remove(music[channel].lastKey());
	}

	public Map<Integer, NoteData> getTrack(int channel) {
		return music[channel] != null ? Collections.unmodifiableMap(music[channel]) : new HashMap();
	}

	public int getLatestPos(int channel) {
		return music[channel] != null && !music[channel].isEmpty() ? music[channel].lastKey() : 0;
	}

	public int getLatestPos() {
		return length;
	}

	public int countTracks() {
		return music.length;
	}

	public MusicScore scaleSpeed(float factor) {
		MusicScore mus = new MusicScore(channelCount);

		for (int i = 0; i < channelCount; i++) {
			if (music[i] != null) {
				for (int time : music[i].keySet()) {
					NoteData c = music[i].get(time);
					if (c != null)
						for (Note n : c.notes.values())
							mus.addNote(i, (int)(time/factor), n);
				}
			}
		}

		return mus;
	}

	public void writeToNBT(NBTTagCompound tag) {
		for (int i = 0; i < channelCount; i++) {
			NBTTagCompound nbt = new NBTTagCompound();
			if (music[i] != null) {
				for (int time : music[i].keySet()) {
					NoteData c = music[i].get(time);
					NBTTagList li = c.writeToNBT();
					nbt.setTag(String.valueOf(time), li);
				}
			}
			tag.setTag("Ch_"+i, nbt);
		}
		tag.setInteger("numchan", channelCount);
	}

	public static MusicScore readFromNBT(NBTTagCompound tag) {
		MusicScore mus = new MusicScore(tag.getInteger("numchan"));

		for (int i = 0; i < mus.channelCount; i++) {
			if (tag.hasKey("Ch_"+i)) {
				mus.music[i] = new TreeMap();
				NBTTagCompound nbt = tag.getCompoundTag("Ch_"+i);
				for (Object o : nbt.func_150296_c()) {
					String s = (String)o;
					int time = Integer.parseInt(s);
					NBTTagList li = nbt.getTagList(s, NBTTypes.COMPOUND.ID);
					NoteData c = NoteData.readFromNBT(time, li);
					mus.music[i].put(time, c);
				}
			}
		}

		return mus;
	}

	@Override
	public String toString() {
		return Arrays.toString(music);
	}

	public void clearChannel(int channel) {
		music[channel] = null;
	}

	@SideOnly(Side.CLIENT)
	public void renderPianoRoll() {

	}

	public MusicScore copy() {
		MusicScore mus = new MusicScore(channelCount);
		for (int i = 0; i < channelCount; i++) {
			if (music[i] != null) {
				mus.music[i] = new TreeMap();
				mus.music[i].putAll(music[i]);
			}
		}
		mus.length = length;
		return mus;
	}

	public static class NoteData {

		private final HashMap<MusicKey, Note> notes = new HashMap();
		private final long tick;

		private NoteData(long t) {
			tick = t;
		}

		private static NoteData readFromNBT(long t, NBTTagList li) {
			NoteData dat = new NoteData(t);
			for (Object o2 : li.tagList) {
				NBTTagCompound val = (NBTTagCompound)o2;
				Note n = Note.readFromNBT(val);
				dat.add(n);
			}
			return dat;
		}

		private NBTTagList writeToNBT() {
			NBTTagList li = new NBTTagList();
			for (Note n : notes.values()) {
				NBTTagCompound val = new NBTTagCompound();
				n.writeToNBT(val);
				li.appendTag(val);
			}
			return li;
		}

		private void add(Note note) {
			notes.put(note.key, note);
		}

		public Collection<Note> notes() {
			return Collections.unmodifiableCollection(notes.values());
		}

		public Set<MusicKey> keys() {
			return Collections.unmodifiableSet(notes.keySet());
		}

	}

	public static class Note {

		public final MusicKey key;

		/** With General MIDI Spec, is 0-127. See MIDI Spec for details */
		public final int voice;

		/** With MIDI Spec, is 0-127 */
		public final int volume;

		/** In MC ticks */
		public final int length;

		public final boolean percussion;

		private Note(MusicKey note, int instru, int vol, int len, boolean perc) {
			key = note;
			voice = instru;
			volume = vol;
			length = len;
			percussion = perc;
		}

		public void writeToNBT(NBTTagCompound nbt) {
			nbt.setInteger("key", key.ordinal());
			nbt.setInteger("volume", volume);
			nbt.setInteger("voice", voice);
			nbt.setInteger("length", length);
			nbt.setBoolean("percussion", percussion);
		}

		public static Note readFromNBT(NBTTagCompound nbt) {
			return new Note(MusicKey.getByIndex(nbt.getInteger("key")), nbt.getInteger("voice"), nbt.getInteger("volume"), nbt.getInteger("length"), nbt.getBoolean("percussion"));
		}

		@Override
		public String toString() {
			return key.name()+" / instr="+voice+" / vol="+volume+" / len="+length;
		}

		@Override
		public int hashCode() {
			return key.ordinal() << 16 | voice << 8 | volume;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Note) {
				Note n = (Note)o;
				return n.key == key && n.voice == voice && n.volume == volume;
			}
			return false;
		}

	}

}
