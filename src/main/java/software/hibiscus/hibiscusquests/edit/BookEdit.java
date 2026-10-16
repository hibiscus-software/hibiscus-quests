/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One change to the pack's book files, as the editor sends it. Each puts one object in its file form (as the chapter
// files write it), or takes it away when the value is empty: a quest or quest link of a chapter, a chapter's images or
// labels (they have no ids, so the whole list goes), a chapter's own fields (empty deletes the chapter), the chapter
// groups, the book settings, a reward table or one translation. Override copies a mod's chapter into the pack's files. The server
// checks every edit (BookEditor), so a client can't write anything the files couldn't hold.
public sealed interface BookEdit {
    record Quest(String chapter, String id, Optional<CompoundTag> value) implements BookEdit {}

    record Link(String chapter, String id, Optional<CompoundTag> value) implements BookEdit {}

    record Images(String chapter, ListTag value) implements BookEdit {}

    record Labels(String chapter, ListTag value) implements BookEdit {}

    record ChapterInfo(String chapter, Optional<CompoundTag> value) implements BookEdit {}

    record Groups(ListTag value) implements BookEdit {}

    record Settings(CompoundTag value) implements BookEdit {}

    record Table(String id, Optional<CompoundTag> value) implements BookEdit {}

    record Override(String chapter) implements BookEdit {}

    // A translation in the pack's lang/<locale>.snbt: set, or removed when empty.
    record Lang(String locale, String key, Optional<String> text) implements BookEdit {}

    // What the edit touches, for messages and patches: "chapter <id>", "groups", "settings" or "table <id>".
    default String target() {
        return switch (this) {
            case Quest edit -> "chapter " + edit.chapter();
            case Link edit -> "chapter " + edit.chapter();
            case Images edit -> "chapter " + edit.chapter();
            case Labels edit -> "chapter " + edit.chapter();
            case ChapterInfo edit -> "chapter " + edit.chapter();
            case Override edit -> "chapter " + edit.chapter();
            case Groups _ -> "groups";
            case Settings _ -> "settings";
            case Table edit -> "table " + edit.id();
            case Lang edit -> "lang " + edit.locale();
        };
    }

    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_TAG = ByteBufCodecs.optional(ByteBufCodecs.TRUSTED_COMPOUND_TAG);

    StreamCodec<FriendlyByteBuf, BookEdit> STREAM_CODEC = StreamCodec.of(BookEdit::write, BookEdit::read);

    private static void write(FriendlyByteBuf buf, BookEdit edit) {
        switch (edit) {
            case Quest e -> {
                buf.writeByte(0);
                buf.writeUtf(e.chapter());
                buf.writeUtf(e.id());
                OPTIONAL_TAG.encode(buf, e.value());
            }
            case Link e -> {
                buf.writeByte(1);
                buf.writeUtf(e.chapter());
                buf.writeUtf(e.id());
                OPTIONAL_TAG.encode(buf, e.value());
            }
            case Images e -> {
                buf.writeByte(2);
                buf.writeUtf(e.chapter());
                writeList(buf, e.value());
            }
            case Labels e -> {
                buf.writeByte(3);
                buf.writeUtf(e.chapter());
                writeList(buf, e.value());
            }
            case ChapterInfo e -> {
                buf.writeByte(4);
                buf.writeUtf(e.chapter());
                OPTIONAL_TAG.encode(buf, e.value());
            }
            case Groups e -> {
                buf.writeByte(5);
                writeList(buf, e.value());
            }
            case Settings e -> {
                buf.writeByte(6);
                ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, e.value());
            }
            case Table e -> {
                buf.writeByte(7);
                buf.writeUtf(e.id());
                OPTIONAL_TAG.encode(buf, e.value());
            }
            case Override e -> {
                buf.writeByte(8);
                buf.writeUtf(e.chapter());
            }
            case Lang e -> {
                buf.writeByte(9);
                buf.writeUtf(e.locale());
                buf.writeUtf(e.key());
                buf.writeOptional(e.text(), FriendlyByteBuf::writeUtf);
            }
        }
    }

    private static BookEdit read(FriendlyByteBuf buf) {
        int kind = buf.readByte();
        return switch (kind) {
            case 0 -> new Quest(buf.readUtf(), buf.readUtf(), OPTIONAL_TAG.decode(buf));
            case 1 -> new Link(buf.readUtf(), buf.readUtf(), OPTIONAL_TAG.decode(buf));
            case 2 -> new Images(buf.readUtf(), readList(buf));
            case 3 -> new Labels(buf.readUtf(), readList(buf));
            case 4 -> new ChapterInfo(buf.readUtf(), OPTIONAL_TAG.decode(buf));
            case 5 -> new Groups(readList(buf));
            case 6 -> new Settings(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf));
            case 7 -> new Table(buf.readUtf(), OPTIONAL_TAG.decode(buf));
            case 8 -> new Override(buf.readUtf());
            case 9 -> new Lang(buf.readUtf(), buf.readUtf(), buf.readOptional(FriendlyByteBuf::readUtf));
            default -> throw new IllegalArgumentException("Unknown book edit " + kind);
        };
    }

    // A list travels inside a compound ({list: [...]}): the stream codecs only carry compounds.
    private static void writeList(FriendlyByteBuf buf, ListTag list) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("list", list);
        ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, wrapper);
    }

    private static ListTag readList(FriendlyByteBuf buf) {
        return ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf).getListOrEmpty("list");
    }
}
