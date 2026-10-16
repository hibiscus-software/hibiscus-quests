/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.IconButton;
import software.hibiscus.hibiscuslib.client.gui.widget.ItemSlotWidget;
import software.hibiscus.hibiscuslib.client.gui.widget.Panel;
import software.hibiscus.hibiscuslib.client.gui.widget.ScrollPanel;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscuslib.client.gui.widget.TextInput;
import software.hibiscus.hibiscuslib.client.gui.widget.ToggleButton;
import software.hibiscus.hibiscusquests.api.editor.FieldHint;
import software.hibiscus.hibiscusquests.api.editor.FieldHint.Kind;
import software.hibiscus.hibiscusquests.book.BookCodecs;
import software.hibiscus.hibiscusquests.book.BookIcon;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.ClientLang;
import software.hibiscus.hibiscusquests.client.QuestDraw;

// A form for one object of the book (a quest, a task, a chapter, the settings...), from a list of fields (Forms). It
// edits a copy of the object's file compound, so fields the form doesn't show (a quest's tasks) are kept as they are.
// A field left empty is left out of the file, which means its default. Save checks the result with the object's own
// codec, exactly as the server will, and shows what's wrong instead of closing. Everything typed lives in fields, so
// the form survives the screen rebuilding when another editor's change arrives.
public final class FormPopup extends Popup {
    private static final int ROW = 22;
    private static final int HEADING = 18;
    private static final int LABEL_MAX = 132;

    private final Component title;
    private final List<Forms.Field> fields;
    private final Codec<?> codec;
    private final String chapter;
    private final Consumer<CompoundTag> onSave;
    private final CompoundTag value;
    // The text in each text field as typed, valid or not, and the fields whose text doesn't parse.
    private final Map<String, String> texts = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    // Translated text the form changed (key to text, in ClientLang's locale): saved to the pack's lang file, not here.
    private final Map<String, String> langChanges = new HashMap<>();
    private @Nullable Component error;
    private double scroll;
    private @Nullable ScrollPanel body;
    private boolean dirty;

    public FormPopup(Component title, List<Forms.Field> fields, CompoundTag value, Codec<?> codec, String chapter, Consumer<CompoundTag> onSave) {
        super(width(), height(fields));
        this.title = title;
        this.fields = List.copyOf(fields);
        this.codec = codec;
        this.chapter = chapter;
        this.onSave = onSave;
        this.value = value.copy();
    }

    private static int width() {
        return Math.min(Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16, 380);
    }

