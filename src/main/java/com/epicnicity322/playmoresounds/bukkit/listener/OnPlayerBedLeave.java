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

package com.epicnicity322.playmoresounds.bukkit.listener;

import com.epicnicity322.playmoresounds.bukkit.PlayMoreSounds;
import com.epicnicity322.playmoresounds.bukkit.sound.PlayableRichSound;
import com.epicnicity322.playmoresounds.core.config.Configurations;
import com.epicnicity322.yamlhandler.Configuration;
import com.epicnicity322.yamlhandler.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.jetbrains.annotations.NotNull;

public final class OnPlayerBedLeave extends PMSListener
{
    private final @NotNull PlayMoreSounds plugin;
    private PlayableRichSound bedLeave;
    private PlayableRichSound wakeUp;

    public OnPlayerBedLeave(@NotNull PlayMoreSounds plugin)
    {
        super(plugin);

        this.plugin = plugin;
    }

    @Override
    public @NotNull String getName()
    {
        return "Bed Leave|Wake Up";
    }

    @Override
    public void load()
    {
        Configuration sounds = Configurations.SOUNDS.getConfigurationHolder().getConfiguration();
        ConfigurationSection leave = sounds.getConfigurationSection("Bed Leave");
        ConfigurationSection wake = sounds.getConfigurationSection("Wake Up");
        boolean leaveEnabled = leave != null && leave.getBoolean("Enabled").orElse(false);
        boolean wakeEnabled = wake != null && wake.getBoolean("Enabled").orElse(false);

        if (leaveEnabled || wakeEnabled) {
            if (leaveEnabled)
                bedLeave = new PlayableRichSound(leave);

            if (wakeEnabled)
                wakeUp = new PlayableRichSound(wake);

            if (!isLoaded()) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                setLoaded(true);
            }
        } else {
            if (isLoaded()) {
                HandlerList.unregisterAll(this);
                setLoaded(false);
            }
        }
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event)
    {
        Player player = event.getPlayer();

        if (bedLeave != null)
            bedLeave.play(player);

        if (wakeUp != null) {
            long time = player.getWorld().getTime();

            if (time < 300)
                wakeUp.play(player);
        }
    }
}
