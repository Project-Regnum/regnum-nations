package dev.sakhno.regnum.nations.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Small pages of real registered commands; argument-taking actions suggest editable input. */
public final class CommandHelp {
    public static final int PAGES = 3;
    public static final int CITIZEN_PAGES = 2;
    public static final int ADMIN_PAGES = 3;
    private CommandHelp() { }

    public static Component page(int page, boolean administrator) {
        MutableComponent result = ChatUi.heading(Component.translatable("chat.regnum-nations.help.title"));
        switch (page) {
            case 1 -> {
                result.append(ChatUi.section(Component.translatable("chat.regnum-nations.help.realm")));
                entry(result, "menu", "menu", false);
                entry(result, "kingdom", "kingdom", false);
                entry(result, "city", "city", false);
                entry(result, "found ", "found", true);
            }
            case 2 -> {
                result.append(ChatUi.section(Component.translatable("chat.regnum-nations.help.territory")));
                entry(result, "territory", "inspect_territory", false);
                entry(result, "claim", "claim", false);
                entry(result, "unclaim", "unclaim", false);
                entry(result, "relocate", "relocate", false);
            }
            case 3 -> {
                result.append(ChatUi.section(Component.translatable("chat.regnum-nations.help.people")));
                entry(result, "citizen", "citizen", false);
                entry(result, "citizen list", "citizens", false);
                entry(result, "invite ", "invite", true);
                entry(result, "join ", "join", true);
                if (administrator) entry(result, "admin help", "admin", false);
            }
            default -> throw new IllegalArgumentException("Unknown help page");
        }
        return result.append(ChatUi.pagination(page, PAGES, p -> "/regnum help " + p))
                .append(ChatUi.line(ChatUi.menu()));
    }

    public static Component citizens(int page) {
        MutableComponent result = ChatUi.heading(Component.translatable("chat.regnum-nations.help.citizen_title"));
        switch (page) {
            case 1 -> {
                entry(result, "citizen invite ", "citizen_invite", true);
                entry(result, "citizen accept ", "citizen_accept", true);
                entry(result, "citizen decline ", "citizen_decline", true);
                entry(result, "citizen list", "citizens", false);
            }
            case 2 -> {
                entry(result, "citizen roles", "roles", false);
                entry(result, "citizen permissions", "permissions", false);
                entry(result, "citizen role ", "role", true);
                entry(result, "citizen kick ", "kick", true);
                entry(result, "citizen leave", "leave", true);
            }
            default -> throw new IllegalArgumentException("Unknown citizenship help page");
        }
        return result.append(ChatUi.pagination(page, CITIZEN_PAGES, p -> "/regnum citizen help " + p));
    }

    public static Component administration(int page) {
        MutableComponent result = ChatUi.heading(Component.translatable("chat.regnum-nations.help.administration"));
        switch (page) {
            case 1 -> {
                entry(result, "admin list", "admin_list", false);
                entry(result, "admin settlements", "admin_cities", false);
                entry(result, "admin info ", "admin_info", true);
                entry(result, "admin settlement ", "admin_city", true);
            }
            case 2 -> {
                entry(result, "admin tp ", "admin_tp", true);
                entry(result, "admin rename ", "admin_rename", true);
                entry(result, "admin unclaim ", "admin_unclaim", true);
                entry(result, "admin delete ", "admin_delete", true);
            }
            case 3 -> {
                entry(result, "admin recover city ", "admin_recover_city", true);
                entry(result, "admin recover state ", "admin_recover_state", true);
                entry(result, "warfare cancel ", "admin_cancel_warfare", true);
            }
            default -> throw new IllegalArgumentException("Unknown administrator help page");
        }
        return result.append(ChatUi.pagination(page, ADMIN_PAGES, p -> "/regnum admin help " + p));
    }

    private static void entry(MutableComponent page, String command, String key, boolean suggest) {
        Component description = Component.translatable("chat.regnum-nations.help." + key);
        String input = "/regnum " + command;
        Component hint = Component.translatable("chat.regnum-nations." + (suggest ? "suggest_hint" : "run_hint"), Component.literal(input), description);
        Component action = suggest ? ChatUi.suggest(Component.literal(input.strip()), input, hint)
                : ChatUi.run(Component.literal(input), input, hint);
        page.append(ChatUi.line(action)).append(ChatUi.line(description));
    }
}