    private static int height(List<Forms.Field> fields) {
        int rows = 0;
        for (Forms.Field field : fields) {
            rows += field.isHeading() ? HEADING : ROW;
        }
        return Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 16, 20 + rows + 8 + 46);
    }

    // ---- layout ----

    @Override
    protected void init() {
        if (body != null) {
            scroll = body.scrollAmount();
        }
        body = new ScrollPanel(getX() + 4, getY() + 20, getWidth() - 8, getHeight() - 20 - 46, Panel.Style.INSET);
        add(body);
        int width = body.contentWidth() - 4;
        int labelWidth = Math.min(LABEL_MAX, width * 2 / 5);
        int y = 3;
        for (Forms.Field field : fields) {
            if (field.isHeading()) {
                body.add(new Caption(width, field.label(), Caption.Style.HEADING), 3, y + 6);
                y += HEADING;
                continue;
            }
            Kind kind = kindOf(field);
            if (kind == Kind.HIDDEN) {
                continue;
            }
            if (kind == Kind.BOOLEAN) {
                ToggleButton toggle = new ToggleButton(0, 0, width, field.label(), booleanValue(field.path()),
                        on -> put(field.path(), ByteTag.valueOf(on)));
                toggle.withTooltip(pathTooltip(field));
                body.add(toggle, 3, y + 3);
                y += ROW;
                continue;
            }
            body.add(new Caption(labelWidth, field.label(), Caption.Style.LABEL).tooltip(pathTooltip(field)), 3, y + 7);
            control(field, kind, labelWidth + 8, y + 2, width - labelWidth - 5);
            y += ROW;
        }
        body.setScrollAmount(scroll);
        int buttonY = getY() + getHeight() - 24;
        add(new TextButton(getX() + 6, buttonY, 64, 18, Component.translatable("gui.cancel"), button -> cancel()));
        add(new TextButton(getX() + getWidth() - 76, buttonY, 70, 18, Component.translatable("hibiscusquests.form.save"),
                button -> save()).accent());
    }

    private static Component pathTooltip(Forms.Field field) {
        return Component.translatable("hibiscusquests.form.in_file", field.path());
    }

    // The control for one field, right of its label.
    private void control(Forms.Field field, @Nullable Kind kind, int x, int y, int width) {
        String path = field.path();
        ScrollPanel panel = body;
        if (panel == null) {
            return;
        }
        if (kind == null) {
            panel.add(textInput(field, null, width), x, y);
            return;
        }
        switch (kind) {
            case CHOICE, OPTIONAL_BOOLEAN -> {
                List<String> options = new ArrayList<>();
                options.add("");
                if (kind == Kind.CHOICE) {
                    options.addAll(field.hint().choices());
                } else {
                    options.add("true");
                    options.add("false");
                }
                String current = choiceValue(path, kind);
                Component label = current.isEmpty() ? Component.translatable("hibiscusquests.form.default")
                        : kind == Kind.OPTIONAL_BOOLEAN ? Component.translatable(current.equals("true") ? "options.on" : "options.off")
                        : Component.literal(current);
                panel.add(new TextButton(0, 0, width, 18, label, button -> {
                    String next = options.get((options.indexOf(current) + 1) % options.size());
                    if (next.isEmpty()) {
                        put(path, null);
                    } else if (kind == Kind.OPTIONAL_BOOLEAN) {
                        put(path, ByteTag.valueOf(next.equals("true")));
                    } else {
                        put(path, StringTag.valueOf(next));
                    }
                    dirty = true;
                }), x, y);
            }
            case LONG_TEXT -> {
                Optional<String> key = translationKey(path, kind);
                String text = key.map(this::translatedText).orElseGet(() -> longText(path));
                String firstLine = text.lines().findFirst().orElse("");
                Component preview = firstLine.isBlank() ? Component.translatable("hibiscusquests.form.empty") : BookText.component(firstLine);
                panel.add(new Caption(width - 56, preview, Caption.Style.MUTED), x, y + 5);
                panel.add(new TextButton(0, 0, 52, 18, Component.translatable("hibiscusquests.form.edit_text"), button -> {
                    if (screen() != null) {
                        screen().openPopup(new RichTextPopup(field.label(), text, chapter, edited -> {
                            if (key.isPresent()) {
                                langChanges.put(key.get(), edited);
                            } else if (edited.isBlank()) {
                                put(path, null);
                            } else {
                                ListTag lines = new ListTag();
                                edited.lines().forEach(line -> lines.add(StringTag.valueOf(line)));
                                put(path, lines);
                            }
                            dirty = true;
                        }));
                    }
                }), x + width - 52, y);
            }
            case ITEM_STACK, ICON -> {
                ItemStack stack = stackOf(get(path));
                panel.add(new ItemSlotWidget(0, 0, stack).onClick(event -> {}), x, y);
                int inputWidth = width - ItemSlotWidget.SIZE - 2 - 40;
                panel.add(textInput(field, kind, inputWidth), x + ItemSlotWidget.SIZE + 2, y);
                panel.add(iconButton(QuestDraw.PICK, "hibiscusquests.form.pick_item", () -> open(PickerPopup.items(field.label(),
                        picked -> setStack(path, picked)))), x + width - 38, y);
                panel.add(iconButton(QuestDraw.HAND, "hibiscusquests.form.held_item", () -> {
                    ItemStack held = heldItem();
                    if (!held.isEmpty()) {
                        setStack(path, held);
                    }
                }), x + width - 18, y);
            }
            case ITEM_LIST -> {
                ListTag list = get(path) instanceof ListTag found ? found : new ListTag();
                int slotX = x;
                int room = (width - 40) / (ItemSlotWidget.SIZE + 1);
                for (int n = 0; n < list.size() && n < room; n++) {
                    int index = n;
                    ItemSlotWidget slot = new ItemSlotWidget(0, 0, stackOf(list.get(n))).onClick(event -> {
                        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                            ListTag changed = list.copy();
                            changed.remove(index);
                            put(path, changed.isEmpty() ? null : changed);
                            dirty = true;
                        }
                    });
                    slot.tooltip(Component.translatable("hibiscusquests.form.remove_item"));
                    panel.add(slot, slotX, y);
                    slotX += ItemSlotWidget.SIZE + 1;
                }
                panel.add(iconButton(QuestDraw.ADD, "hibiscusquests.form.add_item", () -> open(PickerPopup.items(field.label(), picked -> {
                    ListTag changed = list.copy();
                    changed.add(encodeStack(picked));
                    put(path, changed);
                    dirty = true;
                }))), x + width - 18, y);
            }
            case ID, ID_OR_TAG, TAG, QUEST, REWARD_TABLE -> {
                panel.add(textInput(field, kind, width - 20), x, y);
                panel.add(iconButton(QuestDraw.PICK, "hibiscusquests.form.pick", () -> open(picker(field, kind))), x + width - 18, y);
            }
            case COLOR -> {
                panel.add(textInput(field, kind, width - 20), x, y);
                int color = colorOf(get(path));
                panel.add(new IconButton(0, 0, 18, Icon.color(0xFF000000 | color), Component.translatable("hibiscusquests.form.pick_color"),
                        button -> open(new ColorPopup(field.label(), color, picked -> setText(path, kind,
                                String.format(Locale.ROOT, "#%06x", picked & 0xFFFFFF))))), x + width - 18, y);
            }
            default -> panel.add(textInput(field, kind, width), x, y);
        }
    }

    private TextInput textInput(Forms.Field field, @Nullable Kind kind, int width) {
        String path = field.path();
        TextInput input = new TextInput(0, 0, width, 18, field.label());
        input.setMaxLength(32767);
        Optional<String> key = translationKey(path, kind);
        input.setValue(texts.getOrDefault(path, key.map(this::translatedText).orElseGet(() -> toText(get(path), kind))));
        input.placeholder(kind == Kind.STRING_LIST ? Component.translatable("hibiscusquests.form.list_hint")
                : Component.translatable("hibiscusquests.form.default"));
        input.validator(text -> parse(kind, text).ok());
        input.setResponder(text -> {
            texts.put(path, text);
            if (key.isPresent()) {
                langChanges.put(key.get(), text);
            } else {
                apply(path, kind, text);
            }
        });
        if (key.isPresent()) {
            input.setTooltip(Tooltip.create(Component.translatable("hibiscusquests.form.translated", key.get(), ClientLang.locale())));
        }
        return input;
    }

    // The pack translation key a text field holds ({translate:key} or a bare key the pack translates), if it does:
    // the form then edits the translation, in the locale the book is shown in.
    private Optional<String> translationKey(String path, @Nullable Kind kind) {
        if (kind != Kind.TEXT && kind != Kind.LONG_TEXT) {
            return Optional.empty();
        }
        Tag tag = get(path);
        if (tag instanceof ListTag list && list.size() == 1) {
            tag = list.get(0);
        }
        return tag == null ? Optional.empty() : tag.asString().flatMap(BookText::keyOf).filter(EditActions::isPackKey);
    }

    private String translatedText(String key) {
        if (langChanges.containsKey(key)) {
            return langChanges.get(key);
        }
        return ClientBook.book().translation(ClientLang.locale(), key)
                .or(() -> ClientBook.book().translation(BookText.DEFAULT_LOCALE, key)).orElse("");
    }

    private IconButton iconButton(Identifier sprite, String tooltip, Runnable action) {
        return new IconButton(0, 0, 18, Icon.sprite(sprite), Component.translatable(tooltip), button -> action.run());
    }

    private void open(Popup popup) {
        if (screen() != null) {
            screen().openPopup(popup);
        }
    }

    // The picker for an id-like field.
    private PickerPopup picker(Forms.Field field, Kind kind) {
        String path = field.path();
        Consumer<String> set = picked -> setText(path, kind, picked);
        if (kind == Kind.QUEST) {
            List<PickerPopup.Entry> entries = new ArrayList<>();
            for (Quest quest : ClientBook.book().quests()) {
                String ref = quest.chapter().equals(chapter) ? quest.id() : quest.ref().toString();
                entries.add(new PickerPopup.Entry(ref, BookText.titleOr(quest.title(), quest.id()), Component.literal(quest.ref().toString()),
                        QuestDraw.icon(ClientBook.icon(quest))));
            }
            return PickerPopup.of(field.label(), entries, set);
        }
        if (kind == Kind.REWARD_TABLE) {
            List<PickerPopup.Entry> entries = new ArrayList<>();
            for (RewardTable table : ClientBook.book().tables()) {
                entries.add(new PickerPopup.Entry(table.id(), BookText.titleOr(table.title(), table.id()), Component.literal(table.id()),
                        table.icon().map(QuestDraw::icon).orElse(Icon.sprite(QuestDraw.TABLE))));
            }
            return PickerPopup.of(field.label(), entries, set);
        }
        FieldHint hint = field.hint();
        return PickerPopup.registry(field.label(), hint.registry().orElseThrow(), kind, set);
    }

    // ---- values ----

    // A field's control: its hint's, else one that suits the value it has (a text box for SNBT when nothing does).
    private @Nullable Kind kindOf(Forms.Field field) {
        if (field.hint() != null) {
            return field.hint().kind();
        }
        Tag tag = get(field.path());
        if (tag instanceof StringTag) {
            return Kind.TEXT;
        }
        if (tag instanceof ByteTag b && (b.byteValue() == 0 || b.byteValue() == 1)) {
            return Kind.BOOLEAN;
        }
        if (tag instanceof DoubleTag || tag instanceof FloatTag) {
            return Kind.DECIMAL;
        }
        if (tag instanceof NumericTag) {
            return Kind.INTEGER;
        }
        return null;
    }

    private @Nullable Tag get(String path) {
        return get(value, path);
    }

    private static @Nullable Tag get(CompoundTag root, String path) {
        String[] parts = path.split("\\.");
        CompoundTag compound = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Optional<CompoundTag> next = compound.getCompound(parts[i]);
            if (next.isEmpty()) {
                return null;
            }
            compound = next.get();
        }
        return compound.get(parts[parts.length - 1]);
    }

    private void put(String path, @Nullable Tag tag) {
        put(value, path, tag);
        error = null;
    }

    // Sets a value at a path, making the compounds on the way; null removes it, and compounds left empty go too.
    private static void put(CompoundTag root, String path, @Nullable Tag tag) {
        String[] parts = path.split("\\.");
        List<CompoundTag> chain = new ArrayList<>();
        CompoundTag compound = root;
        chain.add(compound);
        for (int i = 0; i < parts.length - 1; i++) {
            CompoundTag next = compound.getCompound(parts[i]).orElse(null);
            if (next == null) {
                if (tag == null) {
                    return;
                }
                next = new CompoundTag();
                compound.put(parts[i], next);
            }
            compound = next;
            chain.add(compound);
        }
        if (tag == null) {
            compound.remove(parts[parts.length - 1]);
            for (int i = chain.size() - 1; i > 0 && chain.get(i).isEmpty(); i--) {
                chain.get(i - 1).remove(parts[i - 1]);
            }
        } else {
            compound.put(parts[parts.length - 1], tag);
        }
    }

    private void setText(String path, @Nullable Kind kind, String text) {
        texts.put(path, text);
        apply(path, kind, text);
        dirty = true;
    }

    private void apply(String path, @Nullable Kind kind, String text) {
        Parsed parsed = parse(kind, text);
        if (parsed.ok()) {
            invalid.remove(path);
            put(path, parsed.tag());
        } else {
            invalid.add(path);
        }
    }

    // A text field's text as a value: ok with the tag (null for empty: left out of the file), or not ok.
    private record Parsed(boolean ok, @Nullable Tag tag) {
        static final Parsed EMPTY = new Parsed(true, null);
        static final Parsed BAD = new Parsed(false, null);
    }

    private static Parsed parse(@Nullable Kind kind, String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty() && kind != Kind.TEXT) {
            return Parsed.EMPTY;
        }
        if (kind == null) {
            try {
                return new Parsed(true, TagParser.create(NbtOps.INSTANCE).parseFully(trimmed));
            } catch (CommandSyntaxException e) {
                return Parsed.BAD;
            }
        }
        try {
            return switch (kind) {
                case TEXT -> text.isEmpty() ? Parsed.EMPTY : new Parsed(true, StringTag.valueOf(text));
                case INTEGER -> new Parsed(true, LongTag.valueOf(Long.parseLong(trimmed)));
                case DECIMAL -> new Parsed(true, DoubleTag.valueOf(Double.parseDouble(trimmed)));
                case STRING_LIST -> {
                    ListTag list = new ListTag();
                    BookCodecs.splitList(trimmed).forEach(entry -> list.add(StringTag.valueOf(entry)));
                    yield list.isEmpty() ? Parsed.EMPTY : new Parsed(true, list);
                }
                case COLOR -> {
                    String hex = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
                    yield hex.matches("[0-9a-fA-F]{6}") ? new Parsed(true, StringTag.valueOf("#" + hex.toLowerCase(Locale.ROOT))) : Parsed.BAD;
                }
                case ITEM_STACK, ICON -> trimmed.startsWith("{") ? new Parsed(true, TagParser.parseCompoundFully(trimmed))
                        : Identifier.tryParse(trimmed) != null ? new Parsed(true, StringTag.valueOf(trimmed)) : Parsed.BAD;
                case ID, QUEST, REWARD_TABLE -> trimmed.matches("[a-z0-9_.:/-]+") ? new Parsed(true, StringTag.valueOf(trimmed)) : Parsed.BAD;
                case ID_OR_TAG, TAG -> trimmed.matches("#?[a-z0-9_.:/-]+") ? new Parsed(true, StringTag.valueOf(trimmed)) : Parsed.BAD;
                default -> new Parsed(true, StringTag.valueOf(trimmed));
            };
        } catch (NumberFormatException | CommandSyntaxException e) {
            return Parsed.BAD;
        }
    }

    // A value as its field's text.
    private static String toText(@Nullable Tag tag, @Nullable Kind kind) {
        if (tag == null) {
            return "";
        }
        if (tag instanceof StringTag string) {
            return string.value();
        }
        if (kind == Kind.STRING_LIST && tag instanceof ListTag list) {
            List<String> parts = new ArrayList<>();
            list.forEach(entry -> parts.add(entry.asString().orElse(entry.toString())));
            return String.join(", ", parts);
        }
        if (tag instanceof NumericTag && kind == Kind.INTEGER) {
            return Long.toString(tag.asLong().orElse(0L));
        }
        if (tag instanceof NumericTag && kind == Kind.DECIMAL) {
            double number = tag.asDouble().orElse(0.0);
            return number == Math.rint(number) && Math.abs(number) < 1e15 ? Long.toString((long) number) : Double.toString(number);
        }
        return tag.toString();
    }

    private String longText(String path) {
        Tag tag = get(path);
        if (tag instanceof ListTag list) {
            List<String> lines = new ArrayList<>();
            list.forEach(line -> lines.add(line.asString().orElse("")));
            return String.join("\n", lines);
        }
        return tag == null ? "" : tag.asString().orElse("");
    }

    private String choiceValue(String path, Kind kind) {
        Tag tag = get(path);
        if (tag == null) {
            return "";
        }
        if (kind == Kind.OPTIONAL_BOOLEAN) {
            return tag.asBoolean().orElse(false) ? "true" : "false";
        }
        return tag.asString().orElse("");
    }

    // A toggle's value: what's set, else the codec's default (found by asking the codec whether false is left out).
    private boolean booleanValue(String path) {
        Tag tag = get(path);
        if (tag != null) {
            return tag.asBoolean().orElse(false);
        }
        CompoundTag probe = value.copy();
        put(probe, path, ByteTag.valueOf(false));
        Optional<Tag> written = roundTrip(codec, probe, ClientEditor.ops());
        return written.isPresent() && written.get() instanceof CompoundTag compound && get(compound, path) != null;
    }

    private static <T> Optional<Tag> roundTrip(Codec<T> codec, Tag tag, DynamicOps<Tag> ops) {
        DataResult<T> decoded = codec.parse(ops, tag);
        return decoded.result().flatMap(result -> codec.encodeStart(ops, result).result());
    }

    private static ItemStack stackOf(@Nullable Tag tag) {
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        return BookIcon.CODEC.parse(ClientEditor.ops(), tag).result().map(BookIcon::item).orElse(ItemStack.EMPTY);
    }

    private void setStack(String path, ItemStack stack) {
        texts.remove(path);
        invalid.remove(path);
        put(path, encodeStack(stack));
        dirty = true;
    }

    // An item as the files write it: its id, or the stack (one of it) when it has components.
    public static Tag encodeStack(ItemStack stack) {
        ItemStack one = stack.copyWithCount(1);
        if (one.getComponentsPatch().isEmpty()) {
            return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(one.getItem()).toString());
        }
        return ItemStack.CODEC.encodeStart(ClientEditor.ops(), one).result().orElse(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(one.getItem()).toString()));
    }

    private static ItemStack heldItem() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
    }

    private static int colorOf(@Nullable Tag tag) {
        String text = tag == null ? "" : tag.asString().orElse("");
        try {
            return text.startsWith("#") ? Integer.parseInt(text.substring(1), 16) : HibiscusTheme.current().accent() & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    // ---- saving ----

    private void save() {
        if (!invalid.isEmpty()) {
            error = Component.translatable("hibiscusquests.form.fix_fields");
            return;
        }
        CompoundTag result = value.copy();
        Optional<String> problem = check(codec, result);
        if (problem.isPresent()) {
            error = Component.literal(problem.get());
            return;
        }
        close();
        if (langChanges.isEmpty()) {
            onSave.accept(result);
        } else {
            EditActions.withLang(Map.copyOf(langChanges), () -> onSave.accept(result));
        }
    }

    private static <T> Optional<String> check(Codec<T> codec, CompoundTag tag) {
        return codec.parse(ClientEditor.ops(), tag).error().map(DataResult.Error::message);
    }

    // ---- drawing ----

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (dirty) {
            dirty = false;
            clear();
            init();
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        Font font = Minecraft.getInstance().font;
        HibiscusTheme theme = HibiscusTheme.current();
        graphics.text(font, title, getX() + 6, getY() + 7, theme.text(), false);
        if (error != null) {
            int y = getY() + getHeight() - 45;
            for (FormattedCharSequence line : font.split(error, getWidth() - 12).stream().limit(2).toList()) {
                graphics.text(font, line, getX() + 6, y, theme.color(HibiscusTheme.NEGATIVE), false);
                y += 10;
            }
        }
    }
}
