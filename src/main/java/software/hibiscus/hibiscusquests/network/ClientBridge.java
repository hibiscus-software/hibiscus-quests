/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.network;

import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.client.ClientQuestState;
import software.hibiscus.hibiscusquests.client.screen.QuestBookScreen;

// The step from a payload handler to client code. The handlers are registered on both sides but only ever run on a
// client, and ClientQuestState is only loaded when one of these is called, so a dedicated server never loads it.
final class ClientBridge {
    private ClientBridge() {}

    static void bookHash(QuestPayloads.BookHash payload) {
        ClientQuestState.onBookHash(payload.hash());
    }

    static void bookChunk(QuestPayloads.BookChunk payload) {
        ClientQuestState.onBookChunk(payload);
    }

    static void progress(QuestPayloads.Progress payload) {
        ClientQuestState.onProgress(payload);
    }

    static void toast(QuestPayloads.Toast payload) {
        ClientQuestState.onToast(payload);
    }

    static void openBook(QuestPayloads.OpenBook payload) {
        QuestBookScreen.open(QuestRef.parse(payload.quest()).orElse(null));
    }
}
