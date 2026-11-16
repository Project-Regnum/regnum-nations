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
    private boolean pending, failed, details;
    public EconomyScreen(Screen parent, EntityRef target) { super(message("title")); this.parent = parent; this.target = Objects.requireNonNull(target); }
    public static Component message(String key, Object... args) { return Component.translatable("economy.regnum-nations." + key, args); }
    private static Component category(EconomyPanel.Category category) { return message("category." + category.name().toLowerCase(Locale.ROOT)); }
    private List<EconomyPanel> matchingPanels() {
        if (context == null) return List.of();
        String filter = query.strip().toLowerCase(Locale.ROOT);
        return EconomyUi.panels(context).stream().filter(p -> (category == null || p.category() == category) && p.title().getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private boolean ready() { return context != null && sent == 0 && !pending && !failed; }
    @Override protected void init() {
        bounds = folioBounds(width, height, 640, 350);
        int left = bounds.left(), top = bounds.y(), content = bounds.contentWidth();
        int footer = bounds.footerY();
        addRenderableWidget(new RoyalButton(left, footer, 64, text("back"), b -> onClose()));
        addRenderableWidget(new RoyalButton(bounds.right() - 70, footer, 70, text("refresh"), b -> read()));
        if (context != null) {
            var technical = addRenderableWidget(new RoyalButton(left + 70, footer, content - 146,
                    text("economic_details"), b -> { details = !details; rebuildWidgets(); }));
            technical.selected(details);
            var categories = Arrays.stream(EconomyPanel.Category.values())
                    .filter(c -> EconomyUi.panels(context).stream().anyMatch(p -> p.category() == c)).toList();
            boolean wide = content >= 430 && height >= 330;
            int rail = wide ? 132 : 0, body = top + 82, listX = left + (wide ? rail + GAP : 0);
            int listWidth = content - (wide ? rail + GAP : 0), bottom = footer - LINE - GAP * 2;
            if (wide) {
                var all = addRenderableWidget(new RoyalButton(left, body, rail, message("overview"), b -> {
                    category = null; details = false; page = 0; rebuildWidgets();
                }));
                all.selected(category == null && !details);
                for (int i = 0; i < categories.size(); i++) {
                    var value = categories.get(i);
                    var tab = addRenderableWidget(new RoyalButton(left, body + (i + 1) * (CONTROL + GAP), rail,
                            category(value), b -> { category = value; details = false; page = 0; rebuildWidgets(); }));
                    tab.selected(category == value && !details); tab.active = ready();
                }
            } else {
                var destinations = new ArrayList<Component>(); destinations.add(message("overview"));
                categories.forEach(c -> destinations.add(category(c)));
                addRenderableWidget(new RoyalButton(left, body, content,
                        text("choose_section", category == null ? message("overview") : category(category)), b ->
                        minecraft.setScreen(new SectionSelectionScreen(this, destinations, index -> {
                            category = index == 0 ? null : categories.get(index - 1); details = false; page = 0; rebuildWidgets();
                        }))));
                body += CONTROL + GAP;
            }
            if (details) {
                var lines = new ArrayList<Component>();
                lines.add(text("identity_uuid", context.entity().ref().id()));
                if (context.entity().independentCity()) lines.add(text("independent_city"));
                if (context.permissions().isEmpty()) lines.add(message("no_permissions"));
                context.permissions().stream().sorted().forEach(p -> lines.add(message("permission." + p.name().toLowerCase(Locale.ROOT))));
                addRenderableWidget(new Readout(font, listX, body, listWidth - GAP, Math.max(20, bottom - body), lines));
            } else {
                var panels = matchingPanels();
                int cardHeight = height < 290 ? CONTROL : 38;
                rows = Math.max(1, (bottom - body - CONTROL - GAP) / (cardHeight + GAP));
                int pages = Math.max(1, (panels.size() + rows - 1) / rows);
                page = Math.clamp(page, 0, pages - 1);
                if (panels.isEmpty()) addRenderableWidget(paragraph(font, listX, body, listWidth - GAP,
                        Math.max(20, bottom - body), message("no_operations")));
                for (int i = page * rows; i < Math.min(panels.size(), (page + 1) * rows); i++) {
                    var panel = panels.get(i);
                    var card = addRenderableWidget(new RoyalButton(listX, body + (i % rows) * (cardHeight + GAP),
                            listWidth, panel.title(), b -> open(panel), panel.category() == EconomyPanel.Category.TREASURY ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL) {
                        @Override protected void renderContent(GuiGraphics g, int color, int offset) {
                            g.drawString(font, ellipsize(font, getMessage(), getWidth() - 32), getX() + 10, getY() + 6, color, false);
                            if (getHeight() > CONTROL) g.drawString(font, ellipsize(font, category(panel.category()), getWidth() - 32), getX() + 10, getY() + 23, MUTED, false);
                            g.drawString(font, "›", getRight() - 14, getY() + (getHeight() - 9) / 2, GOLD, false);
                        }
                    });
                    card.setHeight(cardHeight); card.active = ready();
                }
                if (pages > 1) {
                    var previous = addRenderableWidget(new RoyalButton(listX, bottom - CONTROL, 54, message("previous"), b -> { page--; rebuildWidgets(); }));
                    previous.active = ready() && page > 0;
                    var next = addRenderableWidget(new RoyalButton(listX + listWidth - 54, bottom - CONTROL, 54, message("next"), b -> { page++; rebuildWidgets(); }));
                    next.active = ready() && page + 1 < pages;
                }
            }
        } else addRenderableWidget(paragraph(font, left, top + 82, content - GAP,
                Math.max(CONTROL, footer - top - 108), feedback));
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
        if (context != null) {
            NationsIdentity.draw(g, font, context.entity(), bounds.left(), bounds.y() + 41, bounds.contentWidth());
            g.drawString(font, ellipsize(font, message("role", message("role." + context.role().toLowerCase(Locale.ROOT))), bounds.contentWidth()-34), bounds.left()+34, bounds.y()+69, MUTED, false);
        }
        KingdomUi.feedback(g, font, feedback, bounds.left(), bounds.footerY() - LINE - GAP, bounds.contentWidth(),
                failed ? Feedback.ERROR : received != 0 ? Feedback.SUCCESS : Feedback.NEUTRAL);
        super.render(g, mx, my, delta);
    }
}
