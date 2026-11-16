package dev.sakhno.regnum.nations.chat;

import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;

/** Server-safe native chat components. Never flatten translated or interactive text to strings. */
public final class ChatUi {
    private ChatUi() { }

    public static MutableComponent text(String key, Object... args) {
        Object[] values = new Object[args.length];
        for (int i = 0; i < args.length; i++) values[i] = value(args[i]);
        return Component.translatable(key, values);
    }

    public static MutableComponent value(Object value) {
        MutableComponent result = value instanceof Component component ? component.copy() : Component.literal(String.valueOf(value));
        return result.getStyle().getColor() == null ? result.withStyle(ChatFormatting.GOLD) : result;
    }

    public static MutableComponent heading(Component title) {
        return info(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    public static MutableComponent section(Component title) {
        return Component.empty().append("\n  ").append(title.copy().withStyle(ChatFormatting.GOLD));
    }

    public static MutableComponent line(Component content) {
        return Component.empty().withStyle(ChatFormatting.GRAY).append("\n  ").append(content);
    }

    public static MutableComponent field(String labelKey, Object content) {
        return line(text("chat.regnum-nations.field", Component.translatable(labelKey).withStyle(ChatFormatting.GRAY), value(content)));
    }

    public static MutableComponent info(Component body) { return message(body); }
    public static MutableComponent success(Component body) { return status("success", ChatFormatting.GREEN, body); }
    public static MutableComponent warning(Component body) { return status("warning", ChatFormatting.YELLOW, body); }
    public static MutableComponent error(Component body) {
        // Custom Brigadier errors may already be formatted before reaching a command catch block.
        if (body.getSiblings().size() >= 3 && body.getSiblings().getFirst().getContents() instanceof TranslatableContents prefix
                && prefix.getKey().equals("chat.regnum-nations.prefix")
                && body.getSiblings().get(2).getContents() instanceof TranslatableContents status
                && status.getKey().equals("chat.regnum-nations.error")) return body.copy();
        return status("error", ChatFormatting.RED, body);
    }

    private static MutableComponent status(String kind, ChatFormatting color, Component body) {
        return message(Component.translatable("chat.regnum-nations." + kind, body).withStyle(color));
    }

    private static MutableComponent message(Component body) {
        return Component.empty().withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("chat.regnum-nations.prefix").withStyle(ChatFormatting.GOLD))
                .append(" ").append(body);
    }

    public static MutableComponent run(Component label, String command, Component hint) {
        return action(label, command, hint, ClickEvent.Action.RUN_COMMAND);
    }

    public static MutableComponent suggest(Component label, String command, Component hint) {
        return action(label, command, hint, ClickEvent.Action.SUGGEST_COMMAND);
    }

    private static MutableComponent action(Component label, String command, Component hint, ClickEvent.Action action) {
        if (!command.startsWith("/regnum ")) throw new IllegalArgumentException("Chat actions must use the canonical command root");
        return Component.translatable("chat.regnum-nations.button", label).withStyle(style -> style
                .withColor(ChatFormatting.GOLD).withBold(false).withUnderlined(true)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hint)));
    }

    public static MutableComponent menu() {
        return run(Component.translatable("chat.regnum-nations.menu"), "/regnum menu", Component.translatable("chat.regnum-nations.menu_hint"));
    }

    public static MutableComponent citizenInvitation(String actor, String city, java.util.UUID id) {
        return text("citizen.regnum-nations.invitation", actor, city)
                .append(line(run(Component.translatable("chat.regnum-nations.accept"), "/regnum citizen accept " + id,
                        text("chat.regnum-nations.accept_hint", city))))
                .append("  ").append(run(Component.translatable("chat.regnum-nations.decline"), "/regnum citizen decline " + id,
                        text("chat.regnum-nations.decline_hint", city)));
    }

    /** Only render navigation that has an existing destination. Page numbers are one-based. */
    public static MutableComponent pagination(int page, int pages, IntFunction<String> command) {
        if (page < 1 || pages < page) throw new IllegalArgumentException("Invalid chat page");
        MutableComponent row = line(text("chat.regnum-nations.page", page, pages));
        if (page > 1) row.append("  ").append(run(Component.translatable("chat.regnum-nations.previous"), command.apply(page - 1),
                text("chat.regnum-nations.page_hint", page - 1)));
        if (page < pages) row.append("  ").append(run(Component.translatable("chat.regnum-nations.next"), command.apply(page + 1),
                text("chat.regnum-nations.page_hint", page + 1)));
        return row;
    }

    public static void feedback(CommandSourceStack source, Supplier<Component> body, boolean broadcast) {
        source.sendSuccess(() -> info(body.get()), broadcast);
    }

    public static void confirm(CommandSourceStack source, Supplier<Component> body, boolean broadcast) {
        source.sendSuccess(() -> success(body.get()), broadcast);
    }

    public static void fail(CommandSourceStack source, Component body) { source.sendFailure(error(body)); }

    public static void notify(Player player, Component body, boolean overlay) { player.displayClientMessage(info(body), overlay); }
    public static void warn(Player player, Component body, boolean overlay) { player.displayClientMessage(warning(body), overlay); }
    public static void reject(Player player, Component body, boolean overlay) { player.displayClientMessage(error(body), overlay); }
}
