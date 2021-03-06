package com.elikill58.negativity.universal.adapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.entity.living.player.Player;

import com.elikill58.negativity.sponge.SpongeNegativity;
import com.elikill58.negativity.sponge.SpongeNegativityPlayer;
import com.elikill58.negativity.universal.Cheat;
import com.elikill58.negativity.universal.NegativityAccountManager;
import com.elikill58.negativity.universal.NegativityPlayer;
import com.elikill58.negativity.universal.ProxyCompanionManager;
import com.elikill58.negativity.universal.ReportType;
import com.elikill58.negativity.universal.SimpleAccountManager;
import com.elikill58.negativity.universal.config.ConfigAdapter;
import com.elikill58.negativity.universal.translation.CachingTranslationProvider;
import com.elikill58.negativity.universal.translation.ConfigurateTranslationProvider;
import com.elikill58.negativity.universal.translation.TranslationProvider;
import com.elikill58.negativity.universal.translation.TranslationProviderFactory;
import com.elikill58.negativity.universal.utils.UniversalUtils;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.gson.GsonConfigurationLoader;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;

public class SpongeAdapter extends Adapter implements TranslationProviderFactory {

	private final Logger logger;
	private final SpongeNegativity plugin;
	private ConfigAdapter config;
	private final Path messagesDir;
	private final NegativityAccountManager accountManager = new SimpleAccountManager.Server(SpongeNegativity::sendPluginMessage);

	public SpongeAdapter(SpongeNegativity sn, ConfigAdapter config) {
		this.plugin = sn;
		this.logger = sn.getLogger();
		this.config = config;
		this.messagesDir = sn.getDataFolder().resolve("messages");
	}

	@Override
	public String getName() {
		return "sponge";
	}

	@Override
	public ConfigAdapter getConfig() {
		return config;
	}

	@Override
	public File getDataFolder() {
		return plugin.getDataFolder().toFile();
	}

	@Override
	public void log(String msg) {
		logger.info(msg);
	}

	@Override
	public void warn(String msg) {
		logger.warn(msg);
	}

	@Override
	public void error(String msg) {
		logger.error(msg);
	}

	@Override
	public File copy(String lang, File f) {
		return copy(lang, f.toPath()).toFile();
	}

	public Path copy(String lang, Path filePath) {
		String fileName = "en_US.yml";
		String lowercaseLang = lang.toLowerCase();
		if (lowercaseLang.contains("fr") || lowercaseLang.contains("be"))
			fileName = "fr_FR.yml";
		else if (lowercaseLang.contains("pt") || lowercaseLang.contains("br"))
			fileName = "pt_BR.yml";
		else if (lowercaseLang.contains("no"))
			fileName = "no_NO.yml";
		else if (lowercaseLang.contains("ru"))
			fileName = "ru_RU.yml";
		else if (lowercaseLang.contains("zh") || lowercaseLang.contains("cn"))
			fileName = "zh_CN.yml";
		else if (lowercaseLang.contains("de"))
			fileName = "de_DE.yml";
		else if (lowercaseLang.contains("nl"))
			fileName = "nl_NL.yml";
		else if (lowercaseLang.contains("sv"))
			fileName = "sv_SV.yml";
		else if (lang.toLowerCase().contains("es"))
			fileName = "es_ES.yml";
		else if (lang.toLowerCase().contains("vi") || lang.toLowerCase().contains("vn"))
			fileName = "vi_VN.yml";
		else if (lang.toLowerCase().contains("pl"))
			fileName = "pl_PL.yml";

		if (Files.notExists(filePath)) {
			plugin.getContainer().getAsset(fileName).ifPresent(asset -> {
				try {
					Path parentDir = filePath.normalize().getParent();
					if (parentDir != null) {
						Files.createDirectories(parentDir);
					}

					asset.copyToFile(filePath, false);
				} catch (IOException e) {
					logger.error("Failed to copy default language file " + asset.getFileName(), e);
				}
			});
		}

		return filePath;
	}

