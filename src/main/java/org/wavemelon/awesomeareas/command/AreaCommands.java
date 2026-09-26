package org.wavemelon.awesomeareas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.wavemelon.awesomeareas.AwesomeAreas;
import org.wavemelon.awesomeareas.core.area.Area;
import org.wavemelon.awesomeareas.core.area.AreaType;
import org.wavemelon.awesomeareas.core.boundary.BoxBoundary;
import org.wavemelon.awesomeareas.core.hierarchy.HierarchyValidator;
import org.wavemelon.awesomeareas.core.boundary.Boundary;
import org.wavemelon.awesomeareas.core.history.AreaHistoryEntry;
import org.wavemelon.awesomeareas.core.history.GameCalendar;
import org.wavemelon.awesomeareas.core.home.Home;
import org.wavemelon.awesomeareas.core.population.PopulationService;
import org.wavemelon.awesomeareas.core.role.PlayerRole;
import org.wavemelon.awesomeareas.core.storage.AreaManager;
import org.wavemelon.awesomeareas.network.OpenAreaMapPayload;

import java.util.*;

/**
 * Commands for area creation, inspection, management, claiming, renaming, and map viewing.
 */
public class AreaCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("area")
                // /area create <type> <name> <x1> <z1> <x2> <z2> [parentName]
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (AreaType t : AreaType.values()) {
                                        b.suggest(t.name().toLowerCase(Locale.ROOT));
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                        .executes(c -> executeCreate(c, null))
                                                                        .then(Commands.argument("parent", StringArgumentType.greedyString())
                                                                                .suggests((c, b) -> {
                                                                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                                                                        b.suggest(a.getName());
                                                                                    }
                                                                                    return b.buildFuture();
                                                                                })
                                                                                .executes(c -> executeCreate(c, StringArgumentType.getString(c, "parent")))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                // /area claim <type> <name> [radius] [parentName]
                .then(Commands.literal("claim")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (AreaType t : AreaType.values()) {
                                        b.suggest(t.name().toLowerCase(Locale.ROOT));
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(c -> executeClaim(c, 50, null))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100000))
                                                .executes(c -> executeClaim(c, IntegerArgumentType.getInteger(c, "radius"), null))
                                                .then(Commands.argument("parent", StringArgumentType.greedyString())
                                                        .suggests((c, b) -> {
                                                            for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                                                b.suggest(a.getName());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(c -> executeClaim(c, IntegerArgumentType.getInteger(c, "radius"), StringArgumentType.getString(c, "parent")))
                                                )
                                        )
                                )
                        )
                )
                // /area rename <area> <newName>
                .then(Commands.literal("rename")
                        .then(Commands.argument("area", StringArgumentType.string())
                                .suggests((c, b) -> {
                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                        b.suggest(a.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("newName", StringArgumentType.greedyString())
                                        .executes(AreaCommands::executeRename)
                                )
                        )
                )
                // /area info [name]
                .then(Commands.literal("info")
                        .executes(c -> executeInfo(c, null))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                        b.suggest(a.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> executeInfo(c, StringArgumentType.getString(c, "name")))
                        )
                )
                // /area list
                .then(Commands.literal("list")
                        .executes(AreaCommands::executeList)
                )
                // /area delete <name>
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                        b.suggest(a.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(AreaCommands::executeDelete)
                        )
                )
                // /area sethome
                .then(Commands.literal("sethome")
                        .executes(AreaCommands::executeSetHome)
                )
                // /area home
                .then(Commands.literal("home")
                        .executes(AreaCommands::executeHome)
                )
                // /area setleader <area> <player>
                .then(Commands.literal("setleader")
                        .then(Commands.argument("area", StringArgumentType.string())
                                .suggests((c, b) -> {
                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                        b.suggest(a.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AreaCommands::executeSetLeader)
                                )
                        )
                )
                // /area setcapital <area> <city>
                .then(Commands.literal("setcapital")
                        .then(Commands.argument("area", StringArgumentType.string())
                                .suggests((c, b) -> {
                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                        b.suggest(a.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("city", StringArgumentType.greedyString())
                                        .suggests((c, b) -> {
                                            for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                                if (a.getType() == AreaType.CITY) {
                                                    b.suggest(a.getName());
                                                }
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(AreaCommands::executeSetCapital)
                                )
                        )
                )
                // /area map [name]
                .then(Commands.literal("map")
                        .executes(c -> executeOpenMap(c, ""))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    for (Area a : AwesomeAreas.getAreaManager().getAllAreas()) {
                                        b.suggest(a.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> executeOpenMap(c, StringArgumentType.getString(c, "name")))
                        )
                )
        );
    }

    /**
     * Checks if the command sender has authority to mutate an area.
     * Server operators (permission level 2) have full authority.
     * Regular players must be the designated leader of the area.
     */
    public static boolean canModifyArea(CommandSourceStack source, Area area) {
        if (area == null) return false;
        if (source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            return area.getLeaderUuid() != null && area.getLeaderUuid().equals(player.getUUID());
        }
        return true;
    }

    private static int executeRename(CommandContext<CommandSourceStack> ctx) {
        String rawAreaName = StringArgumentType.getString(ctx, "area");
        String rawNewName = StringArgumentType.getString(ctx, "newName");
        String areaName = AreaManager.cleanName(rawAreaName);
        String newName = AreaManager.cleanName(rawNewName);

        AreaManager manager = AwesomeAreas.getAreaManager();
        Area area = manager.getAreaByName(areaName);
        if (area == null) {
            ctx.getSource().sendFailure(Component.literal("Area '" + areaName + "' was not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!canModifyArea(ctx.getSource(), area)) {
            ctx.getSource().sendFailure(Component.literal("You do not have permission to modify this area. Only the area leader or server operators can make changes.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (manager.getAreaByName(newName) != null) {
            ctx.getSource().sendFailure(Component.literal("An area with the name '" + newName + "' already exists.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String actor = ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getName().getString() : "Server Operator";
        String oldName = area.getName();
        String inGameDate = GameCalendar.formatCurrentDate(ctx.getSource().getLevel());
        boolean success = manager.renameArea(area.getId(), newName, actor, inGameDate);
        if (!success) {
            ctx.getSource().sendFailure(Component.literal("Failed to rename area.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully renamed ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(oldName).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(area.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), true);

        return 1;
    }

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, String rawParentName) {
        String typeStr = StringArgumentType.getString(ctx, "type");
        String name = AreaManager.cleanName(StringArgumentType.getString(ctx, "name"));
        String parentName = rawParentName != null ? AreaManager.cleanName(rawParentName) : null;
        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
        int z2 = IntegerArgumentType.getInteger(ctx, "z2");

        AreaType type;
        try {
            type = AreaType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid area type '" + typeStr + "'. Valid types: Country, Province, County, City, Tribe, District.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        AreaManager manager = AwesomeAreas.getAreaManager();
        BoxBoundary boundary = new BoxBoundary(x1, z1, x2, z2);
        if (type == AreaType.COUNTRY && !AreaManager.validateCountryWaterClaim(boundary, ctx.getSource().getLevel())) {
            ctx.getSource().sendFailure(Component.literal("Cannot claim country territory: Water blocks cannot be claimed unless there is land within 20 blocks.").withStyle(ChatFormatting.RED));
            return 0;
        }
            Area area = new Area(UUID.randomUUID(), name, type, boundary);

        if (parentName != null && !parentName.isBlank()) {
            Area parent = manager.getAreaByName(parentName);
            if (parent == null) {
                ctx.getSource().sendFailure(Component.literal("Parent area '" + parentName + "' was not found.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            if (!canModifyArea(ctx.getSource(), parent)) {
                ctx.getSource().sendFailure(Component.literal("You do not have permission to create subdivisions inside '" + parent.getName() + "'. Only the leader or server operators can do so.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            area.setParentId(parent.getId());
        }

        // Assign creator as leader if run by player
        String actor = "System";
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            area.setLeader(player.getUUID(), player.getName().getString());
            actor = player.getName().getString();
        }
        String inGameDate = GameCalendar.formatCurrentDate(ctx.getSource().getLevel());
        area.addHistoryEntry(new AreaHistoryEntry(System.currentTimeMillis(), inGameDate, "CREATED", "Area created as " + area.getEffectiveTypeDisplayName(), actor, boundary));

        HierarchyValidator.ValidationResult result = manager.addArea(area);
        if (!result.valid()) {
            ctx.getSource().sendFailure(Component.literal("Cannot create area: " + result.message())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully created ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(area.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" (" + area.getEffectiveTypeDisplayName() + ") from [" + boundary.getMinX() + ", " + boundary.getMinZ() + "] to [" + boundary.getMaxX() + ", " + boundary.getMaxZ() + "].")
                        .withStyle(ChatFormatting.GREEN)), true);

        return 1;
    }

    private static int executeClaim(CommandContext<CommandSourceStack> ctx, int radius, String rawParentName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String typeStr = StringArgumentType.getString(ctx, "type");
            String name = AreaManager.cleanName(StringArgumentType.getString(ctx, "name"));
            String parentName = rawParentName != null ? AreaManager.cleanName(rawParentName) : null;

            AreaType type;
            try {
                type = AreaType.valueOf(typeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.literal("Invalid area type '" + typeStr + "'.").withStyle(ChatFormatting.RED));
                return 0;
            }

            int px = player.getBlockX();
            int pz = player.getBlockZ();
            int x1 = px - radius;
            int z1 = pz - radius;
            int x2 = px + radius;
            int z2 = pz + radius;

            AreaManager manager = AwesomeAreas.getAreaManager();
            BoxBoundary boundary = new BoxBoundary(x1, z1, x2, z2);
            if (type == AreaType.COUNTRY && !AreaManager.validateCountryWaterClaim(boundary, player.level())) {
                ctx.getSource().sendFailure(Component.literal("Cannot claim country territory: Water blocks cannot be claimed unless there is land within 20 blocks.").withStyle(ChatFormatting.RED));
                return 0;
            }
            Area area = new Area(UUID.randomUUID(), name, type, boundary);

            if (parentName != null && !parentName.isBlank()) {
                Area parent = manager.getAreaByName(parentName);
                if (parent == null) {
                    ctx.getSource().sendFailure(Component.literal("Parent area '" + parentName + "' was not found.")
                            .withStyle(ChatFormatting.RED));
                    return 0;
                }
                if (!canModifyArea(ctx.getSource(), parent)) {
                    ctx.getSource().sendFailure(Component.literal("You do not have permission to create subdivisions inside '" + parent.getName() + "'. Only the leader or server operators can do so.")
                            .withStyle(ChatFormatting.RED));
                    return 0;
                }
                area.setParentId(parent.getId());
            } else {
                // Auto-detect containing parent if applicable
                List<Area> containing = manager.getAreasAt(px, pz);
                for (Area candidate : containing) {
                    if (HierarchyValidator.validateParentChild(area, candidate, manager::getAreaById).valid()) {
                        if (canModifyArea(ctx.getSource(), candidate)) {
                            area.setParentId(candidate.getId());
                            break;
                        }
                    }
                }
            }

            area.setLeader(player.getUUID(), player.getName().getString());
            String inGameDate = GameCalendar.formatCurrentDate(player.level());
            area.addHistoryEntry(new AreaHistoryEntry(System.currentTimeMillis(), inGameDate, "CREATED", "Area claimed as " + area.getEffectiveTypeDisplayName(), player.getName().getString(), boundary));

            HierarchyValidator.ValidationResult result = manager.addArea(area);
            if (!result.valid()) {
                ctx.getSource().sendFailure(Component.literal("Cannot claim area: " + result.message())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            ctx.getSource().sendSuccess(() -> Component.literal("Claimed ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(area.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" (" + area.getEffectiveTypeDisplayName() + ") covering radius " + radius + " around you.")
                            .withStyle(ChatFormatting.GREEN)), true);

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Claim must be run by a player.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String rawAreaName) {
        AreaManager manager = AwesomeAreas.getAreaManager();
        Area area;

        if (rawAreaName != null && !rawAreaName.isBlank()) {
            String areaName = AreaManager.cleanName(rawAreaName);
            area = manager.getAreaByName(areaName);
            if (area == null) {
                ctx.getSource().sendFailure(Component.literal("Area '" + areaName + "' was not found.").withStyle(ChatFormatting.RED));
                return 0;
            }
        } else {
            try {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                area = manager.getInnermostAreaAt(player.getBlockX(), player.getBlockZ());
                if (area == null) {
                    ctx.getSource().sendSuccess(() -> Component.literal("You are currently in the Wilderness (no area claimed here).")
                            .withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }
            } catch (CommandSyntaxException e) {
                ctx.getSource().sendFailure(Component.literal("Please specify an area name.").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        PopulationService.AreaStatistics stats = PopulationService.computeStatistics(area, manager);

        // Build hierarchy breadcrumb path
        List<String> path = new ArrayList<>();
        Area curr = area;
        while (curr != null) {
            path.add(0, curr.getName());
            curr = curr.getParentId() != null ? manager.getAreaById(curr.getParentId()) : null;
        }
        String hierarchyPath = String.join(" > ", path);

        ctx.getSource().sendSuccess(() -> Component.literal("============== ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(area.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" ==============").withStyle(ChatFormatting.DARK_AQUA)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Type: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(area.getEffectiveTypeDisplayName()).withStyle(ChatFormatting.WHITE)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Hierarchy: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(hierarchyPath).withStyle(ChatFormatting.WHITE)), false);

        String leader = area.getLeaderName() != null ? area.getLeaderName() : "None";
        ctx.getSource().sendSuccess(() -> Component.literal("Leader: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(leader).withStyle(ChatFormatting.WHITE)), false);

        if (area.getCapitalAreaId() != null) {
            Area cap = manager.getAreaById(area.getCapitalAreaId());
            String capName = cap != null ? cap.getName() : "Unknown";
            ctx.getSource().sendSuccess(() -> Component.literal("Capital/Seat: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(capName).withStyle(ChatFormatting.WHITE)), false);
        }

        Boundary b = area.getBoundary();
        if (b != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("Bounds: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("[" + b.getMinX() + ", " + b.getMinZ() + "] to [" + b.getMaxX() + ", " + b.getMaxZ() + "] (" + b.getWidth() + "x" + b.getLength() + " blocks, " + String.format(Locale.ROOT, "%,d", b.getAreaBlocks()) + " m²)")
                            .withStyle(ChatFormatting.WHITE)), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Population: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(stats.population() + " registered residents").withStyle(ChatFormatting.GREEN)), false);

        if (!stats.residentNames().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Residents: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", stats.residentNames())).withStyle(ChatFormatting.WHITE)), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Sub-areas: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(stats.subAreasCount())).withStyle(ChatFormatting.WHITE)), false);

        if (!area.getPastNames().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Past Names: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(String.join(", ", area.getPastNames())).withStyle(ChatFormatting.GRAY)), false);
        }

        // Check command runner's role
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            PlayerRole role = manager.getPlayerRole(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("Your Global Role: ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(role.getDefaultTitle()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), false);
        }

        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        AreaManager manager = AwesomeAreas.getAreaManager();
        List<Area> roots = manager.getRootAreas();

        if (roots.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No areas currently registered in this world.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("===== Registered Areas (" + manager.getAllAreas().size() + ") =====")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), false);

        for (Area root : roots) {
            printAreaTree(ctx.getSource(), manager, root, 0);
        }

        return 1;
    }

    private static void printAreaTree(CommandSourceStack source, AreaManager manager, Area area, int indent) {
        String prefix = "  ".repeat(indent) + (indent > 0 ? "↳ " : "• ");
        Component line = Component.literal(prefix)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(area.getName())
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GOLD)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent.RunCommand("/area info " + area.getName()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to view details for " + area.getName())))
                        )
                )
                .append(Component.literal(" [" + area.getEffectiveTypeDisplayName() + "]")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" (Pop: " + manager.getPopulation(area.getId()) + ")")
                        .withStyle(ChatFormatting.GREEN));

        source.sendSuccess(() -> line, false);

        for (Area child : manager.getSubAreas(area.getId())) {
            printAreaTree(source, manager, child, indent + 1);
        }
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx) {
        String name = AreaManager.cleanName(StringArgumentType.getString(ctx, "name"));
        AreaManager manager = AwesomeAreas.getAreaManager();
        Area area = manager.getAreaByName(name);

        if (area == null) {
            ctx.getSource().sendFailure(Component.literal("Area '" + name + "' was not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!canModifyArea(ctx.getSource(), area)) {
            ctx.getSource().sendFailure(Component.literal("You do not have permission to delete this area. Only the area leader or server operators can do so.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        manager.removeArea(area.getId());
        ctx.getSource().sendSuccess(() -> Component.literal("Deleted area ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(area.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), true);

        return 1;
    }

    private static int executeSetHome(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            BlockPos pos = player.blockPosition();
            AreaManager manager = AwesomeAreas.getAreaManager();

            Home home = new Home(
                    UUID.randomUUID(),
                    player.getUUID(),
                    player.getName().getString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    player.level().dimension().identifier().toString(),
                    false
            );

            manager.setHome(home);

            List<Area> containing = manager.getAreasAt(pos.getX(), pos.getZ());
            StringBuilder locationDesc = new StringBuilder();
            if (containing.isEmpty()) {
                locationDesc.append("Wilderness");
            } else {
                for (int i = containing.size() - 1; i >= 0; i--) {
                    locationDesc.append(containing.get(i).getName());
                    if (i > 0) locationDesc.append(" > ");
                }
            }

            ctx.getSource().sendSuccess(() -> Component.literal("Home registered at [")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("]! Located inside: ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(locationDesc.toString()).withStyle(ChatFormatting.AQUA)), false);

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("sethome must be run by a player.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeHome(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            AreaManager manager = AwesomeAreas.getAreaManager();
            Home home = manager.getHomeByPlayer(player.getUUID());

            if (home == null) {
                ctx.getSource().sendSuccess(() -> Component.literal("You have no home registered. Use ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/area sethome").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" to register your residence.")), false);
                return 1;
            }

            List<Area> containing = manager.getAreasAt(home.getX(), home.getZ());
            StringBuilder locationDesc = new StringBuilder();
            if (containing.isEmpty()) {
                locationDesc.append("Wilderness");
            } else {
                for (int i = containing.size() - 1; i >= 0; i--) {
                    locationDesc.append(containing.get(i).getName());
                    if (i > 0) locationDesc.append(" > ");
                }
            }

            ctx.getSource().sendSuccess(() -> Component.literal("=== Your Home Residence ===")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), false);
            ctx.getSource().sendSuccess(() -> Component.literal("Coordinates: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("[" + home.getX() + ", " + home.getY() + ", " + home.getZ() + "] (" + home.getDimension() + ")")
                            .withStyle(ChatFormatting.WHITE)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("Jurisdiction: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(locationDesc.toString()).withStyle(ChatFormatting.AQUA)), false);

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("home must be run by a player.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeSetLeader(CommandContext<CommandSourceStack> ctx) {
        try {
            String areaName = AreaManager.cleanName(StringArgumentType.getString(ctx, "area"));
            ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "player");

            AreaManager manager = AwesomeAreas.getAreaManager();
            Area area = manager.getAreaByName(areaName);
            if (area == null) {
                ctx.getSource().sendFailure(Component.literal("Area '" + areaName + "' was not found.").withStyle(ChatFormatting.RED));
                return 0;
            }

            if (!canModifyArea(ctx.getSource(), area)) {
                ctx.getSource().sendFailure(Component.literal("You do not have permission to change the leader of this area. Only the current area leader or server operators can do so.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            String actor = ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getName().getString() : "Server Operator";
            area.setLeader(targetPlayer.getUUID(), targetPlayer.getName().getString());
            String inGameDate = GameCalendar.formatCurrentDate(ctx.getSource().getLevel());
            area.addHistoryEntry(new AreaHistoryEntry(System.currentTimeMillis(), inGameDate, "LEADER_CHANGED", "Leader assigned to " + targetPlayer.getName().getString(), actor, area.getBoundary()));
            manager.notifyAreaChanged(area);

            ctx.getSource().sendSuccess(() -> Component.literal("Assigned ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" as the leader of ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(area.getName()).withStyle(ChatFormatting.AQUA)), true);

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Target player not found.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeSetCapital(CommandContext<CommandSourceStack> ctx) {
        String areaName = AreaManager.cleanName(StringArgumentType.getString(ctx, "area"));
        String cityName = AreaManager.cleanName(StringArgumentType.getString(ctx, "city"));

        AreaManager manager = AwesomeAreas.getAreaManager();
        Area area = manager.getAreaByName(areaName);
        if (area == null) {
            ctx.getSource().sendFailure(Component.literal("Area '" + areaName + "' was not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!canModifyArea(ctx.getSource(), area)) {
            ctx.getSource().sendFailure(Component.literal("You do not have permission to set the capital of this area. Only the area leader or server operators can do so.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Area city = manager.getAreaByName(cityName);
        if (city == null) {
            ctx.getSource().sendFailure(Component.literal("City '" + cityName + "' was not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String actor = ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getName().getString() : "Server Operator";
        area.setCapitalAreaId(city.getId());
        String inGameDate = GameCalendar.formatCurrentDate(ctx.getSource().getLevel());
        area.addHistoryEntry(new AreaHistoryEntry(System.currentTimeMillis(), inGameDate, "CAPITAL_CHANGED", "Capital designated as " + city.getName(), actor, area.getBoundary()));
        manager.notifyAreaChanged(area);

        ctx.getSource().sendSuccess(() -> Component.literal("Designated ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(city.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" as the capital/seat of ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(area.getName()).withStyle(ChatFormatting.AQUA)), true);

        return 1;
    }

    private static int executeOpenMap(CommandContext<CommandSourceStack> ctx, String rawTargetArea) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            String targetArea = rawTargetArea != null ? AreaManager.cleanName(rawTargetArea) : "";
            ServerPlayNetworking.send(player, new OpenAreaMapPayload(targetArea));
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("This command can only be run by a player.").withStyle(ChatFormatting.RED));
        return 0;
    }
}
