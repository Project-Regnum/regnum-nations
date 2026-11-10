package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;

/** Three focused regressions for server-issued chat actions, cancellation and pending-request security. */
public final class RoyalCartographyTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="royal_cartography")
    public void issuedChatConfirmRoutesExactUuidAndRetainsCities(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer(); var service=KingdomService.get(server); var data=service.data();
        Kingdom target=kingdom("Chat Confirm "+shortId()); data.add(target);
        var messages=new Messages(); var admin=server.createCommandSourceStack().withPermission(3).withSource(messages);
        h.assertTrue(run(admin,"reigns admin delete \""+target.name().display()+"\"")==1,"Initial request must route through quoted full name");
        Component prompt=messages.last(); var actions=runActions(prompt);
        h.assertTrue(actions.size()==2,"Prompt must expose exactly two RUN_COMMAND actions");
        UUID token=service.requestDeletion(admin,target.id());
        h.assertTrue(actions.getFirst().getStyle().getClickEvent().getValue().equals(KingdomDeletionPrompt.command(target.id(),"confirm",token))
                &&actions.get(1).getStyle().getClickEvent().getValue().equals(KingdomDeletionPrompt.command(target.id(),"cancel",token)),
                "Clickable commands must use the server-issued exact UUID/token");
        h.assertTrue(actions.getFirst().getStyle().isBold()&&actions.getFirst().getStyle().getColor().getValue()==ChatFormatting.RED.getColor(),
                "Dissolution must have a readable danger style");
        String encoded=Component.Serializer.toJson(prompt,h.getLevel().registryAccess());
        h.assertTrue(encoded.contains("delete_fallback")&&encoded.contains("suggest_command")&&encoded.contains(target.name().display()),
                "Localized prompt must retain target identity and a textual/suggest-command fallback");
        var city=data.settlement(target.capitalSettlementId()); var capital=ClaimPos.at(target.dimension(),target.capitalPos());
        h.assertTrue(run(admin,actions.getFirst().getStyle().getClickEvent().getValue().substring(1))==1,"Click command must dissolve through actual command routing");
        h.assertTrue(data.byId(target.id())==null&&data.settlement(city.id()).ownerId().equals(city.ownerId())
                &&data.settlement(city.id()).sovereign()==null&&data.claimOwner(capital).equals(city.id()),"Dissolution must retain city, owner and capital claim");
        h.assertTrue(run(admin,actions.getFirst().getStyle().getClickEvent().getValue().substring(1))==0,"Old confirmation must not replay");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="royal_cartography")
    public void clickCancelInvalidatesAndStaleCancelCannotRevokeNewRequest(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer(); var service=KingdomService.get(server); var data=service.data();
        Kingdom target=kingdom("Chat Cancel "+shortId()); data.add(target);
        var messages=new Messages(); var admin=server.createCommandSourceStack().withPermission(3).withSource(messages);
        String request="reigns admin delete "+target.id(); h.assertTrue(run(admin,request)==1,"Request must succeed");
        var actions=runActions(messages.last()); String oldConfirm=actions.getFirst().getStyle().getClickEvent().getValue().substring(1);
        String oldCancel=actions.get(1).getStyle().getClickEvent().getValue().substring(1);
        CompoundTag before=data.save(new CompoundTag(),h.getLevel().registryAccess()); data.setDirty(false);
        h.assertTrue(run(admin,oldCancel)==1&&run(admin,oldConfirm)==0,"Cancel must invalidate confirmation on the server");
        h.assertTrue(!data.isDirty()&&before.equals(data.save(new CompoundTag(),h.getLevel().registryAccess())),"Cancel/rejected confirmation must not mutate SavedData");
        h.assertTrue(run(admin,request)==1,"Fresh request must succeed after cancellation");
        String current=runActions(messages.last()).getFirst().getStyle().getClickEvent().getValue().substring(1);
        h.assertFalse(current.equals(oldConfirm),"New request must have a new token");
        h.assertTrue(run(admin,oldCancel)==0&&run(admin,current)==1,"Stale Cancel must fail without invalidating the new confirmation");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="royal_cartography")
    public void pendingDeadlineActorPermissionAndTargetRemainBound(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer(); var data=new KingdomSavedData(); var service=new KingdomService(server,data);
        Kingdom target=kingdom("Pending Target"),other=kingdom("Pending Other"); data.add(target); data.add(other);
        UUID actor=UUID.randomUUID(); var profile=new GameProfile(actor,"CartographyTest");
        var player=new ServerPlayer(server,h.getLevel(),profile,ClientInformation.createDefault());
        var replacement=new ServerPlayer(server,h.getLevel(),profile,ClientInformation.createDefault());
        var admin=server.createCommandSourceStack().withPermission(3).withEntity(player).withSuppressedOutput();
        var oldSession=service.prepareDeletion(admin,target.id());
        reject(h,() -> service.prepareDeletion(admin.withEntity(replacement),target.id()));
        reject(h,() -> service.confirmDeletion(admin,target.id(),oldSession.token()));
        var request=service.prepareDeletion(admin,target.id());
        reject(h,() -> service.requestDeletion(admin,other.id()));
        reject(h,() -> service.cancelDeletion(admin.withPermission(2),target.id(),request.token()));
        reject(h,() -> service.cancelDeletion(admin.withEntity(replacement),target.id(),request.token()));
        var clock=(ServerLevelData)server.overworld().getLevelData(); long before=clock.getGameTime();
        try {
            clock.setGameTime(before+KingdomService.SELECTION_TICKS-1);
            var repeated=service.prepareDeletion(admin,target.id());
            h.assertTrue(repeated.existing()&&repeated.token().equals(request.token()),"Repeated requests must retain the issued token");
            clock.setGameTime(before+KingdomService.SELECTION_TICKS);
            reject(h,() -> service.confirmDeletion(admin,target.id(),repeated.token()));
            h.assertTrue(data.byId(target.id())!=null&&data.byId(other.id())!=null,"Repeating must not extend the original 30-second deadline");
            var fresh=service.prepareDeletion(admin,target.id());
            reject(h,() -> service.cancelDeletion(admin,target.id(),request.token()));
            service.confirmDeletion(admin,target.id(),fresh.token());
            h.assertTrue(data.byId(target.id())==null&&data.byId(other.id())!=null,"Only current executor/session/target token may dissolve the selected kingdom");
        } finally { clock.setGameTime(before); }
        h.succeed();
    }
    private static String shortId() { return UUID.randomUUID().toString().substring(0,8); }
    private static Kingdom kingdom(String name) {
        int x=2_000_000+Math.floorMod(UUID.randomUUID().hashCode(),100_000)*16;
        return new Kingdom(UUID.randomUUID(),new KingdomName(name),UUID.randomUUID(),Level.OVERWORLD,new BlockPos(x,64,2_000_000),
                UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED);
    }
    private static int run(CommandSourceStack source,String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command,source);
    }
    private static List<Component> runActions(Component prompt) {
        return prompt.toFlatList().stream().filter(c -> c.getStyle().getClickEvent()!=null
                &&c.getStyle().getClickEvent().getAction()==ClickEvent.Action.RUN_COMMAND).toList();
    }
    private interface Attempt { void run() throws Exception; }
    private static void reject(GameTestHelper h,Attempt attempt) throws Exception {
        boolean rejected=false; try { attempt.run(); } catch (CommandSyntaxException e) { rejected=true; }
        h.assertTrue(rejected,"Stale, unauthorized or mismatched request must fail");
    }
    private static final class Messages implements CommandSource {
        private final List<Component> messages=new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
        Component last() { return messages.getLast(); }
    }
}
