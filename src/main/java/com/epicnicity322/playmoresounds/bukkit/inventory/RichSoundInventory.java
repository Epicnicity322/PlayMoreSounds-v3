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

package com.epicnicity322.playmoresounds.bukkit.inventory;

import com.epicnicity322.playmoresounds.bukkit.PlayMoreSounds;
import com.epicnicity322.playmoresounds.bukkit.sound.PlayableRichSound;
import com.epicnicity322.playmoresounds.bukkit.sound.PlayableSound;
import com.epicnicity322.playmoresounds.core.config.Configurations;
import com.epicnicity322.playmoresounds.core.sound.SoundCategory;
import com.epicnicity322.playmoresounds.core.util.PMSHelper;
import com.epicnicity322.yamlhandler.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class RichSoundInventory implements PMSInventory
{
    private static final @NotNull ArrayList<Material> soundMaterials = new ArrayList<>();

    static {
        Runnable materialsUpdater = () -> {
            soundMaterials.clear();

            ArrayList<String> materialNames = Configurations.CONFIG.getConfigurationHolder().getConfiguration().getCollection("Rich Sound Inventory.Items.Sound.Materials", Object::toString);
            materialNames.forEach(materialName -> {
                Material material = Material.matchMaterial(materialName);
                if (material != null && !material.isAir()) {
                    soundMaterials.add(material);
                }
            });
        };
        materialsUpdater.run();
        PlayMoreSounds.onEnable(materialsUpdater);
        PlayMoreSounds.onReload(materialsUpdater);
    }

    private final @NotNull PlayableRichSound richSound;

    private final @NotNull Inventory inventory;
    private final @NotNull HashMap<Integer, Consumer<InventoryClickEvent>> buttons = new HashMap<>();
    private final @NotNull HashMap<PlayableSound, SoundProperties> childProperties;
    private final @NotNull AtomicInteger soundMaterialIndex = new AtomicInteger(0);
    private @NotNull HashMap<Integer, ArrayList<PlayableSound>> childSoundPages;

    public RichSoundInventory(@NotNull PlayableRichSound richSound)
    {
        this.richSound = richSound;

        Collection<PlayableSound> children = richSound.getChildSounds();
        this.childProperties = new HashMap<>(children.size());

        if (richSound.getSection() == null) {
            int count = 1;
            for (PlayableSound sound : children) {
                String id = Integer.toString(count++);
                childProperties.put(sound, new SoundProperties(id, new SoundInventory(sound, this, id)));
            }
        } else {
            for (PlayableSound sound : children) {
                String id = sound.getSection().getName();
                childProperties.put(sound, new SoundProperties(id, new SoundInventory(sound, this, id)));
            }
        }

        this.childSoundPages = PMSHelper.splitIntoPages(children, 36);

        int size = children.size() + 18;
        if (size > 54) size = 54;

        this.inventory = Bukkit.createInventory(null, size, PlayMoreSounds.getLanguage().get("Rich Sound Inventory.Title").replace("<richsound>", richSound.getName()));
        updateButtonsItems();
        fillChildSounds(1);
        InventoryUtils.fill(Material.BLACK_STAINED_GLASS_PANE, inventory, 9, 17);

        buttons.put(0, event -> {
            richSound.setEnabled(!richSound.isEnabled());
            updateButtonsItems();
        });
        buttons.put(8, event -> {
            richSound.setCancellable(!richSound.isCancellable());
            updateButtonsItems();
        });
        buttons.put(13, event -> {
            var newSound = new PlayableSound("block.note_block.pling", SoundCategory.MASTER, 10, 1, 0, null);
            var newSoundId = PMSHelper.getRandomString(6);
            var newSoundInventory = new SoundInventory(newSound, this, newSoundId);

            children.add(newSound);
            childProperties.put(newSound, new SoundProperties(newSoundId, newSoundInventory));
            this.childSoundPages = PMSHelper.splitIntoPages(children, 36);
            fillChildSounds(childSoundPages.size());
            newSoundInventory.openInventory(event.getWhoClicked());
        });
    }

    protected void updateButtonsItems()
    {
        inventory.setItem(0, parseItemStack("Enabled", Boolean.toString(richSound.isEnabled())));

        // Replacing variables of info item.
        ConfigurationSection section = richSound.getSection();
        ItemStack infoItem = InventoryUtils.getItemStack("Rich Sound Inventory.Items.Info");
        ItemMeta meta = infoItem.getItemMeta();
        var previousLore = meta.getLore();
        var newLore = new ArrayList<String>();
        for (String line : previousLore) {
            if (section != null) {
                Optional<Path> root = section.getRoot().getFilePath();
                if (line.contains("<section-root>")) {
                    if (root.isPresent()) {
                        line = line.replace("<section-path>", root.get().getFileName().toString());
                    } else {
                        continue;
                    }
                }
                line = line.replace("<section-path>", section.getPath());
            } else {
                if (line.contains("<section-path>") || line.contains("<section-root>")) {
                    continue;
                }
            }
            line = line.replace("<name>", richSound.getName());
            line = line.replace("<child-amount>", Integer.toString(richSound.getChildSounds().size()));

            newLore.add(line);
        }
        meta.setLore(newLore);
        infoItem.setItemMeta(meta);
        inventory.setItem(4, infoItem);

        inventory.setItem(8, parseItemStack("Cancellable", Boolean.toString(richSound.isCancellable())));
    }

    private void fillChildSounds(int page)
    {
        // Removing previous child sounds from previous page.
        for (int i = 18; i < inventory.getSize(); ++i) {
            inventory.setItem(i, null);
            buttons.remove(i);
        }

        // Adding next page button.
        if (page != childSoundPages.size()) {
            int nextPage = page + 1;

            inventory.setItem(16, parseItemStack("Next Page", Integer.toString(nextPage)));
            buttons.put(16, event -> fillChildSounds(nextPage));
        } else {
            // Removing previous 'next page' button in case there was one.
            buttons.remove(16);
            InventoryUtils.forceFill(Material.BLACK_STAINED_GLASS_PANE, inventory, 16, 16);
        }
        // Adding previous page button.
        if (page != 1) {
            int previousPage = page - 1;

            inventory.setItem(10, parseItemStack("Previous Page", Integer.toString(previousPage)));
            buttons.put(10, event -> fillChildSounds(previousPage));
        } else {
            // Removing previous 'previous page' button in case there was one.
            buttons.remove(10);
            InventoryUtils.forceFill(Material.BLACK_STAINED_GLASS_PANE, inventory, 10, 10);
        }
        // Adding 'Add Sound' button.
        inventory.setItem(13, parseItemStack("Add Sound", Integer.toString(page)));
        // Unlike change page buttons, add sound button does not need to be added to #buttons map again, because it does
        //the same thing everytime: add a new sound. So it's set on constructor.


        // Filling with the child sound on this page.
        ArrayList<PlayableSound> sounds = childSoundPages.get(page);
        int slot = 18;
        boolean glowing = Configurations.CONFIG.getConfigurationHolder().getConfiguration().getBoolean("Rich Sound Inventory.Items.Sound.Glowing").orElse(false);

        for (PlayableSound sound : sounds) {
            var soundItem = new ItemStack(nextSoundMaterial());
            var meta = soundItem.getItemMeta();
            var properties = getProperties(sound);

            meta.setDisplayName(PlayMoreSounds.getLanguage().getColored("Rich Sound Inventory.Items.Sound.Display Name").replace("<id>", properties.id));

            var lore = new ArrayList<String>();
            for (String line : PlayMoreSounds.getLanguage().getColored("Rich Sound Inventory.Items.Sound.Lore").split("<line>")) {
                lore.add(line.replace("<sound>", sound.getSound())
                        .replace("<volume>", Float.toString(sound.getVolume()))
                        .replace("<pitch>", Float.toString(sound.getPitch())));
            }
            meta.setLore(lore);

            if (glowing)
                meta.addEnchant(Enchantment.DURABILITY, 1, true);

            meta.addItemFlags(ItemFlag.values());
            soundItem.setItemMeta(meta);
            inventory.setItem(slot, soundItem);
            buttons.put(slot, event -> properties.childInventory.openInventory(event.getWhoClicked()));
            ++slot;
        }
    }

    private ItemStack parseItemStack(String name, String value)
    {
        if (value == null) value = "null";
        ItemStack itemStack = InventoryUtils.getItemStack("Rich Sound Inventory.Items." + name);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(meta.getDisplayName().replace("<value>", value));

        List<String> previousLore = meta.getLore();
        var lore = new ArrayList<String>(previousLore.size());

        for (String string : previousLore) lore.add(string.replace("<value>", value));

        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Material nextSoundMaterial()
    {
        int next = soundMaterialIndex.get();
        if (next + 1 >= soundMaterials.size()) {
            soundMaterialIndex.set(0);
        } else {
            soundMaterialIndex.set(next + 1);
        }
        return soundMaterials.get(next);
    }

    private SoundProperties getProperties(PlayableSound sound)
    {
        return childProperties.get(sound);
    }

    public @NotNull PlayableRichSound getRichSound()
    {
        return richSound;
    }

    @Override
    public @NotNull Inventory getInventory()
    {
        return inventory;
    }

    @Override
    public @NotNull HashMap<Integer, Consumer<InventoryClickEvent>> getButtons()
    {
        return buttons;
    }

    @Override
    public void openInventory(@NotNull HumanEntity humanEntity)
    {
        InventoryUtils.openInventory(inventory, buttons, humanEntity);
    }

    private record SoundProperties(String id, SoundInventory childInventory)
    {
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SoundProperties that = (SoundProperties) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(id);
        }
    }
}
