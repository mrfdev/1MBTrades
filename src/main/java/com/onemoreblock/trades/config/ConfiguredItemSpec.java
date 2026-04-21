package com.onemoreblock.trades.config;

import java.util.List;
import org.bukkit.Material;

public record ConfiguredItemSpec(Material material, String name, List<String> lore) {
}