	private ConfigurationNode loadHoconFile(Path filePath) throws IOException {
		return HoconConfigurationLoader.builder().setPath(filePath).build().load();
	}

	@Override
	public TranslationProviderFactory getPlatformTranslationProviderFactory() {
		return this;
	}

	@Nullable
	@Override
	public TranslationProvider createTranslationProvider(String language) {
		String translationFile = language + ".yml";
		Path languageFile = messagesDir.resolve(translationFile);
		try {
			ConfigurationNode messagesNode = loadHoconFile(copy(language, languageFile));
			return new CachingTranslationProvider(new ConfigurateTranslationProvider(messagesNode));
		} catch (IOException e) {
			logger.error("Failed to load translation file {}.", translationFile, e);
			return null;
		}
	}

	@Nullable
	@Override
	public TranslationProvider createFallbackTranslationProvider() {
		Asset fallbackAsset = Sponge.getAssetManager().getAsset(plugin, "en_US.yml").orElse(null);
		if (fallbackAsset == null) {
			logger.warn("Could not find the fallback messages resource.");
			return null;
		}
		try {
			CommentedConfigurationNode messagesNode = HoconConfigurationLoader.builder().setURL(fallbackAsset.getUrl()).build().load();
			return new CachingTranslationProvider(new ConfigurateTranslationProvider(messagesNode));
		} catch (IOException e) {
			logger.error("Failed to load fallback translation resource.", e);
			return null;
		}
	}

	@Override
	public void reload() {
		reloadConfig();
		UniversalUtils.init();
		Cheat.loadCheat();
		plugin.reloadCommands();
		ProxyCompanionManager.updateForceDisabled(config.getBoolean("disableProxyIntegration"));
		SpongeNegativity.trySendProxyPing();
		SpongeNegativity.log = config.getBoolean("log_alerts");
		SpongeNegativity.log_console = config.getBoolean("log_alerts_in_console");
		SpongeNegativity.hasBypass = config.getBoolean("Permissions.bypass.active");
	}

	@Override
	public String getVersion() {
		return Sponge.getPlatform().getMinecraftVersion().getName();
	}

	@Override
	public void reloadConfig() {
		try {
			this.config.load();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to reload configuration", e);
		}
		plugin.loadConfig();
		plugin.loadItemBypasses();
	}

	@Nullable
	@Override
	public NegativityPlayer getNegativityPlayer(UUID playerId) {
		Player player = Sponge.getServer().getPlayer(playerId).orElse(null);
		return player != null ? SpongeNegativityPlayer.getNegativityPlayer(player) : null;
	}

	@Override
	public NegativityAccountManager getAccountManager() {
		return accountManager;
	}

	@Override
	public void alertMod(ReportType type, Object p, Cheat c, int reliability, String proof, String hover_proof) {
		SpongeNegativity.alertMod(type, (Player) p, c, reliability, hover_proof);
	}

	@Override
	public void runConsoleCommand(String cmd) {
		Sponge.getCommandManager().process(Sponge.getServer().getConsole(), cmd);
	}

	@Override
	public CompletableFuture<Boolean> isUsingMcLeaks(UUID playerId) {
		return UniversalUtils.requestMcleaksData(playerId.toString()).thenApply(response -> {
			if (response == null) {
				return false;
			}
			try {
				ConfigurationNode rootNode = GsonConfigurationLoader.builder()
						.setSource(() -> new BufferedReader(new StringReader(response)))
						.build()
						.load();
				return rootNode.getNode("isMcleaks").getBoolean(false);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		});
	}

	@Override
	public List<UUID> getOnlinePlayers() {
		List<UUID> list = new ArrayList<>();
		for(Player temp : Sponge.getServer().getOnlinePlayers())
			list.add(temp.getUniqueId());
		return list;
	}
}
