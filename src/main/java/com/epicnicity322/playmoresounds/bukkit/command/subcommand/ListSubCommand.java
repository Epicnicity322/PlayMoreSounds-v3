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

package com.epicnicity322.playmoresounds.bukkit.command.subcommand;

import com.epicnicity322.epicpluginlib.bukkit.command.Command;
import com.epicnicity322.epicpluginlib.bukkit.command.CommandRunnable;
import com.epicnicity322.epicpluginlib.bukkit.lang.MessageSender;
import com.epicnicity322.epicpluginlib.core.config.ConfigurationHolder;
import com.epicnicity322.playmoresounds.bukkit.PlayMoreSounds;
import com.epicnicity322.playmoresounds.bukkit.inventory.ListInventory;
import com.epicnicity322.playmoresounds.bukkit.util.VersionUtils;
import com.epicnicity322.playmoresounds.core.config.Configurations;
import com.epicnicity322.playmoresounds.core.sound.SoundType;
import com.epicnicity322.playmoresounds.core.util.PMSHelper;
import com.epicnicity322.yamlhandler.Configuration;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

public final class ListSubCommand extends Command implements Helpable
{
    private static final @NotNull ConfigurationHolder config = Configurations.CONFIG.getConfigurationHolder();
    private static final @NotNull HashMap<Integer, HashMap<Integer, ArrayList<String>>> soundPagesCache = new HashMap<>();
    private static final @NotNull MessageSender lang = PlayMoreSounds.getLanguage();

    static {
        // Clear cache on disable.
        PlayMoreSounds.onDisable(soundPagesCache::clear);
    }

    @Override
    public @NotNull String getName()
    {
        return "list";
    }

    @Override
    public @Nullable String getPermission()
    {
        return "playmoresounds.list";
    }

    @Override
    public @NotNull CommandRunnable onHelp()
    {
        return (label, sender, args) -> lang.send(sender, false, lang.get("Help.List").replace("<label>", label));
    }

    @Override
    protected @Nullable CommandRunnable getNoPermissionRunnable()
    {
        return (label, sender, args) -> lang.send(sender, lang.get("General.No Permission"));
    }

    // Using BaseComponent[] on HoverEvent is deprecated on newer versions of spigot but is necessary on older ones.
    @SuppressWarnings(value = "deprecation")
    @Override
    public void run(@NotNull String label, @NotNull CommandSender sender, @NotNull String[] args)
    {
        boolean gui = sender instanceof Player && sender.hasPermission("playmoresounds.list.gui") && VersionUtils.hasPersistentData();
        int page = 1;
        String invalidArgs = lang.get("General.Invalid Arguments").replace("<label>", label).replace(
                "<label2>", args[0]).replace("<args>", "[" + lang.get("List.Page")
                + "] [--gui]");

        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                gui = false;
            } catch (NumberFormatException e) {
                lang.send(sender, lang.get("General.Not A Number").replace("<number>", args[1]));
                return;
            }

            if (args.length > 2) {
                if (args[2].equalsIgnoreCase("--gui")) {
                    if (sender instanceof Player) {
                        if (sender.hasPermission("playmoresounds.list.gui")) {
                            if (VersionUtils.hasPersistentData()) {
                                gui = true;
                            } else {
                                lang.send(sender, lang.get("List.Inventory.Error.Not Supported"));
                                return;
                            }
                        } else {
                            lang.send(sender, lang.get("General.No Permission"));
                            return;
                        }
                    } else {
                        lang.send(sender, lang.get("General.Not A Player"));
                        return;
                    }
                } else {
                    lang.send(sender, invalidArgs);
                    return;
                }
            }
        }

        if (gui) {
            ListInventory listInventory = new ListInventory(page);
            Player player = (Player) sender;

            listInventory.openInventory(player);
        } else {
            Configuration yamlConfig = ListSubCommand.config.getConfiguration();
            HashMap<Integer, ArrayList<String>> soundPages;
            int soundsPerPage = yamlConfig.getNumber("List.Default.Max Per Page").orElse(10).intValue();

            if (soundsPerPage < 1)
                soundsPerPage = 1;

            if (soundPagesCache.containsKey(soundsPerPage)) {
                soundPages = soundPagesCache.get(soundsPerPage);
            } else {
                soundPages = PMSHelper.splitIntoPages(new TreeSet<>(SoundType.getPresentSoundNames()), soundsPerPage);

                soundPagesCache.put(soundsPerPage, soundPages);
            }

            if (page > soundPages.size()) {
                lang.send(sender, lang.get("List.Error.Not Exists").replace("<page>",
                        Long.toString(page)).replace("<totalpages>", Integer.toString(soundPages.size())));
                return;
            }

            lang.send(sender, lang.get("List.Header").replace("<page>", Long.toString(page))
                    .replace("<totalpages>", Integer.toString(soundPages.size())));

            boolean alternatePrefix = false;
            int count = 1;
            TextComponent text = new TextComponent("");
            StringBuilder data = new StringBuilder();
            ArrayList<String> soundList = soundPages.get(page);

            for (String sound : soundList) {
                String prefix;

                if (alternatePrefix)
                    prefix = yamlConfig.getString("List.Default.Alternate Color").orElse("&8");
                else
                    prefix = yamlConfig.getString("List.Default.Color").orElse("&e");

                String separator = yamlConfig.getString("List.Default.Separator").orElse(", ");

                if (count++ == soundList.size())
                    separator = "";

                if (sender instanceof Player) {
                    TextComponent fancySound = new TextComponent((prefix + sound).replace("&",
                            "§"));

                    if (VersionUtils.hasHoverContentApi())
                        fancySound.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(lang
                                .get("List.Sound Tooltip").replace("&", "§").replace("<sound>", sound))));
                    else
                        fancySound.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(lang
                                .get("List.Sound Tooltip").replace("&", "§").replace("<sound>", sound)).create()));

                    fancySound.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pms play " +
                            sound + " " + sender.getName()));

                    text.addExtra(fancySound);
                    text.addExtra(separator.replace("&", "§"));
                } else {
                    data.append(prefix).append(sound).append(separator);
                }

                alternatePrefix = !alternatePrefix;
            }

            if (sender instanceof Player)
                ((Player) sender).spigot().sendMessage(text);
            else
                lang.send(sender, false, data.toString());

            if (page != soundPages.size()) {
                String footer = lang.get("List.Footer").replace("<label>", label).replace("<page>",
                        Long.toString(page + 1));

                if (sender instanceof Player) {
                    TextComponent fancyFooter = new TextComponent(footer.replace("&", "§"));

                    fancyFooter.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pms list " + (page + 1)));
                    ((Player) sender).spigot().sendMessage(fancyFooter);
                } else {
                    lang.send(sender, false, footer);
                }
            }
        }
    }
}
