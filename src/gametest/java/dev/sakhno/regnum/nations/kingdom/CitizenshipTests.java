package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

public class CitizenshipTests implements FabricGameTest {
    private static final AtomicInteger NEXT_LOCATION = new AtomicInteger();
    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void ownerInvitesPlayerAcceptsAndReplayIsRejected(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var city = f.city(owner);
            h.assertTrue(f.citizens.invite(owner, target).id().equals(city.id()), "Owner can invite an online player");
            h.assertTrue(f.data.citizenCity(target.getUUID()) == null, "An invitation alone must not grant rights");
            f.citizens.accept(target, city.id());
            h.assertTrue(f.data.citizenCity(target.getUUID()).id().equals(city.id())
                    && f.citizens.list(target).containsAll(List.of(owner.getUUID(), target.getUUID()))
                    && f.citizens.list(owner).size() == 2, "Owner and accepted player must share one city");
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void decliningConsumesOnlyTheRecipientsInvitation(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var stranger = f.player(); var city = f.city(owner);
            f.citizens.invite(owner, target);
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(stranger, city.id()));
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.decline(stranger, city.id()));
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.decline(target, UUID.randomUUID()));
            f.citizens.decline(target, city.id());
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
            h.assertTrue(f.data.citizenCity(target.getUUID()) == null, "Declining cannot create membership");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void invitationExpiresAtExactlySixtySeconds(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var first = f.player(); var second = f.player(); var city = f.city(owner);
            f.citizens.invite(owner, first);
            f.now.set(CitizenshipService.INVITATION_NANOS - 1);
            f.citizens.accept(first, city.id());
            f.citizens.invite(owner, second);
            f.now.addAndGet(CitizenshipService.INVITATION_NANOS);
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(second, city.id()));
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.decline(second, city.id()));
            h.assertTrue(f.data.isCitizen(city.id(), first.getUUID()), "Invitation is usable just before its deadline");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void duplicatesAndRateLimitingCannotDuplicateCitizenship(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var city = f.city(owner);
            f.citizens.invite(owner, target);
            reject(f, CITIZEN_RATE_LIMIT, () -> f.citizens.invite(owner, target));
            f.advance(); f.citizens.invite(owner, target);
            f.citizens.accept(target, city.id());
            reject(f, ALREADY_CITIZEN, () -> f.citizens.invite(owner, target));
            reject(f, ALREADY_CITIZEN, () -> f.data.addCitizen(city.id(), target.getUUID()));
            h.assertTrue(f.citizens.list(owner).size() == 2, "Duplicate invitations cannot duplicate membership");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void onlyOwnersManageMembershipAndOwnersCannotLeaveOrKickThemselves(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var citizen = f.player(); var stranger = f.player(); var city = f.city(owner);
            f.join(owner, citizen, city);
            reject(f, NOT_OWNER, () -> f.citizens.invite(citizen, stranger));
            reject(f, NOT_OWNER, () -> f.citizens.kick(citizen, owner.getUUID()));
            reject(f, NO_CITY, () -> f.citizens.invite(stranger, citizen));
            reject(f, NO_CITY, () -> f.citizens.kick(stranger, citizen.getUUID()));
            reject(f, OWNER_CANNOT_LEAVE, () -> f.citizens.leave(owner));
            reject(f, OWNER_CANNOT_LEAVE, () -> f.citizens.kick(owner, owner.getUUID()));
            reject(f, NOT_CITIZEN, () -> f.citizens.kick(owner, stranger.getUUID()));
            reject(f, ALREADY_CITIZEN, () -> f.citizens.invite(owner, owner));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void competingInvitationsAndFoundingCannotCreateTwoMemberships(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var other = f.player(); var target = f.player();
            var city = f.city(owner); var second = f.city(other);
            f.citizens.invite(owner, target); f.citizens.invite(other, target);
            f.citizens.accept(target, city.id());
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, second.id()));
            reject(f, ALREADY_CITIZEN, () -> f.data.addCitizen(second.id(), target.getUUID()));
            reject(f, ALREADY_CITIZEN, () -> f.city(target));
            reject(f, ALREADY_CITIZEN, () -> f.data.add(f.kingdom(target)));
            reject(f, ALREADY_CITIZEN, () -> f.citizens.invite(other, target));
            reject(f, ALREADY_CITIZEN, () -> f.citizens.invite(other, owner));
            h.assertTrue(f.data.citizenCity(target.getUUID()).id().equals(city.id()), "First accepted membership must remain authoritative");
            var later = f.player(); f.advance(); f.citizens.invite(owner, later);
            f.data.addCitizen(second.id(), later.getUUID());
            reject(f, ALREADY_CITIZEN, () -> f.citizens.accept(later, city.id()));
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(later, city.id()));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void leaveAndOfflineKickRemoveOnlyCurrentMembership(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var other = f.player(); var city = f.city(owner); var second = f.city(other);
            f.join(owner, target, city); f.citizens.leave(target);
            reject(f, NO_CITY, () -> f.citizens.leave(target));
            f.join(other, target, second);
            reject(f, NOT_CITIZEN, () -> f.citizens.kick(owner, target.getUUID()));
            f.serverPlayers().remove(target); f.citizens.disconnect(target.getUUID());
            f.citizens.kick(other, target.getUUID());
            h.assertTrue(f.data.citizenCity(target.getUUID()) == null && f.citizens.list(other).equals(List.of(other.getUUID())),
                    "Owner must be able to kick a disconnected citizen by UUID");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void disconnectRevokesInvitationsButPreservesAcceptedMembership(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var city = f.city(owner);
            f.citizens.invite(owner, target); f.citizens.disconnect(target.getUUID());
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
            f.advance(); f.citizens.invite(owner, target); f.citizens.disconnect(owner.getUUID());
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
            f.advance(); f.citizens.invite(owner, target);
            var reconnected = f.player(target.getUUID());
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(reconnected, city.id()));
            f.advance(); f.join(owner, reconnected, city); f.citizens.disconnect(target.getUUID());
            h.assertTrue(f.citizens.list(reconnected).contains(target.getUUID()), "Citizenship must survive reconnects");
            var offline = f.player(); f.serverPlayers().remove(offline);
            reject(f, PLAYER_OFFLINE, () -> f.citizens.invite(owner, offline));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void acceptedCitizenshipSurvivesActualSavedDataDiskReload(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var city = f.city(owner); f.join(owner, target, city);
            var pending = f.player(); f.advance(); f.citizens.invite(owner, pending);
            var directory = Files.createTempDirectory("regnum-nations-citizenship-");
            try {
                var level = h.getLevel();
                var writer = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
                writer.set(KingdomSavedData.FILE_ID, f.data); writer.save();
                var reader = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
                var loaded = reader.computeIfAbsent(KingdomSavedData.FACTORY, KingdomSavedData.FILE_ID); loaded.requireAvailable();
                var service = new KingdomService(level.getServer(), loaded).citizenship();
                h.assertTrue(service.list(target).containsAll(List.of(owner.getUUID(), target.getUUID()))
                        && loaded.citizenCity(target.getUUID()).id().equals(city.id()) && !loaded.isDirty(), "Citizenship and implicit owners must reload from disk");
                try { service.accept(pending, city.id()); h.fail("Pending invitations must not survive reload"); }
                catch (FoundingException e) { h.assertTrue(e.reason() == CITIZEN_INVITATION_REQUIRED, "Wrong reload rejection"); }
            } finally {
                try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
                Files.delete(directory);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void schemasOneTwoAndThreeMigrateWithoutInventingCitizens(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var kingdom = f.kingdom(owner); f.data.add(kingdom);
            for (int version = 1; version <= 3; version++) {
                CompoundTag old = f.saved(); old.putInt("Version", version); old.remove("Citizens");
                if (version == 1) old.remove("Claims");
                if (version == 2) for (Tag value : old.getList("Claims", Tag.TAG_COMPOUND)) {
                    CompoundTag claim = (CompoundTag) value; claim.putUUID("Kingdom", claim.getUUID("Settlement")); claim.remove("Settlement");
                }
                var loaded = KingdomSavedData.load(old, h.getLevel().registryAccess()); loaded.requireAvailable();
                h.assertTrue(loaded.citizens(kingdom.id()).equals(List.of(owner.getUUID()))
                        && loaded.citizenCity(owner.getUUID()).id().equals(kingdom.id()) && loaded.isDirty()
                        && loaded.claimOwner(ClaimPos.at(kingdom.dimension(), kingdom.capitalPos())).equals(kingdom.id()),
                        "Every older schema must retain ownership, implicit citizenship and claims");
                var saved = loaded.save(new CompoundTag(), h.getLevel().registryAccess());
                var again = KingdomSavedData.load(saved, h.getLevel().registryAccess()); again.requireAvailable();
                h.assertTrue(saved.getInt("Version") == KingdomSchema.CURRENT && !again.isDirty()
                        && saved.equals(again.save(new CompoundTag(), h.getLevel().registryAccess())), "Schema 5 migration must be idempotent");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void conflictingMalformedAndOrphanedMembershipPreservesOriginalNbt(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var other = f.player();
            var city = f.city(owner); var second = f.city(other); f.join(owner, target, city);
            for (int variant = 0; variant < 7; variant++) {
                var broken = f.saved(); var records = broken.getList("Citizens", Tag.TAG_COMPOUND);
                var extra = records.getCompound(0).copy();
                switch (variant) {
                    case 0 -> records.add(extra); // Identical duplicate is still invalid.
                    case 1 -> { extra.putUUID("Settlement", second.id()); records.add(extra); }
                    case 2 -> records.getCompound(0).putUUID("Player", other.getUUID());
                    case 3 -> records.getCompound(0).putUUID("Settlement", UUID.randomUUID());
                    case 4 -> records.getCompound(0).putString("Player", "not-a-uuid");
                    case 5 -> broken.remove("Citizens");
                    case 6 -> { broken.putInt("Version", 3); extra.putUUID("Settlement", second.id()); records.add(extra); }
                }
                var loaded = KingdomSavedData.load(broken, h.getLevel().registryAccess());
                try { loaded.requireAvailable(); h.fail("Invalid citizenship must disable mutations"); }
                catch (FoundingException e) { h.assertTrue(e.reason() == DATA_UNAVAILABLE, "Wrong corruption rejection"); }
                h.assertTrue(broken.equals(loaded.save(new CompoundTag(), h.getLevel().registryAccess())),
                        "Conflicting records must never be silently selected, overwritten or lost");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void cityDeletionRemovesMembershipsClaimsAndInvalidatesPendingInvitations(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var pending = f.player(); var other = f.player();
            var city = f.city(owner); var second = f.city(other); f.join(owner, target, city);
            f.advance(); f.citizens.invite(owner, pending);
            f.data.deleteSettlement(city.id());
            h.assertTrue(f.data.citizenCity(owner.getUUID()) == null && f.data.citizenCity(target.getUUID()) == null
                    && f.data.citizens(city.id()).isEmpty() && f.data.settlementClaims(city.id()).isEmpty()
                    && f.data.citizenCity(other.getUUID()).id().equals(second.id()), "Deletion must remove only the deleted city's citizenship and land");
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(pending, city.id()));
            var reloaded = KingdomSavedData.load(f.saved(), h.getLevel().registryAccess()); reloaded.requireAvailable();
            h.assertTrue(reloaded.citizenCity(target.getUUID()) == null && reloaded.settlement(city.id()) == null, "Deletion must persist");
            f.join(other, target, second);
            var kingdom = f.kingdom(f.player()); f.data.add(kingdom);
            reject(f, CITY_IS_CAPITAL, () -> f.data.deleteSettlement(kingdom.capitalSettlementId()));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void lostOwnerAuthorityInvalidatesInvitation(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var target = f.player(); var successor = f.player(); var city = f.city(owner);
            f.citizens.invite(owner, target);
            // Simulate replacement of the authoritative city record; there is no player transfer command yet.
            f.data.deleteSettlement(city.id());
            f.data.addSettlement(new Settlement(city.id(), city.name(), city.founderId(), successor.getUUID(), city.dimension(),
                    city.obeliskPos(), city.obeliskId(), city.createdAt(), city.status(), null, city.color(), false));
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void citizenCanBuildBreakAndUseProtectedBlocksButCannotManageClaims(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var owner = f.player(); var target = f.player(); var outsider = f.player(); var city = f.city(owner); f.join(owner, target, city);
            var level = h.getLevel(); var pos = city.obeliskPos(); target.setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            h.assertTrue(ClaimService.canBuild(level, target, pos) && ClaimService.canBuild(level, owner, pos), "Citizen and owner can build");
            h.assertFalse(ClaimService.canBuild(level, outsider, pos), "Outsider must remain blocked");
            h.assertTrue(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, target, pos.below(), level.getBlockState(pos.below()), null),
                    "Actual break hook must allow citizen");
            h.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, outsider, pos.below(), level.getBlockState(pos.below()), null),
                    "Actual break hook must block outsider");
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
            h.assertTrue(UseBlockCallback.EVENT.invoker().interact(target, level, InteractionHand.OFF_HAND, hit) != InteractionResult.FAIL,
                    "Actual protected use hook must allow citizen");
            h.assertTrue(UseBlockCallback.EVENT.invoker().interact(outsider, level, InteractionHand.OFF_HAND, hit) == InteractionResult.FAIL,
                    "Actual protected use hook must block outsider");
            BlockItem stone = (BlockItem) Items.STONE;
            h.assertTrue(stone.place(new BlockPlaceContext(level, target, InteractionHand.MAIN_HAND, new ItemStack(stone), hit)).consumesAction()
                    && level.getBlockState(pos).is(Blocks.STONE), "Actual placement mixin must allow a citizen");
            var claims = new ClaimService(f.data); var capital = ClaimPos.at(city.dimension(), pos); var adjacent = capital.offset(1, 0);
            reject(f, NOT_OWNER, () -> claims.manage(target, capital, city.id(), true));
            target.setPos(adjacent.x() * 16 + 8, 64, adjacent.z() * 16 + 8);
            reject(f, NOT_OWNER, () -> claims.manage(target, adjacent, city.id(), false));
            owner.setPos(adjacent.x() * 16 + 8, 64, adjacent.z() * 16 + 8);
            claims.manage(owner, adjacent, city.id(), false); claims.manage(owner, adjacent, city.id(), true);
            f.citizens.kick(owner, target.getUUID());
            h.assertFalse(ClaimService.canBuild(level, target, pos), "Kicking must immediately revoke building rights");
            f.advance(); f.join(owner, target, city); f.citizens.leave(target);
            h.assertFalse(ClaimService.canBuild(level, target, pos), "Leaving must immediately revoke building rights");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void kingdomInvitationsAndAffiliationRemainSeparateFromCitizenship(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var ruler = f.player(); var mayor = f.player(); var citizen = f.player(); var pending = f.player();
            var kingdom = f.kingdom(ruler); f.data.add(kingdom); var city = f.city(mayor);
            f.join(mayor, citizen, city);
            f.advance(); f.citizens.invite(mayor, pending);
            reject(f, INVITATION_REQUIRED, () -> f.kingdoms.cities().join(mayor, kingdom.id()));
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(pending, kingdom.id()));
            f.kingdoms.cities().invite(ruler, mayor); f.kingdoms.cities().join(mayor, kingdom.id());
            h.assertTrue(f.data.citizenCity(citizen.getUUID()).id().equals(city.id())
                    && f.data.settlement(city.id()).sovereign().equals(kingdom.id())
                    && !f.data.isCitizen(city.id(), ruler.getUUID()), "Affiliation must preserve municipal membership without granting citizenship to ruler");
            reject(f, INVITATION_REQUIRED, () -> f.kingdoms.cities().join(mayor, kingdom.id()));
            f.citizens.accept(pending, city.id()); // Political changes must not invalidate a city invitation.
            f.data.deleteKingdom(kingdom.id());
            h.assertTrue(f.data.citizenCity(citizen.getUUID()).id().equals(city.id()) && f.data.isCitizen(city.id(), pending.getUUID())
                    && f.data.settlement(city.id()).sovereign() == null, "State dissolution cannot remove city citizenship");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void commandTreeAndLegacyAliasUseAuthenticatedPlayers(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var owner = f.player(); var target = f.player(); var city = f.city(owner);
            var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
            var ownerSource = h.getLevel().getServer().createCommandSourceStack().withEntity(owner).withPermission(0).withSuppressedOutput();
            var targetSource = ownerSource.withEntity(target);
            h.assertTrue(dispatcher.execute("nations citizen invite " + target.getGameProfile().getName(), ownerSource) == 1,
                    "Regular owner must invite through the real command tree");
            h.assertTrue(dispatcher.execute("reigns citizen accept " + city.id(), targetSource) == 1
                    && dispatcher.execute("nations citizen list", targetSource) == 1, "Alias must share acceptance and listing");
            h.assertTrue(dispatcher.execute("reigns citizen leave", ownerSource) == 0
                    && dispatcher.execute("nations citizen kick " + owner.getGameProfile().getName(), targetSource) == 0,
                    "Command actor cannot borrow owner authority");
            var offline = f.player();
            f.data.addCitizen(city.id(), offline.getUUID()); f.serverPlayers().remove(offline);
            h.assertTrue(dispatcher.execute("nations citizen kick " + offline.getUUID(), ownerSource) == 1,
                    "Owner command must kick offline citizens by UUID without a profile cache");
            h.assertTrue(dispatcher.execute("nations citizen kick " + target.getGameProfile().getName(), ownerSource) == 1
                    && f.data.citizenCity(target.getUUID()) == null, "Owner command must kick the resolved target UUID");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "citizenship")
    public void originalKingdomAffiliationCommandsStillWork(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var ruler = f.player(); var mayor = f.player(); var citizen = f.player();
            var kingdom = f.kingdom(ruler); f.data.add(kingdom); var city = f.city(mayor);
            f.join(mayor, citizen, city);
            var server = h.getLevel().getServer(); var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withEntity(ruler).withPermission(0).withSuppressedOutput();
            h.assertTrue(dispatcher.execute("nations invite " + mayor.getGameProfile().getName(), source) == 1
                    && dispatcher.execute("reigns join " + kingdom.id(), source.withEntity(mayor)) == 1,
                    "Original affiliation commands and alias must still accept a city owner");
            h.assertTrue(f.data.settlement(city.id()).sovereign().equals(kingdom.id())
                    && f.data.isCitizen(city.id(), citizen.getUUID()) && !f.data.isCitizen(city.id(), ruler.getUUID()),
                    "Command affiliation must not change citizenship");
        }
        h.succeed();
    }

    private interface Attempt { void run() throws Exception; }
    private static void reject(Fixture f, FoundingException.Reason reason, Attempt attempt) throws Exception {
        var before = f.saved(); f.data.setDirty(false);
        try { attempt.run(); f.h.fail("Expected " + reason); }
        catch (FoundingException e) { f.h.assertTrue(e.reason() == reason, "Expected " + reason + ", got " + e.reason()); }
        f.h.assertTrue(before.equals(f.saved()) && !f.data.isDirty(), "Rejected request must preserve registry and dirty state");
    }

    /** Synthetic server players exist in PlayerList only during each synchronous test, never across ticks. */
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final KingdomSavedData data;
        final KingdomService kingdoms;
        final CitizenshipService citizens;
        final AtomicLong now = new AtomicLong();
        final List<ServerPlayer> players = new ArrayList<>();
        Fixture(GameTestHelper h, boolean live) {
            this.h = h;
            kingdoms = live ? KingdomService.get(h.getLevel().getServer()) : new KingdomService(h.getLevel().getServer(), new KingdomSavedData());
            data = kingdoms.data();
            citizens = new CitizenshipService(kingdoms, h.getLevel().getServer(), data, now::get);
        }
        List<ServerPlayer> serverPlayers() { return h.getLevel().getServer().getPlayerList().getPlayers(); }
        ServerPlayer player() { return player(UUID.randomUUID()); }
        ServerPlayer player(UUID id) {
            var level = h.getLevel();
            var player = new ServerPlayer(level.getServer(), level, new GameProfile(id, "Citizen" + id.toString().substring(0, 8)), ClientInformation.createDefault()) {
                @Override public void displayClientMessage(Component message, boolean overlay) { }
            };
            player.setPos(0, 64, 0); players.add(player); serverPlayers().add(player); return player;
        }
        Settlement city(ServerPlayer owner) throws Exception {
            var city = new Settlement(UUID.randomUUID(), new KingdomName("Citizen City"), owner.getUUID(), owner.getUUID(), Level.OVERWORLD,
                    remote(), UUID.randomUUID(), 1, Kingdom.CapitalStatus.UNRESOLVED, null, 1, false);
            data.addSettlement(city); return city;
        }
        Kingdom kingdom(ServerPlayer owner) {
            return new Kingdom(UUID.randomUUID(), new KingdomName("State " + UUID.randomUUID().toString().substring(0, 8)), owner.getUUID(), Level.OVERWORLD,
                    remote(), UUID.randomUUID(), 1, Kingdom.CapitalStatus.UNRESOLVED);
        }
        private BlockPos remote() { return new BlockPos(2_000_008 + NEXT_LOCATION.getAndIncrement() * 32, 64, 2_000_008); }
        void join(ServerPlayer owner, ServerPlayer target, Settlement city) throws Exception { citizens.invite(owner, target); citizens.accept(target, city.id()); }
        void advance() { now.addAndGet(1_000_000_000L); }
        CompoundTag saved() { return data.save(new CompoundTag(), h.getLevel().registryAccess()); }
        @Override public void close() {
            for (var player : players) { serverPlayers().remove(player); citizens.disconnect(player.getUUID()); kingdoms.citizenship().disconnect(player.getUUID()); }
        }
    }
}
