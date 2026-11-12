package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.api.v1.*;
import dev.sakhno.regnum.nations.api.client.v1.*;
import dev.sakhno.regnum.nations.networking.EconomyNavigationPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Nations owns political identity/navigation; registered providers own every financial screen. */
public final class EconomyScreen extends RoyalScreen {
    private final Screen parent;
    private final EntityRef target;
    private EconomyContext context;
    private EconomyPanel.Category category;
    private FolioBounds bounds;
    private Component feedback = message("loading");
    private EditBox search;
    private String query = "";
    private int sequence, page, rows;
    private long sent, received;
    private boolean pending, failed;
    public EconomyScreen(Screen parent, EntityRef target) { super(message("title")); this.parent = parent; this.target = Objects.requireNonNull(target); }
    public static Component message(String key, Object... args) { return Component.translatable("economy.regnum-nations." + key, args); }
    private static Component category(EconomyPanel.Category category) { return message("category." + category.name().toLowerCase(Locale.ROOT)); }
    private List<EconomyPanel> matchingPanels() {
        if (context == null) return List.of();
        String filter = query.strip().toLowerCase(Locale.ROOT);
        return EconomyUi.panels(context).stream().filter(p -> p.category() == category && p.title().getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private boolean ready() { return context != null && sent == 0 && !pending && !failed; }
    @Override protected void init() {
        bounds = folioBounds(width, height, 680, 410);
        int left = bounds.left(), top = bounds.y(), content = bounds.contentWidth();
        addRenderableWidget(new RoyalButton(left, bounds.footerY(), 80, text("refresh"), b -> read()));
        addRenderableWidget(new RoyalButton(bounds.right() - 80, bounds.footerY(), 80, text("back_kingdom"), b -> onClose()));
        int body = top + 108;
        if (context != null) {
            var categories = Arrays.stream(EconomyPanel.Category.values()).filter(c -> EconomyUi.panels(context).stream().anyMatch(p -> p.category() == c)).toList();
            int tabWidth = (content - GAP * categories.size()) / (categories.size() + 1);
            var overview = addRenderableWidget(new RoyalButton(left, top + 80, tabWidth, message("overview"), b -> { category = null; page = 0; rebuildWidgets(); }));
            overview.selected(category == null);
            for (int i = 0; i < categories.size(); i++) {
                var value = categories.get(i);
                var tab = addRenderableWidget(new RoyalButton(left + (i + 1) * (tabWidth + GAP), top + 80, tabWidth, category(value),
                        b -> { category = value; page = 0; query = ""; rebuildWidgets(); }));
                tab.selected(category == value); tab.active = ready();
            }
            if (category == null) {
                var lines = new ArrayList<Component>();
                lines.add(message("role", message("role." + context.role().toLowerCase(Locale.ROOT))));
                lines.add(text("identity_uuid", context.entity().ref().id()));
                if (context.entity().independentCity()) lines.add(text("independent_city"));
                lines.add(message("permissions"));
                if (context.permissions().isEmpty()) lines.add(message("no_permissions"));
                context.permissions().stream().sorted().forEach(p -> lines.add(message("permission." + p.name().toLowerCase(Locale.ROOT))));
                addRenderableWidget(new Readout(font, left, body, content - GAP, Math.max(CONTROL, bounds.footerY() - body - CONTROL - GAP * 2), lines));
            } else {
                search = addRenderableWidget(field(font, left, body, content, message("search")));
                search.setMaxLength(64); search.setValue(query); search.setHint(message("search"));
                search.setResponder(value -> { query = value; page = 0; });
                addRenderableWidget(new RoyalButton(left, body + CONTROL + GAP, content, message("search"), b -> rebuildWidgets()));
                int listTop = body + 2 * (CONTROL + GAP);
                rows = Math.max(1, (bounds.footerY() - listTop - CONTROL * 2 - GAP * 3) / (CONTROL + GAP));
                var panels = matchingPanels(); int pages = Math.max(1, (panels.size() + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
                if (panels.isEmpty()) addRenderableWidget(paragraph(font, left, listTop, content - GAP,
                        Math.max(CONTROL, bounds.footerY() - listTop - CONTROL * 2 - GAP * 3), message("empty")));
                for (int i = page * rows; i < Math.min(panels.size(), (page + 1) * rows); i++) {
                    var panel = panels.get(i);
                    var button = addRenderableWidget(new RoyalButton(left, listTop + (i % rows) * (CONTROL + GAP), content, panel.title(), b -> open(panel)));
                    button.active = ready();
                }
                int pagerY = bounds.footerY() - CONTROL * 2 - GAP * 2;
                var previous = addRenderableWidget(new RoyalButton(left, pagerY, 80, message("previous"), b -> { page--; rebuildWidgets(); }));
                previous.active = page > 0 && ready();
                var next = addRenderableWidget(new RoyalButton(bounds.right() - 80, pagerY, 80, message("next"), b -> { page++; rebuildWidgets(); }));
                next.active = page + 1 < pages && ready();
            }
        } else addRenderableWidget(paragraph(font, left, body, content - GAP,
                Math.max(CONTROL, bounds.footerY() - body - CONTROL - GAP * 2), feedback));
        if (context == null && !pending && sent == 0 && !failed) read();
        else if (context != null && System.currentTimeMillis() - received > 30_000 && !pending && sent == 0) read();
    }
    private void open(EconomyPanel panel) {
        if (!ready() || !EconomyUi.panels(context).contains(panel)) return;
        try {
            var screen = Objects.requireNonNull(panel.factory().create(context, this), "Economy provider returned no screen");
            // Refresh political context on return, including ownership and roles changed in that screen.
            received = 0; minecraft.setScreen(screen);
        } catch (RuntimeException failure) {
            feedback = message("provider_error"); failed = true; rebuildWidgets();
            RegnumNations.LOGGER.error("Economy panel failed: {}", panel.id(), failure);
        }
    }
    private void read() {
        if (minecraft.player == null || !EconomyUi.available(target.scope())) { context = null; feedback = message("unavailable"); failed = true; return; }
        if (!canRead()) { pending = true; return; }
        pending = false; readSent(); sequence = nextSequence(); sent = System.currentTimeMillis(); failed = false; feedback = text("loading");
        ClientPlayNetworking.send(new Request(sequence, target));
    }
    public void invalidate() { context = null; sent = 0; pending = false; failed = false; sequence = nextSequence(); read(); rebuildWidgets(); }
    public void accept(State state) {
        if (state.sequence() != sequence) return;
        sent = 0; received = System.currentTimeMillis();
        if (state.status() == ApiResult.Status.VALID && state.entity().orElseThrow().ref().equals(target)) {
            context = new EconomyContext(state.entity().orElseThrow(), state.role(), state.permissions()); failed = false; feedback = message("updated");
        } else { context = null; failed = true; feedback = message("status." + state.status().name().toLowerCase(Locale.ROOT)); }
        rebuildWidgets();
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null) { onClose(); return; }
        if (!EconomyUi.available(target.scope()) && context != null) { context = null; failed = true; feedback = message("unavailable"); rebuildWidgets(); }
        if (pending && canRead()) read();
        if (sent != 0 && System.currentTimeMillis() - sent > 10_000) { sent = 0; pending = false; context = null; failed = true; feedback = text("timeout"); rebuildWidgets(); }
    }
    @Override public void onClose() { if (parent instanceof KingdomManagementScreen menu) menu.refreshOnOpen(); minecraft.setScreen(parent); }
    @Override public Component getNarrationMessage() {
        return title.copy().append(". ").append(context == null ? feedback : Component.literal(context.entity().name())).append(". ").append(feedback);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BACKGROUND);
        frame(g, font, title, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (context != null) NationsIdentity.draw(g, font, context.entity(), bounds.left(), bounds.y() + 41, bounds.contentWidth());
        KingdomUi.feedback(g, font, feedback, bounds.left(), bounds.footerY() - LINE - GAP, bounds.contentWidth(),
                failed ? Feedback.ERROR : received != 0 ? Feedback.SUCCESS : Feedback.NEUTRAL);
        super.render(g, mx, my, delta);
    }
}
