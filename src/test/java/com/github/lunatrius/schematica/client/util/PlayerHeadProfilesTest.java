package com.github.lunatrius.schematica.client.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.junit.Test;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;

public class PlayerHeadProfilesTest {

    private static final UUID OWNER = new UUID(0, 1);
    private static final GameProfile STOCK = new GameProfile(null, "builder");

    @Test
    public void acceptsVerifiedStockOnlyForTheSameSkin() {
        final GameProfile current = profile(OWNER, "Builder", "current");
        final GameProfile saved = profile(OWNER, "Builder", "current");
        final GameProfile custom = profile(OWNER, "Builder", "custom");
        final Queue<Runnable> pending = new ArrayDeque<>();
        final PlayerHeadProfiles profiles = new PlayerHeadProfiles(() -> new SessionService(current), pending::add);

        assertFalse(profiles.matchesNameOnlyStock(STOCK, saved));
        assertFalse(profiles.matchesNameOnlyStock(STOCK, custom));
        assertEquals(1, pending.size());
        pending.remove()
            .run();

        assertEquals(1, profiles.getRevision());
        assertTrue(profiles.matchesNameOnlyStock(STOCK, saved));
        assertFalse(profiles.matchesNameOnlyStock(STOCK, custom));
        assertFalse(profiles.matchesNameOnlyStock(STOCK, new GameProfile(OWNER, "Builder")));
        assertTrue(pending.isEmpty());
    }

    @Test
    public void rejectsUnresolvedAndDifferentOwners() {
        final GameProfile saved = new GameProfile(OWNER, "Builder");
        final GameProfile[] responses = { null, new GameProfile(OWNER, "Renamed"),
            new GameProfile(new UUID(0, 2), "Builder") };
        for (GameProfile response : responses) {
            final PlayerHeadProfiles profiles = new PlayerHeadProfiles(
                () -> new SessionService(response),
                Runnable::run);
            assertFalse(profiles.matchesNameOnlyStock(STOCK, saved));
        }
        final PlayerHeadProfiles profiles = new PlayerHeadProfiles(() -> new SessionService(saved), Runnable::run);
        assertTrue(profiles.matchesNameOnlyStock(STOCK, saved));
        assertFalse(profiles.matchesNameOnlyStock(profile(null, "Builder", "custom"), saved));
    }

    private static GameProfile profile(UUID id, String name, String texture) {
        final GameProfile profile = new GameProfile(id, name);
        final String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + texture
            + "\"}}}";
        final String value = Base64.getEncoder()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        profile.getProperties()
            .put("textures", new Property("textures", value));
        return profile;
    }

    private static final class SessionService extends YggdrasilMinecraftSessionService {

        private final GameProfile response;

        SessionService(GameProfile response) {
            super(new YggdrasilAuthenticationService(Proxy.NO_PROXY, "test"));
            this.response = response;
        }

        @Override
        public GameProfile fillProfileProperties(GameProfile request, boolean requireSecure) {
            assertEquals(OWNER, request.getId());
            assertNull(request.getName());
            assertTrue(requireSecure);
            return this.response == null ? request : this.response;
        }

        @Override
        public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(GameProfile profile,
            boolean requireSecure) {
            // The fixture's texture payloads have no Mojang signature.
            return super.getTextures(profile, false);
        }
    }
}
