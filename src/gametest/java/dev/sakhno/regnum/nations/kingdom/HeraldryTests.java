package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import dev.sakhno.regnum.nations.block.*;
import dev.sakhno.regnum.nations.kingdom.Heraldry.*;
import dev.sakhno.regnum.nations.networking.HeraldryPayloads.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.*;

public final class HeraldryTests implements FabricGameTest {
    private static final AtomicInteger NEXT=new AtomicInteger(0);
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void everyVocabularyOptionRoundTripsInNbtAndWire(GameTestHelper h) {
        for (var shape:Shield.values()) for (var division:Division.values()) for (var ordinary:Ordinary.values()) for (var emblem:Emblem.values()) {
            var arms=new Heraldry(1,shape,Tincture.GULES,division,Tincture.ARGENT,ordinary,Tincture.OR,
                    List.of(new Charge(emblem,Tincture.ARGENT,50,46,65,3,true)));
            h.assertTrue(arms.equals(Heraldry.fromTag(arms.toTag())),"All vocabulary persists by stable textual ID");
            var b=buffer(h); try { arms.write(b); h.assertTrue(b.readableBytes()<473&&arms.equals(Heraldry.read(b))&&!b.isReadable(),"Wire is compact and deterministic"); } finally { b.release(); }
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void defaultsAreStableAcrossRenameAndMigrationFromSeven(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var raw=f.saved(); raw.putInt("Version",7); for (var item:raw.getList("Kingdoms",Tag.TAG_COMPOUND)) ((CompoundTag)item).remove("Heraldry");
        var untouched=raw.copy(); var data=KingdomSavedData.load(raw,h.getLevel().registryAccess());
        h.assertTrue(data.available()&&data.heraldry(f.realm.id()).equals(Heraldry.defaults(f.realm.id()))&&raw.equals(untouched),"Schema 7 gains deterministic arms without mutating its input");
        var before=data.heraldry(f.realm.id()); data.rename(f.realm.id(),new KingdomName("Renamed Arms"));
        h.assertTrue(data.heraldry(f.realm.id()).equals(before)&&data.settlementClaims(f.realm.id()).equals(f.data.settlementClaims(f.realm.id())),"Name does not control arms or territory");
        var saved=data.save(new CompoundTag(),h.getLevel().registryAccess()); var again=KingdomSavedData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.getInt("Version")==8&&saved.equals(again.save(new CompoundTag(),h.getLevel().registryAccess()))&&!again.isDirty(),"Schema 8 reaches an idempotent save"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void malformedOrFutureArmsRecoverWithoutLosingKingdomData(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false);
        for (String field:List.of("Shield","Version","Charges","Scale")) {
            var raw=f.saved(); var record=raw.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0); var arms=record.getCompound("Heraldry");
            switch(field) {
                case "Shield" -> arms.putString(field,"evil:uploaded"); case "Version" -> arms.putInt(field,900);
                case "Charges" -> arms.putString(field,"malformed"); case "Scale" -> arms.getList("Charges",Tag.TAG_COMPOUND).getCompound(0).putInt(field,Integer.MAX_VALUE);
            }
            var original=arms.copy(); var data=KingdomSavedData.load(raw,h.getLevel().registryAccess());
            h.assertTrue(data.available()&&data.kingdoms().size()==1&&data.heraldry(f.realm.id()).equals(Heraldry.defaults(f.realm.id())),"Only corrupt arms fall back");
            var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());
            h.assertTrue(saved.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0).get("HeraldryRecovery").equals(original)
                    &&saved.get("Claims").equals(raw.get("Claims")),"Original arms and unrelated claims are retained");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void invalidChargeBoundsAndCountAreRejected(GameTestHelper h) {
        for (int[] n:List.of(new int[]{14,50,50,0},new int[]{50,81,50,0},new int[]{50,50,86,0},new int[]{50,50,19,0},new int[]{50,50,50,4}))
            rejects(h,() -> new Charge(Emblem.LION,Tincture.OR,n[0],n[1],n[2],n[3],false));
        var a=Heraldry.defaults(UUID.randomUUID()); var raw=a.toTag(); var list=raw.getList("Charges",Tag.TAG_COMPOUND);
        while(list.size()<4) list.add(list.getCompound(0).copy()); rejects(h,() -> Heraldry.fromTag(raw));
        rejects(h,() -> new Sync(false,Collections.nCopies(33,new Entry(UUID.randomUUID(),a)))); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void tinctureWarningNeverPreventsSaving(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var a=new Heraldry(1,Shield.HEATER,Tincture.GULES,Division.PLAIN,Tincture.AZURE,Ordinary.NONE,Tincture.OR,List.of(new Charge(Emblem.ROSE,Tincture.AZURE,50,46,60,0,false)));
        h.assertTrue(a.tinctureWarning(),"Colour on colour warns"); f.arms.save(f.realm.id(),f.ruler.getUUID(),a);
        h.assertTrue(f.data.heraldry(f.realm.id()).equals(a),"Authenticity remains guidance"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void politicalPermissionEnforcementAndRevocation(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var member=f.player(); f.data.addCitizen(f.realm.id(),member.getUUID()); var a=Heraldry.defaults(UUID.randomUUID());
        h.assertFalse(f.arms.canEdit(f.realm.id(),member.getUUID()),"Members cannot edit");
        try { f.arms.save(f.realm.id(),member.getUUID(),a); h.fail("Unauthorized save accepted"); } catch (FoundingException expected) { }
        f.data.setKingdomRole(f.realm.id(),member.getUUID(),KingdomRole.OFFICER); h.assertFalse(f.arms.canEdit(f.realm.id(),member.getUUID()),"Officers cannot edit");
        f.data.setKingdomRole(f.realm.id(),member.getUUID(),KingdomRole.CHANCELLOR); f.arms.save(f.realm.id(),member.getUUID(),a);
        f.data.setKingdomRole(f.realm.id(),member.getUUID(),KingdomRole.MEMBER);
        h.assertFalse(f.arms.canEdit(f.realm.id(),member.getUUID()),"Demotion revokes authority immediately"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void unsignedAndModifiedIdentitiesFailVerification(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var good=f.arms.banner(f.realm.id());
        h.assertTrue(f.arms.verifiedIdentity(good).equals(f.realm.id()),"Only server-issued seals verify");
        var tag=good.get(DataComponents.CUSTOM_DATA).copyTag(); tag.putUUID("KingdomId",UUID.randomUUID()); good.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        h.assertTrue(f.arms.verifiedIdentity(good)==null&&f.arms.verifiedIdentity(new ItemStack(ModBlocks.ROYAL_BANNER_ITEM))==null,"Editing ownership or omitting the seal cannot forge identity");
        var other=new Fixture(h,false); h.assertTrue(other.arms.verifiedIdentity(f.arms.banner(f.realm.id()))==null,"Seals are specific to the saved world"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void bannerSealAndArmsSurviveActualSavedDataDiskReload(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var item=f.arms.banner(f.realm.id()); var custom=Heraldry.defaults(UUID.randomUUID()); f.arms.save(f.realm.id(),f.ruler.getUUID(),custom);
        var path=Files.createTempDirectory("regnum-heraldry-");
        try {
            var level=h.getLevel(); var writer=new DimensionDataStorage(path.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
            f.data.setDirty(); writer.set(KingdomSavedData.FILE_ID,f.data); writer.save();
            var reader=new DimensionDataStorage(path.toFile(),level.getServer().getFixerUpper(),level.registryAccess()); var data=reader.computeIfAbsent(KingdomSavedData.FACTORY,KingdomSavedData.FILE_ID);
            h.assertTrue(data.heraldry(f.realm.id()).equals(custom)&&new HeraldryService(data).verifiedIdentity(item).equals(f.realm.id()),"Arms and server seal survive a real disk boundary");
        } finally { try(var files=Files.walk(path)) { for(var file:files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); } }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void standingBannerPlacesBreaksAndRetainsOriginalIdentity(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var item=f.arms.banner(f.realm.id()); var pos=f.pos;
        f.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.assertTrue(f.place(f.ruler,item,pos,Direction.UP).consumesAction(),"Standing standard places through native BlockItem path");
        var banner=(RoyalBannerBlockEntity)f.level.getBlockEntity(pos); h.assertTrue(banner!=null&&banner.kingdom().equals(f.realm.id()),"Placed entity references stable identity");
        var drops=Block.getDrops(f.level.getBlockState(pos),f.level,pos,banner);
        h.assertTrue(drops.size()==1&&f.arms.verifiedIdentity(drops.getFirst()).equals(f.realm.id()),"Native break drop retains authenticated original kingdom");
        h.assertTrue(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(f.level,f.ruler,pos,f.level.getBlockState(pos),banner),"Owner can remove banner");
        f.level.destroyBlock(pos,true,f.ruler); h.assertTrue(f.level.getBlockState(pos).isAir(),"Native removal succeeds"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void wallBannerUsesSupportAndDropsWhenSupportRemoved(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var support=f.pos.south(); f.level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        h.assertTrue(f.place(f.ruler,f.arms.banner(f.realm.id()),f.pos,Direction.NORTH).consumesAction(),"Side placement selects wall variant");
        h.assertTrue(f.level.getBlockState(f.pos).is(ModBlocks.WALL_ROYAL_BANNER),"Wall block selected");
        f.level.destroyBlock(support,false); h.assertTrue(f.level.getBlockState(f.pos).isAir(),"Removing support triggers normal removal"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void blockEntityRestoresUuidWithoutCopyingHeraldry(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); f.level.setBlockAndUpdate(f.pos,ModBlocks.ROYAL_BANNER.defaultBlockState());
        var banner=(RoyalBannerBlockEntity)f.level.getBlockEntity(f.pos); banner.bind(f.realm.id());
        var tag=banner.saveWithFullMetadata(f.level.registryAccess()); var restored=(RoyalBannerBlockEntity)BlockEntity.loadStatic(f.pos,banner.getBlockState(),tag,f.level.registryAccess());
        h.assertTrue(restored!=null&&restored.kingdom().equals(f.realm.id())&&!tag.contains("Heraldry")&&!tag.contains("RoyalSeal"),"Chunk NBT carries only stable identity");
        var before=restored.kingdom(); f.arms.save(f.realm.id(),f.ruler.getUUID(),Heraldry.defaults(UUID.randomUUID()));
        h.assertTrue(restored.kingdom().equals(before)&&banner.getUpdateTag(f.level.registryAccess()).getUUID("KingdomId").equals(before),"Updating arms needs no block update or scan"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void deletionLeavesNeutralTrophiesWithOriginalIdentity(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var item=f.arms.banner(f.realm.id()); f.data.deleteKingdom(f.realm.id());
        h.assertTrue(f.data.heraldry(f.realm.id())==null&&f.arms.verifiedIdentity(item).equals(f.realm.id()),"Dissolved realm no longer supplies arms; trophies retain signed historic identity");
        h.assertTrue(f.data.settlement(f.realm.id())!=null&&f.data.settlementClaimCount(f.realm.id())==1,"Dissolution preserves municipal territory"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void forgedBlockEntityComponentsCannotOverrideSeal(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var item=f.arms.banner(f.realm.id()); var evil=new CompoundTag(); evil.putUUID("KingdomId",UUID.randomUUID());
        BlockItem.setBlockEntityData(item,ModBlocks.ROYAL_BANNER_ENTITY,evil); f.level.setBlockAndUpdate(f.pos.below(),Blocks.STONE.defaultBlockState());
        h.assertTrue(f.place(f.ruler,item,f.pos,Direction.UP).consumesAction(),"Authenticated item can place even with ignored foreign NBT");
        h.assertTrue(((RoyalBannerBlockEntity)f.level.getBlockEntity(f.pos)).kingdom().equals(f.realm.id()),"Client supplied block entity NBT cannot choose kingdom"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void outsiderAndAlliedKingdomCannotPlaceOrBreakInForeignCity(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var foreign=f.player(); var id=UUID.randomUUID();
        var other=new Kingdom(id,new KingdomName("Foreign "+id.toString().substring(0,8)),foreign.getUUID(),Level.OVERWORLD,f.pos.offset(0,0,128),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED); f.data.add(other);
        f.data.diplomacy.put(new DiplomacyData.Pair(f.realm.id(),other.id()),new DiplomacyData.Alliance(UUID.randomUUID(),1),Set.of(),null);
        f.level.setBlockAndUpdate(f.pos.below(),Blocks.STONE.defaultBlockState()); var item=f.arms.banner(other.id());
        h.assertFalse(f.place(foreign,item,f.pos,Direction.UP).consumesAction(),"Allied official banner grants no construction rights");
        f.place(f.ruler,f.arms.banner(f.realm.id()),f.pos,Direction.UP);
        h.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(f.level,foreign,f.pos,f.level.getBlockState(f.pos),f.level.getBlockEntity(f.pos)),"Allies cannot break banners in another city's claim"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void unsignedItemsCannotPlaceOrCreateClaims(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var pos=f.pos.offset(32,0,0); f.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        var before=f.data.settlementClaimCount(f.realm.id());
        h.assertFalse(f.place(f.ruler,new ItemStack(ModBlocks.ROYAL_BANNER_ITEM),pos,Direction.UP).consumesAction(),"Blank banners require an official seal");
        h.assertTrue(f.place(f.player(),f.arms.banner(f.realm.id()),pos,Direction.UP).consumesAction(),"Captured authentic banner can be placed in wilderness");
        h.assertTrue(f.data.claimOwner(ClaimPos.at(Level.OVERWORLD,pos))==null&&before==f.data.settlementClaimCount(f.realm.id()),"A banner never claims territory"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void requestSessionsRejectUnauthorizedReplayAndConcurrentChanges(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var outsider=f.player(); var preview=HeraldryServer.handle(outsider,new Request(1,Action.VIEW,f.realm.id(),null,null));
        h.assertFalse(preview.editable(),"Public preview grants no edit authority");
        var reject=HeraldryServer.handle(outsider,new Request(2,Action.SAVE,f.realm.id(),UUID.randomUUID(),Heraldry.defaults(UUID.randomUUID())));
        h.assertTrue(!reject.success()&&reject.result().endsWith("forbidden"),"Unauthorized client payload is rejected");
        var view=HeraldryServer.handle(f.ruler,new Request(3,Action.VIEW,f.realm.id(),null,null)); var a=Heraldry.defaults(UUID.randomUUID());
        var accepted=HeraldryServer.handle(f.ruler,new Request(4,Action.SAVE,f.realm.id(),view.token(),a));
        h.assertTrue(accepted.success()&&f.data.heraldry(f.realm.id()).equals(a),"Authorized current session saves");
        h.assertFalse(HeraldryServer.handle(f.ruler,new Request(5,Action.SAVE,f.realm.id(),view.token(),view.arms())).success(),"Consumed token cannot replay");
        var concurrent=HeraldryServer.handle(f.ruler,new Request(6,Action.VIEW,f.realm.id(),null,null)); f.arms.save(f.realm.id(),f.ruler.getUUID(),Heraldry.defaults(UUID.randomUUID()));
        h.assertFalse(HeraldryServer.handle(f.ruler,new Request(7,Action.SAVE,f.realm.id(),concurrent.token(),a)).success(),"Concurrent edit cannot be overwritten silently"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void dimensionChangesAndDemotionInvalidateEditSessions(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var official=f.player(); f.data.addCitizen(f.realm.id(),official.getUUID()); f.data.setKingdomRole(f.realm.id(),official.getUUID(),KingdomRole.CHANCELLOR);
        var view=HeraldryServer.handle(official,new Request(1,Action.VIEW,f.realm.id(),null,null)); f.data.setKingdomRole(f.realm.id(),official.getUUID(),KingdomRole.MEMBER);
        h.assertFalse(HeraldryServer.handle(official,new Request(2,Action.SAVE,f.realm.id(),view.token(),view.arms())).success(),"Authority is rechecked on every save");
        view=HeraldryServer.handle(f.ruler,new Request(3,Action.VIEW,f.realm.id(),null,null)); f.ruler.setServerLevel(f.level.getServer().getLevel(Level.NETHER));
        h.assertFalse(HeraldryServer.handle(f.ruler,new Request(4,Action.SAVE,f.realm.id(),view.token(),view.arms())).success(),"Changing dimension invalidates the old screen session"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void sealingRequiresAuthorizedSingleBlankMainHandItem(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); f.ruler.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModBlocks.ROYAL_BANNER_ITEM));
        f.arms.seal(f.ruler,f.realm.id()); h.assertTrue(f.arms.verifiedIdentity(f.ruler.getMainHandItem()).equals(f.realm.id()),"Authorized official seals held crafted blank");
        rejects(h,() -> f.arms.seal(f.ruler,f.realm.id()));
        var foreign=f.player(); foreign.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModBlocks.ROYAL_BANNER_ITEM));
        try { f.arms.seal(foreign,f.realm.id()); h.fail("Outsider sealed official banner"); } catch (FoundingException expected) { }
        h.assertTrue(HeraldryService.identity(foreign.getMainHandItem())==null,"Outsider retains an unsigned blank"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void networkingRejectsOversizeIdsCountsAndRapidReplay(GameTestHelper h) throws Exception {
        var f=new Fixture(h,true); var b=buffer(h);
        final var batch=b;
        try { b.writeBoolean(false); b.writeByte(33); rejects(h,() -> Sync.CODEC.decode(batch)); } finally { b.release(); }
        b=buffer(h); final var bad=b;
        try { b.writeVarInt(1); b.writeUtf("unknown_identifier"); rejects(h,() -> Heraldry.read(bad)); } finally { b.release(); }
        var view=new Request(1,Action.SAVE,f.realm.id(),UUID.randomUUID(),Heraldry.defaults(UUID.randomUUID())); b=buffer(h);
        try { Request.CODEC.encode(b,view); h.assertTrue(b.readableBytes()<1024&&Request.CODEC.decode(b).equals(view),"Request has bounded structured data"); } finally { b.release(); }
        h.assertTrue(KingdomManagementServer.gate(f.ruler,100,true)==null&&KingdomManagementServer.gate(f.ruler,100,true).endsWith("duplicate")
                &&KingdomManagementServer.gate(f.ruler,101,true).endsWith("rate_limit"),"Shared mutation gate prevents replay and spam"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="heraldry")
    public void deletionAsFirstChangeAfterRestartEmitsPublicTombstone(GameTestHelper h) throws Exception {
        var f=new Fixture(h,false); var server=f.level.getServer();
        var field=HeraldryServer.class.getDeclaredField("PUBLISHED"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var published=(Map<net.minecraft.server.MinecraftServer,Map<UUID,Heraldry>>)field.get(null);
        var previous=published.remove(server);
        try {
            HeraldryServer.login(f.ruler,f.data);
            f.data.deleteKingdom(f.realm.id());
            var changes=HeraldryServer.changes(server,f.data);
            h.assertTrue(changes.equals(List.of(new Entry(f.realm.id(),null))),
                    "First post-login deletion removes arms that clients received before any political event");
            h.assertTrue(HeraldryServer.changes(server,f.data).isEmpty(),"Deletion is published once, with no repeated traffic");
        } finally {
            published.remove(server); if (previous!=null) published.put(server,previous);
        }
        h.succeed();
    }
    private static RegistryFriendlyByteBuf buffer(GameTestHelper h) { return new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess()); }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(GameTestHelper h,Attempt attempt) { try { attempt.run(); } catch (Exception expected) { return; } h.fail("Malformed request unexpectedly accepted"); }
    private static final class Fixture {
        final GameTestHelper h; final ServerLevel level; final KingdomSavedData data; final HeraldryService arms; final ServerPlayer ruler; final Kingdom realm; final BlockPos pos;
        Fixture(GameTestHelper h,boolean live) throws Exception {
            this.h=h; level=h.getLevel(); data=live?KingdomService.get(level.getServer()).data():new KingdomSavedData(); arms=new HeraldryService(data);
            pos=new BlockPos(320008+NEXT.incrementAndGet()*64,70,320008); ruler=player();
            var id=UUID.randomUUID(); realm=new Kingdom(id,new KingdomName("Arms "+id.toString().substring(0,8)),ruler.getUUID(),Level.OVERWORLD,pos.offset(4,0,4),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED); data.add(realm);
        }
        ServerPlayer player() {
            var p=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"HeraldryTest"),ClientInformation.createDefault()) { @Override public void displayClientMessage(Component c,boolean actionBar) { } };
            p.setPos(Vec3.atCenterOf(pos).add(0,0,2)); return p;
        }
        CompoundTag saved() { return data.save(new CompoundTag(),level.registryAccess()); }
        InteractionResult place(ServerPlayer actor,ItemStack stack,BlockPos at,Direction face) {
            var hit=new BlockHitResult(Vec3.atCenterOf(at.relative(face.getOpposite())),face,at.relative(face.getOpposite()),false);
            return ModBlocks.ROYAL_BANNER_ITEM.place(new BlockPlaceContext(level,actor,InteractionHand.MAIN_HAND,stack,hit));
        }
    }
}
