package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.CommandDispatcher;
import dev.sakhno.regnum.nations.chat.ChatUi;
import dev.sakhno.regnum.nations.chat.CommandHelp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Standalone regressions for real Minecraft text/Brigadier; never starts Minecraft or a world. */
public final class ChatUiTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        KingdomCommands.register(dispatcher);
        var output = new Output();
        var source = new CommandSourceStack(output, Vec3.ZERO, Vec2.ZERO, null, 0, "ChatTest", Component.empty(), null, null);
        var root = dispatcher.getRoot().getChild("regnum");
        require(root != null, "Canonical root missing");
        for (String alias : List.of("reigns", "nations")) {
            require(dispatcher.getRoot().getChild(alias).getRedirect() == root, "Compatibility alias must share one tree");
        }
        for (String command : List.of("city", "kingdom", "territory", "menu", "admin")) {
            require(root.getChild(command) != null, "Missing command " + command);
        }
        for (String command : List.of("regnum", "nations", "reigns")) {
            require(dispatcher.execute(command + " help", source) == 1, "Help must execute without OP");
            require(!valid(dispatcher, command + " admin help", source), "Alias bypasses administrator gate");
            require(valid(dispatcher, command + " admin help", source.withPermission(3)), "Authorized admin route lost");
            var roles=dispatcher.getCompletionSuggestions(dispatcher.parse(command+" citizen role Example ",source)).get().getList()
                    .stream().map(s -> s.getText()).collect(java.util.stream.Collectors.toSet());
            require(roles.equals(java.util.Set.of("officer","citizen")), "Role suggestions changed through " + command);
            require(!valid(dispatcher, command + " warfare cancel " + UUID.randomUUID(), source), "Warfare gate lost");
        }
        require(!valid(dispatcher, "regnum help 0", source) && !valid(dispatcher, "regnum help 4", source), "Help bounds lost");
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("regnum ", source)).get().getList();
        require(suggestions.stream().anyMatch(s -> s.getText().equals("help")), "Help suggestion missing");
        require(!root.getChild("admin").canUse(source) && root.getChild("admin").canUse(source.withPermission(3)), "Admin visibility gate lost");

        Language original = Language.getInstance();
        try {
            for (String locale : List.of("en_us", "uk_ua")) {
                installLanguage(locale, original);
                for (int page = 1; page <= CommandHelp.PAGES; page++) {
                    require(dispatcher.execute("regnum help " + page, source) == 1, "Help page failed");
                    Component help = output.messages.getLast();
                    require(help.getString().startsWith("[Regnum] "), "Missing prefix");
                    require(!help.getString().contains(".regnum-nations."), "Unresolved translation in " + locale);
                    require(!help.getString().contains("/reigns") && !help.getString().contains("/nations"), "Noncanonical public command");
                    checkActions(dispatcher, help, source);
                    long navigation = actions(help).stream().filter(s -> s.getClickEvent().getValue().startsWith("/regnum help ")).count();
                    require(navigation == (page == 2 ? 2 : 1), "Pagination rendered an invalid destination");
                    checkActions(dispatcher, CommandHelp.page(page, true), source.withPermission(3));
                }
                for (int page=1;page<=CommandHelp.ADMIN_PAGES;page++) {
                    Component help=CommandHelp.administration(page);
                    require(!help.getString().contains(".regnum-nations."), "Missing administrator translation");
                    checkActions(dispatcher,help,source.withPermission(3));
                }
                for (int page=1;page<=CommandHelp.CITIZEN_PAGES;page++) {
                    require(dispatcher.execute("regnum citizen help " + page,source)==1, "Citizen help failed");
                    Component help=output.messages.getLast();
                    require(!help.getString().contains(".regnum-nations."), "Missing citizenship translation");
                    checkActions(dispatcher,help,source);
                }
                String translated = ChatUi.success(Component.translatable("territory.regnum-nations.success")).getString();
                require(translated.contains(locale.equals("uk_ua") ? "Успіх:" : "Success:"), "Status must have a translated text label");
                require(ChatUi.warning(Component.empty()).getString().contains(locale.equals("uk_ua") ? "Увага:" : "Warning:"), "Warning label lost");
                require(ChatUi.error(Component.empty()).getString().contains(locale.equals("uk_ua") ? "Помилка:" : "Error:"), "Error label lost");
                var invitation = ChatUi.info(ChatUi.citizenInvitation("Sender", "Кам’янець", UUID.randomUUID()));
                require(invitation.getString().contains("Кам’янець"), "Ukrainian name corrupted");
                checkActions(dispatcher, invitation, source);
                var kingdom = new Kingdom(UUID.randomUUID(), new KingdomName("Golden Vale"), UUID.randomUUID(),
                        net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO, UUID.randomUUID(), 0L, Kingdom.CapitalStatus.PRESENT);
                var token = UUID.randomUUID();
                Component deletion = KingdomDeletionPrompt.create(kingdom, token, false);
                checkActions(dispatcher, deletion, source.withPermission(3));
                List<Style> destructive = actions(deletion).stream().filter(s -> s.getClickEvent().getAction() == ClickEvent.Action.RUN_COMMAND).toList();
                require(destructive.size() == 2, "Deletion needs exactly confirm and cancel");
                require(destructive.getFirst().isBold() && destructive.getFirst().getColor().getValue() == ChatFormatting.RED.getColor(), "Danger style lost");
                require(destructive.getFirst().getClickEvent().getValue().equals(KingdomDeletionPrompt.command(kingdom.id(), "confirm", token)), "Confirmation token changed");
            }
        } finally { Language.inject(original); }
        var once=ChatUi.error(Component.literal("failure"));
        require(ChatUi.error(once).getString().equals(once.getString()), "Custom errors acquire duplicate prefixes");
        for (String command : List.of("/regnum unclaim confirm", "/regnum relocate confirm " + UUID.randomUUID(),
                "/regnum admin info " + UUID.randomUUID(), "/regnum admin settlement " + UUID.randomUUID(),
                "/regnum join " + UUID.randomUUID())) {
            checkActions(dispatcher, ChatUi.run(Component.literal("Action"), command, Component.literal("Hint")), source.withPermission(3));
        }
        for (String list : List.of("admin list", "admin settlements", "citizen list")) {
            checkActions(dispatcher, ChatUi.pagination(2, 3, p -> "/regnum " + list + " " + p), source.withPermission(3));
        }
        var value = Component.literal("Aurelia");
        ChatUi.text("test", value);
        require(value.getStyle().equals(Style.EMPTY), "Formatting mutates supplied text");
        require(ChatUi.value("12, -8").getStyle().getColor().getValue() == ChatFormatting.GOLD.getColor(), "Value accent lost");
        require(actions(ChatUi.pagination(1, 1, p -> "/regnum help " + p)).isEmpty(), "Single page has navigation");
        System.out.println("Chat UI checks passed: native components, EN/UK, actions, canonical routing, aliases, permissions and pagination (Java " + Runtime.version().feature() + ")");
    }

    private static boolean valid(CommandDispatcher<CommandSourceStack> dispatcher, String input, CommandSourceStack source) {
        var parsed = dispatcher.parse(input, source);
        var context = parsed.getContext();
        while (context.getChild() != null) context = context.getChild();
        return !parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && context.getCommand() != null;
    }

    private static void checkActions(CommandDispatcher<CommandSourceStack> dispatcher, Component message, CommandSourceStack source) {
        for (Style style : actions(message)) {
            String command = style.getClickEvent().getValue();
            require(command.startsWith("/regnum "), "Action has obsolete root");
            require(style.getHoverEvent() != null, "Action lacks a tooltip");
            if (style.getClickEvent().getAction() == ClickEvent.Action.RUN_COMMAND) {
                require(valid(dispatcher, command.substring(1), source), "Nonfunctional button " + command);
            } else if (command.endsWith(" ")) {
                var parsed=dispatcher.parse(command.substring(1).strip(),source);
                require(!parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Unregistered suggestion prefix " + command);
            } else {
                require(valid(dispatcher, command.substring(1), source), "Invalid suggestion " + command);
            }
        }
    }

    private static List<Style> actions(Component component) {
        List<Style> actions = new ArrayList<>();
        component.visit((style, text) -> {
            if (style.getClickEvent() != null && !actions.contains(style)) actions.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        return actions;
    }

    private static void installLanguage(String locale, Language original) throws Exception {
        Map<String, String> values = new HashMap<>();
        try (var stream = ChatUiTest.class.getResourceAsStream("/assets/regnum-nations/lang/" + locale + ".json")) {
            require(stream != null, "Missing locale " + locale);
            Language.loadFromJson(stream, values::put);
        }
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return values.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return original.getVisualOrder(text); }
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Output implements CommandSource {
        final List<Component> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
