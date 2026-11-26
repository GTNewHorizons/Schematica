package com.github.lunatrius.schematica.client.util;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

final class PlayerHeadProfiles {

    static final PlayerHeadProfiles INSTANCE = new PlayerHeadProfiles(
        () -> Minecraft.getMinecraft()
            .func_152347_ac(),
        new ThreadPoolExecutor(
            0,
            1,
            1,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(32),
            new ThreadFactoryBuilder().setNameFormat("Schematica player head lookup")
                .setDaemon(true)
                .build()));

    private final Cache<UUID, CompletableFuture<VerifiedProfile>> profiles = CacheBuilder.newBuilder()
        .maximumSize(256)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();
    private final AtomicInteger revision = new AtomicInteger();
    private final Supplier<MinecraftSessionService> sessionService;
    private final Executor executor;

    PlayerHeadProfiles(Supplier<MinecraftSessionService> sessionService, Executor executor) {
        this.sessionService = sessionService;
        this.executor = executor;
    }

    int getRevision() {
        return this.revision.get();
    }

    static boolean isNameOnly(GameProfile profile) {
        return profile != null && profile.getId() == null
            && profile.getProperties()
                .isEmpty();
    }

    boolean matchesNameOnlyStock(GameProfile stock, GameProfile saved) {
        if (!isNameOnly(stock) || saved == null
            || saved.getId() == null
            || !stock.getName()
                .equalsIgnoreCase(saved.getName())) {
            return false;
        }

        try {
            final VerifiedProfile verified = this.profiles.get(saved.getId(), () -> lookup(saved.getId()))
                .getNow(null);
            return verified != null && stock.getName()
                .equalsIgnoreCase(verified.name)
                && Objects.equals(verified.skinUrl, skinUrl(this.sessionService.get(), saved, false));
        } catch (ExecutionException | RuntimeException e) {
            return false;
        }
    }

    private CompletableFuture<VerifiedProfile> lookup(UUID id) {
        final CompletableFuture<VerifiedProfile> result = new CompletableFuture<>();
        this.executor.execute(() -> {
            result.complete(resolve(id));
            this.revision.incrementAndGet();
        });
        return result;
    }

    private VerifiedProfile resolve(UUID id) {
        try {
            final MinecraftSessionService service = this.sessionService.get();
            // Authlib returns the input on failure. Only the server response can supply the account name.
            final GameProfile profile = service.fillProfileProperties(new GameProfile(id, null), true);
            if (profile != null && id.equals(profile.getId()) && profile.isComplete()) {
                return new VerifiedProfile(profile.getName(), skinUrl(service, profile, true));
            }
        } catch (RuntimeException e) {
            // Cache failures briefly to avoid repeated requests when the service is unavailable.
        }
        return null;
    }

    static String skinUrl(MinecraftSessionService service, GameProfile profile, boolean requireSecure) {
        final MinecraftProfileTexture skin = service.getTextures(profile, requireSecure)
            .get(MinecraftProfileTexture.Type.SKIN);
        return skin == null ? null : skin.getUrl();
    }

    private static final class VerifiedProfile {

        final String name;
        final String skinUrl;

        VerifiedProfile(String name, String skinUrl) {
            this.name = name;
            this.skinUrl = skinUrl;
        }
    }
}
