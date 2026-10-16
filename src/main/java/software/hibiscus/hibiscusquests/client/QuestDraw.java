/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.book.BookIcon;

// Drawing for the book: icons at any size, quest nodes in each state, progress rings and dependency lines. Colours come
// from Hibiscus Lib's theme (and the book's own accent, when its data.snbt sets one), so a resource pack restyles them.
public final class QuestDraw {
    public static final Identifier CHECK = HibiscusQuests.id("icon/check");
    public static final Identifier LOCK = HibiscusQuests.id("icon/lock");
    public static final Identifier CLAIM_ALL = HibiscusQuests.id("icon/claim_all");
    public static final Identifier LINK = HibiscusQuests.id("icon/link");
    public static final Identifier PANEL_COLLAPSE = HibiscusQuests.id("icon/panel_collapse");
    public static final Identifier PANEL_EXPAND = HibiscusQuests.id("icon/panel_expand");
    public static final Identifier PIN = HibiscusQuests.id("icon/pin");

    // A locked quest's face and edge, and the veil over its icon.
    private static final int LOCKED_FACE = 0xFF26282C;
    private static final int LOCKED_EDGE = 0xFF3A3D44;
    private static final int LOCKED_VEIL = 0xB026282C;

    private QuestDraw() {}

    private static HibiscusTheme theme() {
        return HibiscusTheme.current();
    }

    // The accent: the book's own (data.snbt accent) if it has one, else the theme's.
    public static int accent() {
        return ClientBook.book().settings().accent().map(rgb -> 0xFF000000 | rgb).orElseGet(() -> theme().accent());
    }

    // ---- icons ----

    public static Icon icon(BookIcon icon) {
        return (graphics, x, y) -> icon(graphics, icon, x, y, 16);
    }

