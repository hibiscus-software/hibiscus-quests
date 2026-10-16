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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.edit.BookEdit;

// Every payload Hibiscus Quests sends. Server to client: the book's hash, the book in chunks, patches to it after
// edits, progress (all of it, or the quests that changed), and the editor's state. Client to server: asking for the
// book, claims and submits, and the editor's edits, which the server checks (QuestNetwork).
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

    // A toast reward's toast.
    public record Toast(Component title, Component message, ItemStack icon) implements CustomPacketPayload {
        public static final Type<Toast> TYPE = payloadType("toast");
        public static final StreamCodec<RegistryFriendlyByteBuf, Toast> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Toast::title,
                ComponentSerialization.STREAM_CODEC, Toast::message,
                ItemStack.OPTIONAL_STREAM_CODEC, Toast::icon,
                Toast::new);

        @Override
        public Type<Toast> type() {
            return TYPE;
        }
    }

    // The book after an edit, as a patch (compressed NBT, see BookPatch) to the book with hash base; the result has
    // hash book. A client with another book asks for the whole new one instead.
    public record BookPatched(String base, String book, byte[] data) implements CustomPacketPayload {
        public static final Type<BookPatched> TYPE = payloadType("book_patched");
        public static final StreamCodec<ByteBuf, BookPatched> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, BookPatched::base,
                ByteBufCodecs.STRING_UTF8, BookPatched::book,
                ByteBufCodecs.byteArray(QuestSync.CHUNK_SIZE), BookPatched::data,
                BookPatched::new);

        @Override
        public Type<BookPatched> type() {
            return TYPE;
        }
    }

    // Whether the player may use the editor (the top bar shows its button), and whether they're editing now.
    public record EditorState(boolean canEdit, boolean editing) implements CustomPacketPayload {
        public static final Type<EditorState> TYPE = payloadType("editor_state");
        public static final StreamCodec<ByteBuf, EditorState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, EditorState::canEdit,
                ByteBufCodecs.BOOL, EditorState::editing,
                EditorState::new);

        @Override
        public Type<EditorState> type() {
            return TYPE;
        }
    }

    // How an edit went: refused (and why), or saved with new warnings about the book.
    public record EditResult(boolean ok, List<Component> messages) implements CustomPacketPayload {
        public static final Type<EditResult> TYPE = payloadType("edit_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditResult> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, EditResult::ok,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(64)), EditResult::messages,
                EditResult::new);

        @Override
        public Type<EditResult> type() {
            return TYPE;
        }
    }

    // A registry's ids and tags, for the editor's pickers.
    public record RegistryList(String registry, List<String> ids, List<String> tags) implements CustomPacketPayload {
        public static final Type<RegistryList> TYPE = payloadType("registry_list");
        public static final StreamCodec<ByteBuf, RegistryList> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RegistryList::registry,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RegistryList::ids,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RegistryList::tags,
                RegistryList::new);

        @Override
        public Type<RegistryList> type() {
            return TYPE;
        }
    }

    // Show quest text in another locale ("" for the player's own): /hibiscusquests locale.
    public record PreviewLocale(String locale) implements CustomPacketPayload {
        public static final Type<PreviewLocale> TYPE = payloadType("preview_locale");
        public static final StreamCodec<ByteBuf, PreviewLocale> STREAM_CODEC = ByteBufCodecs.stringUtf8(32).map(PreviewLocale::new, PreviewLocale::locale);

        @Override
        public Type<PreviewLocale> type() {
            return TYPE;
        }
    }

    // ---- Client to server ----

    // Edits from the book's editor, applied together or not at all.
    public record Edit(List<BookEdit> edits) implements CustomPacketPayload {
        public static final int MAX_EDITS = 1024;
        public static final Type<Edit> TYPE = payloadType("edit");
        public static final StreamCodec<FriendlyByteBuf, Edit> STREAM_CODEC = BookEdit.STREAM_CODEC
                .apply(ByteBufCodecs.list(MAX_EDITS)).map(Edit::new, Edit::edits);

        @Override
        public Type<Edit> type() {
            return TYPE;
        }
    }

    // Turn the editor on or off for the sender.
    public record SetEditing(boolean editing) implements CustomPacketPayload {
        public static final Type<SetEditing> TYPE = payloadType("set_editing");
        public static final StreamCodec<ByteBuf, SetEditing> STREAM_CODEC = ByteBufCodecs.BOOL.map(SetEditing::new, SetEditing::editing);

        @Override
        public Type<SetEditing> type() {
            return TYPE;
        }
    }

    // "Send me this registry's ids and tags" (editors only).
    public record RegistryRequest(String registry) implements CustomPacketPayload {
        public static final Type<RegistryRequest> TYPE = payloadType("registry_request");
        public static final StreamCodec<ByteBuf, RegistryRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(256)
                .map(RegistryRequest::new, RegistryRequest::registry);

        @Override
        public Type<RegistryRequest> type() {
            return TYPE;
        }
    }

    // "Send me the book with this hash" (the server only answers for its current book).
    public record BookRequest(String hash) implements CustomPacketPayload {
        public static final Type<BookRequest> TYPE = payloadType("book_request");
        public static final StreamCodec<ByteBuf, BookRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(128).map(BookRequest::new, BookRequest::hash);

        @Override
        public Type<BookRequest> type() {
            return TYPE;
        }
    }

    // Claim one reward of a quest ("chapter/quest"), with the option picked for a choice reward (-1 for none).
    public record Claim(String quest, String reward, int choice) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = payloadType("claim");
        public static final StreamCodec<ByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Claim::quest,
                ByteBufCodecs.stringUtf8(128), Claim::reward,
                ByteBufCodecs.VAR_INT, Claim::choice,
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

    // Pin a quest to the player's HUD, or unpin it.
    public record Pin(String quest, boolean pinned) implements CustomPacketPayload {
        public static final Type<Pin> TYPE = payloadType("pin");
        public static final StreamCodec<ByteBuf, Pin> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Pin::quest,
                ByteBufCodecs.BOOL, Pin::pinned,
                Pin::new);

        @Override
        public Type<Pin> type() {
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
