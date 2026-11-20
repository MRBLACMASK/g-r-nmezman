package com.example.gorunmezmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GorunmezManhunt extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey wandKey;
    private UUID runnerId;
    private boolean invisible;
    private long lastToggle;
    private BukkitTask equipTask;

    private static final String TEAM_NAME = "gm_runner";
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Override
    public void onEnable() {
        wandKey = new NamespacedKey(this, "gorunmez_asa");
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("gorunmezmanhunt");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        setInvisible(false);
    }

    // ---------- Komut ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Kullanım: /" + label + " baslat <isim> | dur", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("baslat")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Kullanım: /" + label + " baslat <kaçanın ismi>", NamedTextColor.YELLOW));
                return true;
            }
            Player runner = Bukkit.getPlayerExact(args[1]);
            if (runner == null) {
                sender.sendMessage(Component.text("Oyuncu bulunamadı: " + args[1], NamedTextColor.RED));
                return true;
            }
            stopGame();
            runnerId = runner.getUniqueId();
            invisible = false;
            runner.getInventory().addItem(createWand());
            Bukkit.broadcast(Component.text("Görünmez Manhunt başladı! Kaçan: " + runner.getName(), NamedTextColor.GOLD));
            runner.sendMessage(Component.text("Asaya sol tıkla: görünmez ol / görünür ol.", NamedTextColor.LIGHT_PURPLE));
            return true;
        }

        if (sub.equals("dur")) {
            stopGame();
            Bukkit.broadcast(Component.text("Görünmez Manhunt durduruldu.", NamedTextColor.GOLD));
            return true;
        }

        sender.sendMessage(Component.text("Kullanım: /" + label + " baslat <isim> | dur", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("baslat", "dur")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("baslat")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }

    // ---------- Oyun mantığı ----------

    private void stopGame() {
        setInvisible(false);
        Player runner = runnerId == null ? null : Bukkit.getPlayer(runnerId);
        if (runner != null) {
            for (ItemStack it : runner.getInventory().getContents()) {
                if (isWand(it)) runner.getInventory().remove(it);
            }
        }
        runnerId = null;
    }

    /**
     * Runner'ı görünmez yapar. Oyuncu hunter'lar için sunucuda "var" kalır,
     * bu yüzden vurulabilir. Parçacık yok, zırh/eşya ve isim etiketi gizlenir.
     */
    private void setInvisible(boolean value) {
        invisible = value;
        if (equipTask != null) {
            equipTask.cancel();
            equipTask = null;
        }
        if (runnerId == null) return;
        Player runner = Bukkit.getPlayer(runnerId);
        if (runner == null) return;

        runner.setInvisible(value);

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) {
            team = sb.registerNewTeam(TEAM_NAME);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        if (value) {
            team.addEntry(runner.getName());
            equipTask = Bukkit.getScheduler().runTaskTimer(this, this::hideEquipment, 1L, 2L);
        } else {
            team.removeEntry(runner.getName());
            for (Player hunter : Bukkit.getOnlinePlayers()) {
                if (hunter.equals(runner)) continue;
                for (EquipmentSlot slot : SLOTS) {
                    hunter.sendEquipmentChange(runner, slot, runner.getInventory().getItem(slot));
                }
            }
        }
    }

    /** Hunter'lara runner'ın zırhını ve elindeki eşyaları boş gösterir. */
    private void hideEquipment() {
        if (runnerId == null || !invisible) return;
        Player runner = Bukkit.getPlayer(runnerId);
        if (runner == null) return;
        ItemStack air = new ItemStack(Material.AIR);
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            if (hunter.equals(runner)) continue;
            for (EquipmentSlot slot : SLOTS) {
                hunter.sendEquipmentChange(runner, slot, air);
            }
        }
    }

    private ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Görünmezlik Asası", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Sol tık: görünmez ol / görünür ol", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    // ---------- Olaylar ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!isWand(p.getInventory().getItemInMainHand())) return;

        e.setCancelled(true);
        if (runnerId == null || !p.getUniqueId().equals(runnerId)) return;

        long now = System.currentTimeMillis();
        if (now - lastToggle < 500) return; // spam engeli
        lastToggle = now;

        setInvisible(!invisible);
        p.sendActionBar(invisible
                ? Component.text("Görünmezsin", NamedTextColor.GREEN)
                : Component.text("Görünürsün", NamedTextColor.RED));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isWand(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (runnerId != null && e.getPlayer().getUniqueId().equals(runnerId) && invisible) {
            setInvisible(false);
            e.getPlayer().sendActionBar(Component.text("Öldün, görünürsün", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (runnerId != null && e.getPlayer().getUniqueId().equals(runnerId)) {
            setInvisible(false);
        }
    }
}