    // A book icon (item or texture) with its top-left at x, y, size pixels square.
    public static void icon(GuiGraphicsExtractor graphics, BookIcon icon, float x, float y, float size) {
        if (icon.texture().isPresent()) {
            int s = Math.max(1, Math.round(size));
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture().get(), Math.round(x), Math.round(y), 0, 0, s, s, s, s);
        } else {
            item(graphics, icon.item(), x, y, size);
        }
    }

    public static void item(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(size / 16f, size / 16f);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    // ---- nodes ----

    // A shape's mask and outline sprites: "circle" is hibiscusquests:shape/circle, "mypack:star" is mypack:shape/star.
    public static Identifier shape(String name, boolean outline) {
        String suffix = outline ? "_outline" : "";
        Identifier id = name.indexOf(':') >= 0 ? Identifier.tryParse(name) : Identifier.tryBuild(HibiscusQuests.MODID, name);
        if (id == null) {
            id = HibiscusQuests.id("circle");
        }
        return id.withPath("shape/" + id.getPath() + suffix);
    }

    // Brightness 0..1 of things that pulse (claimable quests), or a steady 1 with reduced motion.
    public static float pulse() {
        if (QuestsClientConfig.get(QuestsClientConfig.REDUCED_MOTION)) {
            return 1;
        }
        long time = Util.getMillis();
        return 0.6f + 0.4f * (float) (Math.sin(time / 260.0) * 0.5 + 0.5);
    }

    // Draws a quest node centred on cx, cy, size pixels across.
    public static void node(GuiGraphicsExtractor graphics, Font font, NodeState state, BookIcon icon, String shapeName, float cx, float cy,
            float size, float fraction, boolean hovered, boolean selected) {
        HibiscusTheme theme = theme();
        int s = Math.max(4, Math.round(size));
        int x = Math.round(cx - s / 2f);
        int y = Math.round(cy - s / 2f);
        int accent = accent();
        int face;
        int edge;
        switch (state) {
            case LOCKED, SECRET -> {
                face = LOCKED_FACE;
                edge = LOCKED_EDGE;
            }
            case CLAIMABLE -> {
                face = mix(theme.color(HibiscusTheme.PANEL), theme.color(HibiscusTheme.GOLD), 0.18f);
                edge = mix(theme.color(HibiscusTheme.PANEL), theme.color(HibiscusTheme.GOLD), pulse());
            }
            case DONE -> {
                face = mix(theme.color(HibiscusTheme.PANEL), accent, 0.22f);
                edge = accent;
            }
            default -> {
                face = theme.color(HibiscusTheme.PANEL_HIGHLIGHT);
                edge = theme.color(HibiscusTheme.TEXT_MUTED);
            }
        }
        if (hovered || selected) {
            edge = mix(edge, 0xFFFFFFFF, 0.45f);
            face = mix(face, 0xFFFFFFFF, 0.08f);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, shape(shapeName, false), x, y, s, s, face);
        float iconSize = s * 0.62f;
        if (state == NodeState.SECRET) {
            float scale = Math.max(1, s / 14f);
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.text(font, "?", -font.width("?") / 2, -4, theme.textMuted(), false);
            graphics.pose().popMatrix();
        } else {
            icon(graphics, icon, cx - iconSize / 2, cy - iconSize / 2, iconSize);
            if (state == NodeState.LOCKED) {
                graphics.nextStratum();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, shape(shapeName, false), x, y, s, s, LOCKED_VEIL);
            }
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, shape(shapeName, true), x, y, s, s, edge);
        if (state == NodeState.IN_PROGRESS) {
            ring(graphics, cx, cy, s / 2f + Math.max(2, s / 10f), fraction, accent, Math.max(1, s / 12f));
        }
        int badge = Math.max(6, s / 3);
        if (state == NodeState.DONE) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHECK, x + s - badge + badge / 4, y + s - badge + badge / 4, badge, badge);
        } else if (state == NodeState.CLAIMABLE) {
            int dot = Math.max(4, s / 5);
            int dx = x + s - dot;
            int dy = y;
            graphics.fill(dx - 1, dy - 1, dx + dot + 1, dy + dot + 1, theme.color(HibiscusTheme.PANEL_SHADOW));
            graphics.fill(dx, dy, dx + dot, dy + dot, theme.color(HibiscusTheme.GOLD));
        }
    }

    // A ring of dots round a centre, fraction of the way round from the top, clockwise.
    public static void ring(GuiGraphicsExtractor graphics, float cx, float cy, float radius, float fraction, int color, float thickness) {
        int segments = Mth.clamp(Math.round(radius * 2.4f), 16, 72);
        int lit = Math.round(segments * Mth.clamp(fraction, 0, 1));
        int track = theme().color(HibiscusTheme.PANEL_SHADOW);
        float t = Math.max(1, thickness);
        for (int i = 0; i < segments; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * (i + 0.5) / segments;
            float px = cx + (float) Math.cos(angle) * radius;
            float py = cy + (float) Math.sin(angle) * radius;
            graphics.pose().pushMatrix();
            graphics.pose().translate(px, py);
            graphics.pose().rotate((float) angle + (float) Math.PI / 2);
            int half = Math.max(1, Math.round((float) (Math.PI * radius / segments) * 0.8f));
            graphics.fill(-half, Math.round(-t / 2), half, Math.max(1, Math.round(t / 2)), i < lit ? color : track);
            graphics.pose().popMatrix();
        }
    }

    // ---- lines ----

    // A straight line from (x0, y0) to (x1, y1), thickness pixels wide. With dashes, it's drawn as dashes moving
    // toward the end (dependency lines leading into a quest that's open), unless reduced motion is on.
    public static void line(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float thickness, int color, boolean dashes) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, y0);
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        int half = Math.max(1, Math.round(thickness / 2));
        int top = -half;
        int bottom = Math.max(1, Math.round(thickness)) - half;
        if (!dashes || QuestsClientConfig.get(QuestsClientConfig.REDUCED_MOTION)) {
            graphics.fill(0, top, Math.round(length), bottom, color);
        } else {
            float dash = Math.max(3, thickness * 3);
            float period = dash * 2;
            float offset = (Util.getMillis() % 1000) / 1000f * period;
            graphics.fill(0, top, Math.round(length), bottom, mix(color, 0x00000000, 0.55f));
            for (float start = offset - period; start < length; start += period) {
                int a = Math.round(Math.max(0, start));
                int b = Math.round(Math.min(length, start + dash));
                if (b > a) {
                    graphics.fill(a, top, b, bottom, color);
                }
            }
        }
        graphics.pose().popMatrix();
    }

    // ---- colours ----

    // Mixes two ARGB colours, t of the way from a to b.
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int aa = a >>> 24;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
