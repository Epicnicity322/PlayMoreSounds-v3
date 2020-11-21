/*
 * Copyright (c) 2020 Christiano Rangel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.epicnicity322.playmoresounds.bukkit.command;

import com.epicnicity322.epicpluginlib.bukkit.command.Command;
import com.epicnicity322.epicpluginlib.bukkit.command.CommandManager;
import com.epicnicity322.epicpluginlib.bukkit.command.CommandRunnable;
import com.epicnicity322.epicpluginlib.bukkit.lang.MessageSender;
import com.epicnicity322.playmoresounds.bukkit.PlayMoreSounds;
import com.epicnicity322.playmoresounds.bukkit.command.subcommand.*;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CommandLoader
{
    private static final @NotNull MessageSender lang = PlayMoreSounds.getMessageSender();
    private static final @NotNull LinkedHashSet<Command> commands = new LinkedHashSet<>();
    private static CommandRunnable unknownCommand = null;
    private static CommandRunnable description = null;

    static {
        PlayMoreSounds.addOnInstanceRunnable(() -> {
            description = (label, sender, args) -> {
                lang.send(sender, false, lang.get("Description.Header").replace("<version>",
                        PlayMoreSounds.getVersion().getVersion()));
                lang.send(sender, false, "&6Author: &7Epicnicity322");
                lang.send(sender, false, "&6Description: &7" + PlayMoreSounds.getInstance().getDescription().getDescription());

                if (sender.hasPermission("playmoresounds.help"))
                    lang.send(sender, false, lang.get("Description.Help").replace("<label>", label));
                else
                    lang.send(sender, false, lang.get("Description.No Permission"));
            };

            unknownCommand = (label, sender, args) ->
                    lang.send(sender, lang.get("General.Unknown Command").replace("<label>", label));

            commands.add(new CheckSubCommand());
            commands.add(new ConfirmSubCommand());
            commands.add(new DiscSubCommand());
            commands.add(new HelpSubCommand());
            commands.add(new FinderSubCommand());

            // List command requires the server to run spigot.
            try {
                Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                commands.add(new ListSubCommand());
            } catch (ClassNotFoundException ignored) {
            }

            commands.add(new PlaySubCommand());
            commands.add(new RegionSubCommand());
            commands.add(new ReloadSubCommand());
            commands.add(new StopSoundSubCommand());
            commands.add(new ToggleSubCommand());
            commands.add(new UpdateSubCommand());
        });
    }

    private CommandLoader()
    {
    }

    /**
     * Adds a command to the list of command to be loaded on {@link #loadCommands()}.
     *
     * @param command The command to add to be registered.
     */
    public static void addCommand(@NotNull Command command)
    {
        commands.add(command);
    }

    /**
     * Removes a command from the list of command to be loaded on {@link #loadCommands()}.
     *
     * @param command The command to remove.
     */
    public static void removeCommand(@NotNull Command command)
    {
        commands.remove(command);
    }

    /**
     * @return A set with all the command that are being loaded on {@link #loadCommands()}.
     */
    public static @NotNull Set<Command> getCommands()
    {
        return Collections.unmodifiableSet(commands);
    }

    /**
     * Registers all sub commands to PlayMoreSounds main command.
     */
    public static void loadCommands()
    {
        CommandManager.registerCommand(Bukkit.getPluginCommand("playmoresounds"), commands, description,
                unknownCommand);
    }
}
