package dev.sakhno.regnum.nations.kingdom;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import dev.sakhno.regnum.nations.kingdom.CitizenshipTests.Fixture;
import static dev.sakhno.regnum.nations.kingdom.CityPermission.*;
import static dev.sakhno.regnum.nations.kingdom.CityRole.*;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

public class CityRoleTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void defaultRolesAndFullPermissionMatrixAreMunicipal(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var officer = f.player(); var citizen = f.player(); var outsider = f.player();
            var city = f.city(owner); f.join(owner, officer, city); f.advance(); f.join(owner, citizen, city);
            h.assertTrue(f.data.role(city.id(), owner.getUUID()) == OWNER && f.data.role(city.id(), officer.getUUID()) == CITIZEN
                    && f.data.role(city.id(), citizen.getUUID()) == CITIZEN && f.data.role(city.id(), outsider.getUUID()) == null,
                    "Owners are implicit, all new members default to CITIZEN, and outsiders have no role");
            f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            var identities = List.of(owner, officer, citizen, outsider);
            var expected = List.of(Set.of(CityPermission.values()), Set.of(BUILD, INTERACT, INVITE), Set.of(BUILD, INTERACT), Set.<CityPermission>of());
            for (int i = 0; i < identities.size(); i++) for (var permission : CityPermission.values())
                h.assertTrue(CityPermissions.allows(f.data, city, identities.get(i).getUUID(), permission) == expected.get(i).contains(permission),
                        "Incorrect default permission " + permission + " for " + i);
            var otherCity = f.city(outsider);
            for (var permission : CityPermission.values())
                h.assertFalse(CityPermissions.allows(f.data, otherCity, officer.getUUID(), permission), "Roles never grant rights in another city");
            h.assertTrue(f.saved().getList("Citizens", Tag.TAG_COMPOUND).size() == 2, "No owner or outsider role records may be stored");
            try { CityPermissions.permissions(OWNER).clear(); h.fail("Owner permissions must be immutable"); }
            catch (UnsupportedOperationException expectedFailure) { }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void ownerRoleCannotBeDemotedReassignedOrGranted(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var citizen = f.player(); var city = f.city(owner); f.join(owner, citizen, city);
            for (var role : CityRole.values()) reject(f, OWNER_ROLE_IMMUTABLE, () -> f.citizens.assignRole(owner, owner.getUUID(), role));
            reject(f, OWNER_ROLE_IMMUTABLE, () -> f.citizens.assignRole(owner, citizen.getUUID(), OWNER));
            reject(f, OWNER_ROLE_IMMUTABLE, () -> f.data.assignRole(city.id(), owner.getUUID(), OFFICER));
            reject(f, INVALID_CITY_ROLE, () -> f.citizens.assignRole(owner, citizen.getUUID(), null));
            h.assertTrue(f.data.settlement(city.id()).ownerId().equals(owner.getUUID()) && f.data.role(city.id(), owner.getUUID()) == OWNER,
                    "Role mutations cannot change settlement ownership");
            f.data.setDirty(false); f.citizens.assignRole(owner, citizen.getUUID(), CITIZEN);
            h.assertFalse(f.data.isDirty(), "Reassigning the same role must not dirty persistent state");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void onlyCurrentCityOwnerMayChangeRoles(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var officer = f.player(); var citizen = f.player(); var outsider = f.player();
            var city = f.city(owner); f.join(owner, officer, city); f.advance(); f.join(owner, citizen, city);
            f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            reject(f, NOT_OWNER, () -> f.citizens.assignRole(officer, citizen.getUUID(), OFFICER));
            reject(f, NOT_OWNER, () -> f.citizens.assignRole(citizen, citizen.getUUID(), OFFICER));
            reject(f, NO_CITY, () -> f.citizens.assignRole(outsider, citizen.getUUID(), OFFICER));
            reject(f, NOT_CITIZEN, () -> f.citizens.assignRole(owner, outsider.getUUID(), OFFICER));
            f.city(outsider);
            reject(f, NOT_CITIZEN, () -> f.citizens.assignRole(outsider, citizen.getUUID(), OFFICER));
            h.assertTrue(f.citizens.role(owner, citizen.getUUID()) == CITIZEN, "Unauthorized role requests cannot promote a citizen");
            var before = f.saved(); f.data.setDirty(false);
            boolean rejectedOffThread = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { f.citizens.assignRole(owner, citizen.getUUID(), OFFICER); return false; }
                catch (IllegalStateException expected) { return true; }
                catch (FoundingException failure) { throw new java.util.concurrent.CompletionException(failure); }
            }).join();
            h.assertTrue(rejectedOffThread && before.equals(f.saved()) && !f.data.isDirty(), "Role mutations require the server thread");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void officerCanInviteButCannotKickOrChangeRoles(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var officer = f.player(); var target = f.player(); var city = f.city(owner);
            f.join(owner, officer, city); f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            f.citizens.invite(officer, target); f.citizens.accept(target, city.id());
            h.assertTrue(f.citizens.role(owner, target.getUUID()) == CITIZEN, "Officer invites grant citizenship, never officer authority");
            reject(f, NOT_OWNER, () -> f.citizens.kick(officer, target.getUUID()));
            reject(f, NOT_OWNER, () -> f.citizens.kick(officer, owner.getUUID()));
            reject(f, NOT_OWNER, () -> f.citizens.assignRole(officer, target.getUUID(), OFFICER));
            var pending = f.player();
            reject(f, CITIZEN_RATE_LIMIT, () -> f.citizens.invite(officer, pending));
            f.advance(); f.citizens.invite(officer, pending); f.citizens.decline(pending, city.id());
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(pending, city.id()));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void demotionAndRemovalRevokeOfficerInvitations(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var officer = f.player(); var target = f.player(); var city = f.city(owner);
            f.join(owner, officer, city); f.citizens.assignRole(owner, officer.getUUID(), OFFICER); f.citizens.invite(officer, target);
            f.citizens.assignRole(owner, officer.getUUID(), CITIZEN);
            reject(f, NOT_OWNER, () -> f.citizens.invite(officer, f.player()));
            f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
            f.advance(); f.citizens.invite(officer, target);
            // Even an internal permission change must be caught by acceptance's fresh authority check.
            f.data.assignRole(city.id(), officer.getUUID(), CITIZEN);
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
            f.citizens.assignRole(owner, officer.getUUID(), OFFICER); f.advance(); f.citizens.invite(officer, target);
            f.citizens.kick(owner, officer.getUUID());
            f.join(owner, officer, city); f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
            f.advance(); f.citizens.invite(officer, target); f.citizens.leave(officer);
            reject(f, CITIZEN_INVITATION_REQUIRED, () -> f.citizens.accept(target, city.id()));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void officerCannotClaimOrUnclaimWhileOwnerRulesRemain(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var officer = f.player(); var city = f.city(owner);
            f.join(owner, officer, city); f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            var claims = new ClaimService(f.data); var capital = ClaimPos.at(city.dimension(), city.obeliskPos()); var adjacent = capital.offset(1, 0);
            officer.setPos(adjacent.x() * 16 + 8, 64, adjacent.z() * 16 + 8);
            reject(f, NOT_OWNER, () -> claims.manage(officer, adjacent, city.id(), false));
            owner.setPos(adjacent.x() * 16 + 8, 64, adjacent.z() * 16 + 8);
            claims.manage(owner, adjacent, city.id(), false);
            reject(f, NOT_OWNER, () -> claims.manage(officer, adjacent, city.id(), true));
            claims.manage(owner, adjacent, city.id(), true);
            owner.setPos(Vec3.atCenterOf(city.obeliskPos()));
            reject(f, CAPITAL_CHUNK, () -> claims.manage(owner, capital, city.id(), true));
            var diagonal = capital.offset(1, 1); owner.setPos(diagonal.x() * 16 + 8, 64, diagonal.z() * 16 + 8);
            reject(f, NOT_ADJACENT, () -> claims.manage(owner, diagonal, city.id(), false));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void citizenAndOfficerUseExistingBuildBreakAndInteractHooks(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var owner = f.player(); var officer = f.player(); var citizen = f.player(); var outsider = f.player();
            var city = f.city(owner); f.join(owner, officer, city); f.advance(); f.join(owner, citizen, city);
            f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            var level = h.getLevel(); var pos = city.obeliskPos();
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
            for (var member : List.of(owner, officer, citizen)) {
                member.setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
                h.assertTrue(ClaimService.canBuild(level, member, pos) && ClaimService.canAccess(level, member, pos, INTERACT), "Member can build and interact");
                h.assertTrue(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, member, pos.below(), level.getBlockState(pos.below()), null),
                        "Actual break hook must allow every initial member role");
                h.assertTrue(UseBlockCallback.EVENT.invoker().interact(member, level, InteractionHand.OFF_HAND, hit) != InteractionResult.FAIL,
                        "Actual interaction hook must allow every initial member role");
                member.setPos(0, 64, 0); // Synthetic peers have no connection to receive placement sounds.
            }
            officer.setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
            BlockItem stone = (BlockItem) Items.STONE;
            h.assertTrue(stone.place(new BlockPlaceContext(level, officer, InteractionHand.MAIN_HAND, new ItemStack(stone), hit)).consumesAction(),
                    "Actual placement mixin must allow an officer");
            h.assertFalse(ClaimService.canBuild(level, outsider, pos) || ClaimService.canAccess(level, outsider, pos, INTERACT), "Outsiders have neither block permission");
            h.assertTrue(UseBlockCallback.EVENT.invoker().interact(outsider, level, InteractionHand.OFF_HAND, hit) == InteractionResult.FAIL,
                    "Actual interaction hook must still deny outsiders");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void rolesSurviveSavedDataDiskReload(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var officer = f.player(); var citizen = f.player(); var city = f.city(owner);
            f.join(owner, officer, city); f.advance(); f.join(owner, citizen, city); f.citizens.assignRole(owner, officer.getUUID(), OFFICER);
            var directory = Files.createTempDirectory("regnum-nations-roles-");
            try {
                var level = h.getLevel();
                var writer = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
                writer.set(KingdomSavedData.FILE_ID, f.data); writer.save();
                var reader = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
                var loaded = reader.computeIfAbsent(KingdomSavedData.FACTORY, KingdomSavedData.FILE_ID); loaded.requireAvailable();
                h.assertTrue(loaded.role(city.id(), officer.getUUID()) == OFFICER && loaded.role(city.id(), citizen.getUUID()) == CITIZEN
                        && loaded.role(city.id(), owner.getUUID()) == OWNER && !loaded.isDirty(), "All roles must survive disk reload without a stored owner role");
                var service = new KingdomService(level.getServer(), loaded).citizenship();
                var target = f.player(); service.invite(officer, target); service.accept(target, city.id());
                h.assertTrue(loaded.role(city.id(), target.getUUID()) == CITIZEN, "Restored officer can invite after reload");
                var saved = loaded.save(new CompoundTag(), level.registryAccess());
                var again = KingdomSavedData.load(saved, level.registryAccess()); again.requireAvailable();
                h.assertTrue(saved.getInt("Version") == 6 && !again.isDirty() && saved.equals(again.save(new CompoundTag(), level.registryAccess())),
                        "Schema 6 reload must be idempotent");
            } finally {
                try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
                Files.delete(directory);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void schemaFourMembersMigrateToCitizensWithoutLosingHistory(GameTestHelper h) throws Exception {
        CompoundTag legacy;
        try (var input = getClass().getResourceAsStream("/fixtures/project-reigns-schema3.snbt")) {
            if (input == null) throw new IllegalStateException("Missing legacy fixture");
            legacy = TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        legacy.putInt("Version", 4);
        var members = new ListTag(); var member = new CompoundTag(); UUID player = new UUID(0, 20); UUID city = new UUID(0, 6);
        member.putUUID("Player", player); member.putUUID("Settlement", city); members.add(member); legacy.put("Citizens", members);
        var data = KingdomSavedData.load(legacy, h.getLevel().registryAccess()); data.requireAvailable();
        h.assertTrue(data.role(city, player) == CITIZEN && data.role(city, new UUID(0, 7)) == OWNER && data.isDirty(),
                "Schema 4 members default to CITIZEN and owner stays implicit");
        var saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(saved.getInt("Version") == 6 && saved.getList("Citizens", Tag.TAG_COMPOUND).getCompound(0).getString("Role").equals("CITIZEN")
                && Set.copyOf(saved.getList("Claims", Tag.TAG_COMPOUND)).equals(Set.copyOf(legacy.getList("Claims", Tag.TAG_COMPOUND)))
                && saved.getList("DeletedStateAnchors", Tag.TAG_COMPOUND).equals(legacy.getList("DeletedStateAnchors", Tag.TAG_COMPOUND))
                && data.kingdoms().size() == 1 && data.settlements().size() == 2, "Migration must retain political records, claims, tombstones, and identity");
        var reloaded = KingdomSavedData.load(saved, h.getLevel().registryAccess()); reloaded.requireAvailable();
        h.assertTrue(!reloaded.isDirty() && reloaded.role(city, player) == CITIZEN, "Migrated role persists on second load");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void invalidRolesPreserveOriginalNbtAndDisableMutations(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var citizen = f.player(); var city = f.city(owner); f.join(owner, citizen, city);
            for (int variant = 0; variant < 5; variant++) {
                var broken = f.saved(); var record = broken.getList("Citizens", Tag.TAG_COMPOUND).getCompound(0);
                switch (variant) {
                    case 0 -> record.putString("Role", "OWNER");
                    case 1 -> record.putString("Role", "CUSTOM");
                    case 2 -> record.putInt("Role", 1);
                    case 3 -> record.remove("Role");
                    case 4 -> { broken.putInt("Version", 4); record.putString("Role", "OWNER"); }
                }
                var loaded = KingdomSavedData.load(broken, h.getLevel().registryAccess());
                try { loaded.requireAvailable(); h.fail("Invalid role must disable mutations"); }
                catch (FoundingException e) { h.assertTrue(e.reason() == DATA_UNAVAILABLE, "Wrong invalid-role rejection"); }
                h.assertTrue(broken.equals(loaded.save(new CompoundTag(), h.getLevel().registryAccess())), "Invalid role NBT must survive verbatim");
                h.assertFalse(CityPermissions.allows(loaded, city, owner.getUUID(), BUILD), "An unreadable registry cannot grant owner rights");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void leaveKickAndCityDeletionCannotLeaveAssignedRoles(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var owner = f.player(); var member = f.player(); var city = f.city(owner); f.join(owner, member, city);
            f.citizens.assignRole(owner, member.getUUID(), OFFICER); f.citizens.leave(member);
            h.assertTrue(f.data.role(city.id(), member.getUUID()) == null && f.saved().getList("Citizens", Tag.TAG_COMPOUND).isEmpty(), "Leaving removes membership and assigned role");
            f.advance(); f.join(owner, member, city);
            h.assertTrue(f.data.role(city.id(), member.getUUID()) == CITIZEN, "Rejoining never restores an officer role");
            f.citizens.assignRole(owner, member.getUUID(), OFFICER); f.citizens.kick(owner, member.getUUID());
            h.assertTrue(f.data.role(city.id(), member.getUUID()) == null && f.saved().getList("Citizens", Tag.TAG_COMPOUND).isEmpty(), "Kicking removes the role record");
            f.advance(); f.join(owner, member, city); f.citizens.assignRole(owner, member.getUUID(), OFFICER);
            f.data.deleteSettlement(city.id());
            h.assertTrue(f.data.role(city.id(), member.getUUID()) == null && f.data.role(city.id(), owner.getUUID()) == null
                    && f.saved().getList("Citizens", Tag.TAG_COMPOUND).isEmpty(), "City deletion removes all roles, including the derived owner role");
            var loaded = KingdomSavedData.load(f.saved(), h.getLevel().registryAccess()); loaded.requireAvailable();
            h.assertTrue(loaded.citizenCity(member.getUUID()) == null, "No stale role survives reload after removal");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void kingdomAffiliationAndDissolutionPreserveMunicipalRoles(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false)) {
            var ruler = f.player(); var mayor = f.player(); var officer = f.player(); var city = f.city(mayor);
            var kingdom = f.kingdom(ruler); f.data.add(kingdom); f.join(mayor, officer, city); f.citizens.assignRole(mayor, officer.getUUID(), OFFICER);
            f.kingdoms.cities().invite(ruler, mayor); f.kingdoms.cities().join(mayor, kingdom.id());
            h.assertTrue(f.data.role(city.id(), officer.getUUID()) == OFFICER && f.data.role(city.id(), ruler.getUUID()) == null,
                    "Kingdom membership must neither remove roles nor assign one to its ruler");
            f.data.deleteKingdom(kingdom.id());
            h.assertTrue(f.data.role(city.id(), officer.getUUID()) == OFFICER && f.data.settlement(city.id()).sovereign() == null,
                    "Dissolving a kingdom must preserve officers in its surviving cities");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "city_roles")
    public void roleCommandsAliasAndListOutputUseServerAuthority(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var owner = f.player(); var officer = f.player(); var citizen = f.player(); var target = f.player();
            var city = f.city(owner); f.join(owner, officer, city); f.advance(); f.join(owner, citizen, city);
            var server = h.getLevel().getServer(); var dispatcher = server.getCommands().getDispatcher(); var output = new Output();
            var source = server.createCommandSourceStack().withEntity(owner).withPermission(0).withSource(output);
            h.assertTrue(dispatcher.execute("nations citizen role " + officer.getGameProfile().getName() + " OFFICER", source) == 1,
                    "Owner can promote using the authenticated command actor");
            h.assertTrue(dispatcher.execute("reigns citizen role " + citizen.getUUID() + " officer", source.withEntity(officer)) == 0
                    && dispatcher.execute("nations citizen role " + officer.getUUID() + " citizen", source.withEntity(citizen)) == 0
                    && dispatcher.execute("nations citizen role " + owner.getUUID() + " citizen", source) == 0
                    && dispatcher.execute("nations citizen role " + citizen.getUUID() + " owner", source) == 0
                    && dispatcher.execute("nations citizen role " + citizen.getUUID() + " custom", source) == 0,
                    "Commands must deny unauthorized, ownership-changing and unknown roles");
            output.messages.clear();
            h.assertTrue(dispatcher.execute("reigns citizen roles", source.withEntity(officer)) == 1 && output.messages.size() == 3,
                    "Alias must list the three role definitions");
            output.messages.clear(); dispatcher.execute("nations citizen list", source);
            h.assertTrue(output.messages.stream().anyMatch(message -> hasRole(message, officer.getUUID(), "officer"))
                    && output.messages.stream().anyMatch(message -> hasRole(message, owner.getUUID(), "owner"))
                    && output.messages.stream().anyMatch(message -> hasRole(message, citizen.getUUID(), "citizen")), "Citizen list must display all actual roles");
            output.messages.clear(); dispatcher.execute("nations citizen permissions", source.withEntity(officer));
            h.assertTrue(output.messages.size() == 6 && output.messages.stream().filter(message -> permissionValue(message, true)).count() == 3
                    && output.messages.stream().filter(message -> permissionValue(message, false)).count() == 2, "Officer permission command must display exactly the three allowed actions");
            h.assertTrue(dispatcher.execute("reigns citizen invite " + target.getGameProfile().getName(), source.withEntity(officer)) == 1
                    && dispatcher.execute("nations citizen accept " + city.id(), source.withEntity(target)) == 1
                    && dispatcher.execute("nations citizen kick " + citizen.getUUID(), source.withEntity(officer)) == 0
                    && dispatcher.execute("nations claim", source.withEntity(officer)) == 0, "Officer command authority extends only to invitations");
            f.serverPlayers().remove(officer);
            h.assertTrue(dispatcher.execute("reigns citizen role " + officer.getUUID() + " citizen", source) == 1
                    && f.data.role(city.id(), officer.getUUID()) == CITIZEN, "Owner can demote an offline officer by UUID");
        }
        h.succeed();
    }

    private static boolean hasRole(Component message, UUID player, String role) {
        if (!(message.getContents() instanceof TranslatableContents contents) || !contents.getKey().equals("citizen.regnum-nations.role_entry")) return false;
        Object[] args = contents.getArgs();
        return args[1].equals(player.toString()) && args[2] instanceof Component label
                && label.getContents() instanceof TranslatableContents translated && translated.getKey().equals("citizen.regnum-nations.role." + role);
    }
    private static boolean permissionValue(Component message, boolean allowed) {
        return message.getContents() instanceof TranslatableContents contents && contents.getKey().equals("citizen.regnum-nations.permission_entry")
                && contents.getArgs()[1] instanceof Component value && value.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("ui.regnum-nations." + (allowed ? "yes" : "no"));
    }
    private static final class Output implements CommandSource {
        final List<Component> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Fixture f, FoundingException.Reason reason, Attempt attempt) throws Exception {
        var before = f.saved(); f.data.setDirty(false);
        try { attempt.run(); f.h.fail("Expected " + reason); }
        catch (FoundingException e) { f.h.assertTrue(e.reason() == reason, "Expected " + reason + ", got " + e.reason()); }
        f.h.assertTrue(before.equals(f.saved()) && !f.data.isDirty(), "Rejected role request must preserve saved state and dirty flag");
    }
}
