/*
 * PlayMoreSounds - A bukkit plugin that manages and plays sounds.
 * Copyright (C) 2022 Christiano Rangel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.epicnicity322.playmoresounds.bukkit.command.subcommands;

import com.epicnicity322.epicpluginlib.bukkit.command.Command;
import com.epicnicity322.epicpluginlib.bukkit.command.CommandRunnable;
import com.epicnicity322.epicpluginlib.core.util.StringUtils;
import com.epicnicity322.playmoresounds.bukkit.PlayMoreSounds;
import com.epicnicity322.playmoresounds.bukkit.command.CommandUtils;
import com.epicnicity322.playmoresounds.bukkit.gui.inventories.RegionSoundInventory;
import com.epicnicity322.playmoresounds.bukkit.listeners.OnPlayerInteract;
import com.epicnicity322.playmoresounds.bukkit.region.RegionManager;
import com.epicnicity322.playmoresounds.bukkit.region.SoundRegion;
import com.epicnicity322.playmoresounds.bukkit.util.UniqueRunnable;
import com.epicnicity322.playmoresounds.core.PlayMoreSoundsCore;
import com.epicnicity322.playmoresounds.core.config.Configurations;
import com.epicnicity322.playmoresounds.core.util.PMSHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class RegionSubCommand extends Command implements Helpable
{
    /**
     * Borders are quite heavy on performance so there is a maximum amount of borders that can be shown at the same time.
     */
    private static final @NotNull AtomicInteger showingBorders = new AtomicInteger(0);
    private final @NotNull PlayMoreSounds plugin;

    public RegionSubCommand(@NotNull PlayMoreSounds plugin)
    {
        this.plugin = plugin;
    }

    @Override public @NotNull CommandRunnable onHelp()
    {
        return (label, sender, args) -> PlayMoreSounds.getLanguage().send(sender, false, PlayMoreSounds.getLanguage().get("Help.Region").replace("<label>", label));
    }

    @Override public @NotNull String getName()
    {
        return "region";
    }

    @Override public @Nullable String[] getAliases()
    {
        return new String[]{"regions", "rg"};
    }

    @Override public @Nullable String getPermission()
    {
        return "playmoresounds.region";
    }

    @Override public int getMinArgsAmount()
    {
        return 2;
    }

    @Override protected @Nullable CommandRunnable getNoPermissionRunnable()
    {
        return (label, sender, args) -> PlayMoreSounds.getLanguage().send(sender, PlayMoreSounds.getLanguage().get("General.No Permission"));
    }

    @Override protected @Nullable CommandRunnable getNotEnoughArgsRunnable()
    {
        return (label, sender, args) -> PlayMoreSounds.getLanguage().send(sender, PlayMoreSounds.getLanguage().get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", "<create|info|list|remove|rename|set|teleport|wand>"));
    }

    @Override public void run(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        switch (args[1].toLowerCase()) {
            case "create", "new" -> {
                if (!sender.hasPermission("playmoresounds.region.create")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                new Thread(() -> create(label, sender, args), "Region Builder").start();
            }
            case "info", "description" -> {
                if (!sender.hasPermission("playmoresounds.region.info")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                info(label, sender, args);
            }
            case "list", "l" -> {
                if (!sender.hasPermission("playmoresounds.region.list")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                list(label, sender, args);
            }
            case "remove", "delete", "rm", "del" -> {
                if (!sender.hasPermission("playmoresounds.region.remove")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                remove(label, sender, args);
            }
            case "rename", "newname", "setname", "rn" -> {
                if (!sender.hasPermission("playmoresounds.region.rename")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                rename(label, sender, args);
            }
            case "set" -> {
                if (!sender.hasPermission("playmoresounds.region.description") && !sender.hasPermission("playmoresounds.region.select.command")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                set(label, sender, args);
            }
            case "teleport", "tp" -> {
                if (!sender.hasPermission("playmoresounds.region.teleport")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                teleport(label, sender, args);
            }
            case "wand", "tool", "wandtool", "selectiontool" -> {
                if (!sender.hasPermission("playmoresounds.region.wand")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                wand(sender);
            }
            default -> getNotEnoughArgsRunnable().run(label, sender, args);
        }
    }

    private void create(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        UUID creator;

        if (sender instanceof Player player) creator = player.getUniqueId();
        else creator = null;

        Location[] selected = OnPlayerInteract.getSelectedDiagonals(creator);

        if (selected == null || selected[0] == null || selected[1] == null) {
            lang.send(sender, lang.get("Region.Create.Error.Not Selected").replace("<label>", label).replace("<label2>", args[0]));
            return;
        } else if (!selected[0].getWorld().equals(selected[1].getWorld())) {
            lang.send(sender, lang.get("Region.Create.Error.Different Worlds"));
            return;
        }

        String name;
        var config = Configurations.CONFIG.getConfigurationHolder().getConfiguration();

        if (args.length > 2) {
            name = args[2];

            if (RegionManager.getRegions().stream().anyMatch(region -> region.getName().equalsIgnoreCase(name))) {
                lang.send(sender, lang.get("Region.Create.Error.Already Exists"));
                return;
            }
            if (!SoundRegion.ALLOWED_REGION_NAME_CHARS.matcher(name).matches()) {
                lang.send(sender, lang.get("Region.Create.Error.Illegal Characters"));
                return;
            }

            int maxCharacters = config.getNumber("Sound Regions.Max Name Characters").orElse(20).intValue();

            if (name.length() > maxCharacters) {
                lang.send(sender, lang.get("Region.Create.Error.Max Name Characters").replace("<max>", Integer.toString(maxCharacters)));
                return;
            }
        } else name = PMSHelper.getRandomString(8);

        String description;

        if (args.length > 3 && sender.hasPermission("playmoresounds.region.description")) {
            StringBuilder builder = new StringBuilder();

            for (int i = 3; i < args.length; ++i)
                builder.append(" ").append(args[i]);

            description = builder.toString().trim();
        } else description = lang.getColored("Region.Create.Default Description");

        var region = new SoundRegion(name, selected[0], selected[1], creator, description);

        // Checking if the player exceeds the max created regions specified on config.
        if (sender instanceof Player) {
            if (!sender.hasPermission("playmoresounds.region.create.unlimited.regions")) {
                UUID playerId = ((Player) sender).getUniqueId();
                long amount = RegionManager.getRegions().stream().filter(soundRegion -> soundRegion.getCreator().equals(playerId)).count();
                long maxAmount = config.getNumber("Sounds Regions.Max Regions").orElse(5).longValue();

                if (amount >= maxAmount) {
                    lang.send(sender, lang.get("Region.Create.Error.Max Regions").replace("<max>", Long.toString(maxAmount)));
                    return;
                }
            }
        }

        // Checking if the region area is bigger than the specified on config.
        if (!sender.hasPermission("playmoresounds.region.create.unlimited.area")) {
            var min = region.getMinDiagonal();
            var max = region.getMaxDiagonal();

            long xSize = max.getBlockX() - min.getBlockX();
            long ySize = max.getBlockY() - min.getBlockY();
            long zSize = max.getBlockZ() - min.getBlockZ();

            long maxArea = config.getNumber("Sound Regions.Max Area").orElse(1000000).longValue();

            if (xSize * ySize * zSize > maxArea) {
                lang.send(sender, lang.get("Region.Create.Error.Max Area").replace("<max>", Long.toString(maxArea)));
                return;
            }
        }

        // Checking if any block of the selected area is inside another already existing region.
        if (!sender.hasPermission("playmoresounds.region.select.overlap")) {
            var min = region.getMinDiagonal();
            var max = region.getMaxDiagonal();

            // Filtering so it can check only the regions that are on this world and the regions that are not owned by the sender.
            Set<SoundRegion> regionsOnWorld = RegionManager.getRegions().stream().filter(otherRegion -> !Objects.equals(otherRegion.getCreator(), creator) && otherRegion.getMaxDiagonal().getWorld().equals(max.getWorld())).collect(Collectors.toSet());

            for (int x = min.getBlockX(); x <= max.getBlockX(); ++x)
                for (int y = min.getBlockY(); y <= max.getBlockY(); ++y)
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); ++z)
                        for (SoundRegion otherRegion : regionsOnWorld) {
                            var otherMin = otherRegion.getMinDiagonal();
                            var otherMax = otherRegion.getMaxDiagonal();

                            if (x >= otherMin.getBlockX() && x <= otherMax.getBlockX() & y >= otherMin.getBlockY() && y <= otherMax.getBlockY() & z >= otherMin.getBlockZ() && z <= otherMax.getBlockZ()) {
                                lang.send(sender, lang.get("Region.Select.Error.Overlap"));
                                return;
                            }
                        }
        }

        try {
            RegionManager.save(region);
            lang.send(sender, lang.get("Region.Create.Success").replace("<name>", name));
            OnPlayerInteract.selectDiagonal(creator, null, true);
            OnPlayerInteract.selectDiagonal(creator, null, false);
        } catch (IOException e) {
            lang.send(sender, lang.get("Region.Create.Error.Default").replace("<name>", name));
            PlayMoreSoundsCore.getErrorHandler().report(e, "Error while creating region \"" + name + "\":");
        }
    }

    private void info(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        var config = Configurations.CONFIG.getConfigurationHolder().getConfiguration();
        Set<SoundRegion> regions;

        if (args.length > 2) {
            regions = new HashSet<>();
            var region = getRegion(args[2], sender, null);

            if (region == null) {
                lang.send(sender, lang.get("Region.General.Error.Not Found." + (args[2].contains("-") ? "UUID" : "Name")).replace("<label>", label).replace("<label2>", args[0]));
                return;
            }

            regions.add(region);
        } else {
            if (sender instanceof Player player) {
                var location = player.getLocation();
                regions = RegionManager.getRegions(location);

                if (regions.isEmpty()) {
                    lang.send(sender, lang.get("Region.Info.Error.No Regions"));
                    return;
                }
            } else {
                lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " <" + lang.get("Region.Region") + ">"));
                return;
            }
        }

        var random = new Random();

        for (SoundRegion region : regions) {
            // Checking if particles should be sent.
            if (sender instanceof Player && showingBorders.get() < config.getNumber("Sound Regions.Border.Max Showing Borders").orElse(30).intValue()) {
                int count;
                double r, g, b;

                // If the player is standing on multiple regions, they should make different color particles.
                if (regions.size() == 1) {
                    count = 1;
                    r = 0;
                    g = 0;
                    b = 0;
                } else {
                    count = 0;
                    r = random.nextDouble();
                    g = random.nextDouble();
                    b = random.nextDouble();
                }

                showingBorders.incrementAndGet();

                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    for (Location border : region.getBorder())
                        ((Player) sender).spawnParticle(Particle.NOTE, border, count, r, g, b);
                }, 0, 5);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    task.cancel();
                    showingBorders.decrementAndGet();
                }, config.getNumber("Sound Regions.Border.Showing Time").orElse(100).longValue());
            }

            lang.send(sender, lang.get("Region.Info.Header").replace("<name>", region.getName()));
            lang.send(sender, false, lang.get("Region.Info.Owner").replace("<owner>", region.getCreator() == null ? "CONSOLE" : Bukkit.getOfflinePlayer(region.getCreator()).getName()));
            lang.send(sender, false, lang.get("Region.Info.Id").replace("<uuid>", region.getId().toString()));
            lang.send(sender, false, lang.get("Region.Info.World").replace("<world>", region.getMaxDiagonal().getWorld().getName()));
            lang.send(sender, false, lang.get("Region.Info.Creation Date").replace("<date>", region.getCreationDate().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))));
            lang.send(sender, false, lang.get("Region.Info.Description").replace("<description>", region.getDescription()));
        }
    }

    private void list(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        Set<SoundRegion> regions;
        String who;

        if (args.length > 2) {
            HashSet<Player> targets;

            if (sender instanceof Player) {
                targets = CommandUtils.getTargets(sender, args, 2, "", "playmoresounds.region.list.others");

                if (targets == null) return;

                who = CommandUtils.getWho(targets, sender);
            } else {
                String target = args[2].toLowerCase();

                if (target.equals("me") || target.equals("self") || target.equals("myself")) {
                    targets = new HashSet<>();

                    who = lang.get("General.You");
                } else {
                    targets = CommandUtils.getTargets(sender, args, 2, "", "playmoresounds.region.list.others");

                    if (targets == null) return;

                    who = CommandUtils.getWho(targets, sender);
                }
            }

            var uuidTargets = new HashSet<UUID>();

            targets.forEach(target -> uuidTargets.add(target.getUniqueId()));

            regions = RegionManager.getRegions().stream().filter(region -> {
                if (targets.isEmpty()) return region.getCreator() == null;
                else return uuidTargets.contains(region.getCreator());
            }).collect(Collectors.toSet());
        } else {
            if (sender instanceof Player) {
                UUID id = ((Player) sender).getUniqueId();

                regions = RegionManager.getRegions().stream().filter(region -> region.getCreator().equals(id)).collect(Collectors.toSet());
            } else {
                regions = RegionManager.getRegions().stream().filter(region -> region.getCreator() == null).collect(Collectors.toSet());
            }

            who = lang.get("General.You");
        }

        if (regions.isEmpty()) {
            lang.send(sender, lang.get("Region.List.Error.No Regions").replace("<targets>", who));
            return;
        }

        int page = 1;

        if (args.length > 3) {
            try {
                page = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                lang.send(sender, lang.get("General.Not A Number").replace("<number>", args[3]));
                return;
            }
        }

        if (page < 1) page = 1;

        HashMap<Integer, ArrayList<SoundRegion>> pages = PMSHelper.splitIntoPages(regions, 5);

        if (page > pages.size()) {
            lang.send(sender, lang.get("Region.List.Error.Not Exists").replace("<page>", Long.toString(page)).replace("<totalPages>", Integer.toString(pages.size())));
            return;
        }

        if (who.equals(lang.get("General.You")))
            lang.send(sender, lang.get("Region.List.Header.Default").replace("<page>", Long.toString(page)).replace("<totalPages>", Integer.toString(pages.size())));
        else
            lang.send(sender, lang.get("Region.List.Header.Player").replace("<targets>", who).replace("<page>", Long.toString(page)).replace("<totalPages>", Integer.toString(pages.size())));

        for (SoundRegion region : pages.get(page))
            lang.send(sender, false, lang.get("Region.List.Region").replace("<uuid>", region.getId().toString()).replace("<name>", region.getName()));

        if (page < pages.size())
            lang.send(sender, false, lang.get("Region.List.Footer").replace("<label>", label).replace("<label2>", args[0]).replace("<label3>", args[1]).replace("<label4>", args.length > 2 ? args[2] : "me").replace("<next>", Long.toString(page + 1)));
    }

    private void remove(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        if (args.length < 3) {
            lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " <name|uuid>"));
            return;
        }

        var region = getRegion(args[2], sender, "playmoresounds.region.remove.others");

        if (region == null) {
            lang.send(sender, lang.get("Region.General.Error.Not Found." + (args[2].contains("-") ? "UUID" : "Name")).replace("<label>", label).replace("<label2>", args[0]));
            return;
        }

        var name = region.getName();

        lang.send(sender, lang.get("Region.Remove.Confirm").replace("<label>", label).replace("<region>", name));

        ConfirmSubCommand.addPendingConfirmation(sender, new UniqueRunnable(region.getId())
        {
            @Override public void run()
            {
                try {
                    RegionManager.delete(region);
                    lang.send(sender, lang.get("Region.Remove.Success").replace("<region>", name));
                } catch (Exception ex) {
                    lang.send(sender, lang.get("Region.Remove.Error").replace("<region>", name));
                    PlayMoreSoundsCore.getErrorHandler().report(ex, "Error while deleting region " + name);
                }
            }
        }, lang.get("Region.Remove.Description").replace("<region>", name));
    }

    private void rename(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        if (args.length < 4) {
            lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " <" + lang.get("Region.Region") + "> <" + lang.get("Region.Rename.New Name") + ">"));
            return;
        }

        String oldName = args[2];
        String newName = args[3];

        if (oldName.equals(newName)) {
            lang.send(sender, lang.get("Region.Rename.Error.Same"));
            return;
        }

        if (RegionManager.getRegions().stream().anyMatch(region -> region.getName().equalsIgnoreCase(newName))) {
            lang.send(sender, lang.get("Region.Rename.Error.Already Exists"));
            return;
        }

        var region = getRegion(oldName, sender, "playmoresounds.region.rename.others");

        if (region == null) {
            lang.send(sender, lang.get("Region.General.Error.Not Found." + (oldName.contains("-") ? "UUID" : "Name")).replace("<label>", label).replace("<label2>", args[0]));
            return;
        }

        // Fixing case.
        oldName = region.getName();

        if (!SoundRegion.ALLOWED_REGION_NAME_CHARS.matcher(newName).matches()) {
            lang.send(sender, lang.get("Region.General.Error.Illegal Characters"));
            return;
        }

        var config = Configurations.CONFIG.getConfigurationHolder().getConfiguration();
        int maxCharacters = config.getNumber("Sound Regions.Max Name Characters").orElse(20).intValue();

        if (newName.length() > maxCharacters) {
            lang.send(sender, lang.get("Region.General.Error.Max Name Characters").replace("<max>", Integer.toString(maxCharacters)));
            return;
        }

        region.setName(newName);

        try {
            RegionManager.save(region);
            lang.send(sender, lang.get("Region.Rename.Success").replace("<region>", oldName).replace("<newName>", newName));
        } catch (Exception ex) {
            lang.send(sender, lang.get("Region.General.Error.Save").replace("<region>", region.getName()));
            PlayMoreSoundsCore.getErrorHandler().report(ex, region.getName() + " Region Save Error:");
        }
    }

    private void set(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        if (args.length < 3) {
            lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " <p1|p2|description>"));
            return;
        }

        final boolean p1;

        switch (args[2].toLowerCase()) {
            case "description", "desc", "info", "information" -> {
                if (!sender.hasPermission("playmoresounds.region.description")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                if (args.length < 5) {
                    lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " " + args[2] + " <" + lang.get("Region.Region") + "> <" + lang.get("General.Description") + ">"));
                    return;
                }

                var region = getRegion(args[3], sender, "playmoresounds.region.description.others");

                if (region == null) {
                    lang.send(sender, lang.get("Region.General.Error.Not Found." + (args[3].contains("-") ? "UUID" : "Name")).replace("<label>", label).replace("<label2>", args[0]));
                    return;
                }

                var descriptionBuilder = new StringBuilder();

                for (int i = 4; i < args.length; ++i) {
                    descriptionBuilder.append(" ").append(args[i]);
                }

                var description = descriptionBuilder.substring(1);

                if (description.length() > 100) {
                    lang.send(sender, lang.get("Region.Set.Description.Error.Max Characters"));
                    return;
                }

                region.setDescription(description);
                try {
                    RegionManager.save(region);
                    lang.send(sender, lang.get("Region.Set.Description.Success").replace("<region>", region.getName()).replace("<description>", description));
                } catch (Exception ex) {
                    lang.send(sender, lang.get("Region.General.Error.Save").replace("<region>", region.getName()));
                    PlayMoreSoundsCore.getErrorHandler().report(ex, region.getName() + " Region Save Error:");
                }
                return;
            }
            case "sounds", "sound" -> {
                if (!sender.hasPermission("playmoresounds.region.sound.enter") && !sender.hasPermission("playmoresounds.region.sound.leave")
                        && !sender.hasPermission("playmoresounds.region.sound.loop")) {
                    lang.send(sender, lang.get("General.No Permission"));
                    return;
                }

                sounds(label, sender, args);
                return;
            }
            case "p1", "pone", "one", "position1", "positionone", "firstposition", "first" -> p1 = true;
            case "p2", "ptwo", "two", "position2", "positiontwo", "secondposition", "second" -> p1 = false;
            default -> {
                lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " <p1|p2|description|sound>"));
                return;
            }
        }

        if (!sender.hasPermission("playmoresounds.region.select.command")) {
            lang.send(sender, lang.get("General.No Permission"));
            return;
        }

        Location location;

        if (args.length < 7) {
            if (sender instanceof Player player) {
                location = player.getLocation();
            } else {
                lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " " + args[2] + " <" + lang.get("General.World") + "> <x> <y> <z>"));
                return;
            }
        } else {
            try {
                var world = Bukkit.getWorld(args[3]);

                if (world == null) {
                    lang.send(sender, lang.get("Region.Set.Select.Error.Not A World").replace("<value>", args[3]));
                    return;
                }

                location = new Location(world, Double.parseDouble(args[4]), Double.parseDouble(args[5]), Double.parseDouble(args[6]));
            } catch (NumberFormatException ex) {
                var notNumber = "";

                if (!StringUtils.isNumeric(args[4])) notNumber = args[4];
                else if (!StringUtils.isNumeric(args[5])) notNumber = args[5];
                else if (!StringUtils.isNumeric(args[6])) notNumber = args[6];

                lang.send(sender, lang.get("General.Not A Number").replace("<number>", notNumber));
                return;
            }
        }

        UUID uuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        OnPlayerInteract.selectDiagonal(uuid, location, p1);
        lang.send(sender, lang.get("Region.Set.Select.Position." + (p1 ? "First" : "Second")).replace("<w>", location.getWorld().getName()).replace("<x>", Integer.toString(location.getBlockX())).replace("<y>", Integer.toString(location.getBlockY())).replace("<z>", Integer.toString(location.getBlockZ())));
    }

    private void teleport(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();
        if (!(sender instanceof Player)) {
            lang.send(sender, lang.get("General.Not A Player"));
            return;
        }
        if (args.length < 3) {
            lang.send(sender, lang.get("General.Invalid Arguments").replace("<label>", label).replace("<label2>", args[0]).replace("<args>", args[1] + " <" + lang.get("Region.Region") + ">"));
            return;
        }

        var region = getRegion(args[2], sender, "playmoresounds.region.teleport.others");

        if (region == null) {
            lang.send(sender, lang.get("Region.General.Error.Not Found." + (args[2].contains("-") ? "UUID" : "Name")).replace("<label>", label).replace("<label2>", args[0]));
            return;
        }

        lang.send(sender, lang.get("Region.Teleport.Success").replace("<region>", region.getName()));
        ((Player) sender).teleport(region.getMinDiagonal(), PlayerTeleportEvent.TeleportCause.COMMAND);
    }

    private void wand(@NotNull CommandSender sender)
    {
        var lang = PlayMoreSounds.getLanguage();
        if (!(sender instanceof Player)) {
            lang.send(sender, lang.get("General.Not A Player"));
            return;
        }

        var wand = RegionManager.getWand();

        if (wand == null) {
            lang.send(sender, lang.get("Region.Wand.Error.Config"));
            return;
        }

        ((Player) sender).getInventory().addItem(wand);
        lang.send(sender, lang.get("Region.Wand.Success"));
    }

    private void sounds(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        var lang = PlayMoreSounds.getLanguage();

        if (!(sender instanceof Player player)) {
            lang.send(sender, lang.get("General.Not A Player"));
            return;
        }

        final SoundRegion region;
        boolean multipleFound = false;

        if (args.length > 3) {
            region = getRegion(args[3], sender, "playmoresounds.region.sound.others");

            if (region == null) {
                lang.send(sender, lang.get("Region.General.Error.Not Found." + (args[3].contains("-") ? "UUID" : "Name")).replace("<label>", label).replace("<label2>", args[0]));
                return;
            }
        } else {
            Set<SoundRegion> locationRegions = RegionManager.getRegions(player.getLocation());

            if (locationRegions.isEmpty()) {
                lang.send(sender, lang.get("Region.Set.Sounds.Error.No Regions").replace("<label>", label).replace("<label2>", args[0]).replace("<label4>", args[2]));
                return;
            }

            if (!sender.hasPermission("playmoresounds.region.sound.others")) {
                locationRegions.removeIf(otherRegion -> !Objects.equals(otherRegion.getCreator(), player.getUniqueId()));

                if (locationRegions.isEmpty()) {
                    lang.send(sender, lang.get("Region.Set.Sounds.Error.No Owning Regions"));
                    return;
                }
            }

            region = locationRegions.iterator().next();
            if (locationRegions.size() > 1) multipleFound = true;
        }

        lang.send(sender, lang.get("Region.Set.Sounds.Editing." + (multipleFound ? "Multiple" : "Default")).replace("<region>", region.getName()));
        new RegionSoundInventory(region, player).openInventory();
    }

    /**
     * Gets a region by its name or {@link UUID}.
     *
     * @param nameOrUUID The name or uuid of the region.
     * @param sender     The owner of the region.
     * @param permission The permission if this player is allowed to get other peoples regions.
     * @return The region with this name or uuid, or null if not found.
     */
    private SoundRegion getRegion(@NotNull String nameOrUUID, @NotNull CommandSender sender, @Nullable String permission)
    {
        UUID creator = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        if (creator == null) permission = null;
        // Checking if nameOrUUID is an uuid since region names cannot contain '-'.
        boolean checkUUID = nameOrUUID.contains("-");

        // Only players with the permission are allowed to get regions made by other players.
        if (permission == null || sender.hasPermission(permission)) {
            for (var rg : RegionManager.getRegions())
                if (checkUUID ? rg.getId().toString().equalsIgnoreCase(nameOrUUID) : rg.getName().equalsIgnoreCase(nameOrUUID))
                    return rg;
        } else {
            for (var rg : RegionManager.getRegions())
                if (Objects.equals(rg.getCreator(), creator))
                    if (checkUUID ? rg.getId().toString().equalsIgnoreCase(nameOrUUID) : rg.getName().equalsIgnoreCase(nameOrUUID))
                        return rg;
        }
        return null;
    }
}
