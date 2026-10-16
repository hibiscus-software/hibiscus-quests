/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.network;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.hibiscus.hibiscusquests.HibiscusQuests;

// Every payload Hibiscus Quests sends. Server to client: the book's hash, the book in chunks, and progress (all of it,
// or the quests that changed). Client to server: asking for the book, claims and submits, which the server checks
// (QuestNetwork).
public final class QuestPayloads {
    private QuestPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(HibiscusQuests.id(path));
    }

    // ---- Server to client ----

    // The current book's content hash. A client that has a book with this hash (from earlier, or its cache) uses it;
    // otherwise it asks for the book with BookRequest.
    public record BookHash(String hash) implements CustomPacketPayload {
        public static final Type<BookHash> TYPE = payloadType("book_hash");
        public static final StreamCodec<ByteBuf, BookHash> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(BookHash::new, BookHash::hash);

        @Override
        public Type<BookHash> type() {
            return TYPE;
        }
    }

    // One piece of the compressed book, index of count; the client puts them together.
    public record BookChunk(String hash, int index, int count, byte[] data) implements CustomPacketPayload {
        public static final Type<BookChunk> TYPE = payloadType("book_chunk");
        public static final StreamCodec<ByteBuf, BookChunk> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, BookChunk::hash,
                ByteBufCodecs.VAR_INT, BookChunk::index,
                ByteBufCodecs.VAR_INT, BookChunk::count,
                ByteBufCodecs.byteArray(QuestSync.CHUNK_SIZE), BookChunk::data,
                BookChunk::new);

        @Override
        public Type<BookChunk> type() {
            return TYPE;
        }
    }

    // The player's team's progress: everything (full, on login and team changes) or a delta of the quests that changed,
    // with the quests completed just now (for toasts).
    public record Progress(UUID teamId, boolean full, CompoundTag data, List<String> completedNow) implements CustomPacketPayload {
        public static final Type<Progress> TYPE = payloadType("progress");
        public static final StreamCodec<ByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Progress::teamId,
                ByteBufCodecs.BOOL, Progress::full,
                ByteBufCodecs.TRUSTED_COMPOUND_TAG, Progress::data,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Progress::completedNow,
                Progress::new);

        @Override
        public Type<Progress> type() {
            return TYPE;
        }
    }

    // Open the book, on a quest ("chapter/quest") or where the player left it ("").
    public record OpenBook(String quest) implements CustomPacketPayload {
        public static final Type<OpenBook> TYPE = payloadType("open_book");
        public static final StreamCodec<ByteBuf, OpenBook> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(OpenBook::new, OpenBook::quest);

        @Override
        public Type<OpenBook> type() {
            return TYPE;
        }
    }

    // ---- Client to server ----

    // "Send me the book with this hash" (the server only answers for its current book).
    public record BookRequest(String hash) implements CustomPacketPayload {
        public static final Type<BookRequest> TYPE = payloadType("book_request");
        public static final StreamCodec<ByteBuf, BookRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(128).map(BookRequest::new, BookRequest::hash);

        @Override
        public Type<BookRequest> type() {
            return TYPE;
        }
    }

    // Claim one reward of a quest ("chapter/quest").
    public record Claim(String quest, String reward) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = payloadType("claim");
        public static final StreamCodec<ByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Claim::quest,
                ByteBufCodecs.stringUtf8(128), Claim::reward,
                Claim::new);

        @Override
        public Type<Claim> type() {
            return TYPE;
        }
    }

    // Claim everything claimable (the book's Claim all).
    public record ClaimAll() implements CustomPacketPayload {
        public static final ClaimAll INSTANCE = new ClaimAll();
        public static final Type<ClaimAll> TYPE = payloadType("claim_all");
        public static final StreamCodec<ByteBuf, ClaimAll> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<ClaimAll> type() {
            return TYPE;
        }
    }

    // Submit a task (consume items, tick a checkmark).
    public record Submit(String quest, String task) implements CustomPacketPayload {
        public static final Type<Submit> TYPE = payloadType("submit");
        public static final StreamCodec<ByteBuf, Submit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Submit::quest,
                ByteBufCodecs.stringUtf8(128), Submit::task,
                Submit::new);

        @Override
        public Type<Submit> type() {
            return TYPE;
        }
    }
}
