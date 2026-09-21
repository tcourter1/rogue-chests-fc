package com.roguechestsfc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class RogueChestsFcSyncService
{
    static final String BANNED_NAMES_KEY = "bannedNames";
    static final String IGNORED_NAMES_KEY = "ignoredNames";

    private static final String CONFIG_GROUP = "roguechestsfc";
    private static final String PARTY_SYNC_VERSION_KEY = "partySyncVersion";
    private static final String PARTY_SYNC_NONCE_KEY = "partySyncNonce";
    private static final String PARTY_SYNC_CIPHERTEXT_KEY = "partySyncCiphertext";
    private static final String PARTY_SYNC_MAC_KEY = "partySyncMac";

    private static final String PARTY_SYNC_ALGORITHM =
            "HMAC-SHA256-STREAM-v1";

    private static final String PARTY_SYNC_ROOT_KEY_BASE64 =
            "jtlLqKn/LpgI7uXh7/Y2ASkxVjs9seqWWHFv33Lp934=";

    private static final String SYNC_URL =
            "https://script.google.com/macros/s/AKfycbx89x9PgKuyctvvMdOViiXHXei9HNOFubnUle4iMgpaYGLZCYcz7h6UKfEAoNBWbauBhw/exec";

    private static final String SYNC_TOKEN =
            "k9X2mP7qW4vL1bZ8fY3hR6dN0jT5gC2x";

    private static final Duration SYNC_INTERVAL = Duration.ofMinutes(10);

    private static final Runnable NO_OP = () -> { };

    private final OkHttpClient okHttpClient;
    private final ConfigManager configManager;
    private final ClientThread clientThread;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    private volatile Runnable syncedListsChangedCallback = NO_OP;
    private volatile Runnable syncStateChangedCallback = NO_OP;
    private volatile Instant lastSync;
    private volatile String lastSyncError;
    private ScheduledExecutorService executor;

    @Inject
    public RogueChestsFcSyncService(
            OkHttpClient okHttpClient,
            ConfigManager configManager,
            ClientThread clientThread)
    {
        this.okHttpClient = okHttpClient;
        this.configManager = configManager;
        this.clientThread = clientThread;
    }

    void setCallbacks(
            Runnable syncedListsChangedCallback,
            Runnable syncStateChangedCallback)
    {
        this.syncedListsChangedCallback =
                syncedListsChangedCallback == null
                        ? NO_OP
                        : syncedListsChangedCallback;

        this.syncStateChangedCallback =
                syncStateChangedCallback == null
                        ? NO_OP
                        : syncStateChangedCallback;
    }

    void start()
    {
        if (executor != null)
        {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread thread = new Thread(r, "rogue-chests-sync");
            thread.setDaemon(true);
            return thread;
        });

        executor.scheduleWithFixedDelay(
                this::syncNow,
                2,
                SYNC_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    void stop()
    {
        ScheduledExecutorService currentExecutor = executor;
        executor = null;

        if (currentExecutor != null)
        {
            currentExecutor.shutdownNow();
        }

        syncInProgress.set(false);
        notifySyncStateChanged();
    }

    void syncNow()
    {
        ScheduledExecutorService currentExecutor = executor;

        if (currentExecutor == null
                || currentExecutor.isShutdown()
                || !syncInProgress.compareAndSet(false, true))
        {
            return;
        }

        notifySyncStateChanged();

        currentExecutor.execute(() ->
        {
            try
            {
                applySyncedLists(fetchGlobalLists());
            }
            catch (Exception exception)
            {
                lastSyncError = exception.getMessage() == null
                        ? "Sync failed"
                        : exception.getMessage();

                log.debug("Unable to sync global lists", exception);
            }
            finally
            {
                syncInProgress.set(false);
                notifySyncStateChanged();
            }
        });
    }

    Instant getLastSync()
    {
        return lastSync;
    }

    String getLastSyncError()
    {
        return lastSyncError;
    }

    boolean isSyncInProgress()
    {
        return syncInProgress.get();
    }

    String getPartyPassphrase()
    {
        String versionText = configManager.getConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_VERSION_KEY
        );

        String nonce = configManager.getConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_NONCE_KEY
        );

        String ciphertext = configManager.getConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_CIPHERTEXT_KEY
        );

        String mac = configManager.getConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_MAC_KEY
        );

        if (versionText == null
                || nonce == null
                || ciphertext == null
                || mac == null)
        {
            return null;
        }

        try
        {
            return decryptPartyCredential(
                    new SyncedPartyCredential(
                            Integer.parseInt(versionText),
                            true,
                            nonce,
                            ciphertext,
                            mac
                    )
            );
        }
        catch (GeneralSecurityException | IllegalArgumentException exception)
        {
            log.debug("Unable to decrypt synced Party passphrase", exception);
            return null;
        }
    }

    private SyncedPlayerLists fetchGlobalLists() throws Exception
    {
        HttpUrl baseUrl = HttpUrl.parse(SYNC_URL);
        if (baseUrl == null)
        {
            throw new IllegalStateException("Invalid sync endpoint");
        }

        HttpUrl requestUrl = baseUrl.newBuilder()
                .addQueryParameter("token", SYNC_TOKEN)
                .build();

        Request request = new Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IllegalStateException("HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null)
            {
                throw new IllegalStateException("Empty response");
            }

            JsonObject root = parseSyncResponse(body.string());
            if (!root.has("ok") || !root.get("ok").getAsBoolean())
            {
                String error = root.has("error")
                        ? root.get("error").getAsString()
                        : "Invalid response";

                throw new IllegalStateException(error);
            }

            if (!root.has("players") || !root.get("players").isJsonArray())
            {
                throw new IllegalStateException("Missing players array");
            }

            if (!root.has("under84Players")
                    || !root.get("under84Players").isJsonArray())
            {
                throw new IllegalStateException("Missing under84Players array");
            }

            if (!root.has("party") || !root.get("party").isJsonObject())
            {
                throw new IllegalStateException("Missing party object");
            }

            return new SyncedPlayerLists(
                    parseSyncedPlayerArray(root.getAsJsonArray("players")),
                    parseSyncedPlayerArray(root.getAsJsonArray("under84Players")),
                    parseSyncedPartyCredential(root.getAsJsonObject("party"))
            );
        }
    }

    private JsonObject parseSyncResponse(String responseText)
    {
        return new JsonParser()
                .parse(responseText)
                .getAsJsonObject();
    }

    private SyncedPartyCredential parseSyncedPartyCredential(JsonObject party)
    {
        if (!party.has("version"))
        {
            throw new IllegalStateException("Missing Party version");
        }

        int version = party.get("version").getAsInt();
        boolean available = !party.has("available")
                || party.get("available").getAsBoolean();

        String algorithm = party.has("algorithm")
                ? party.get("algorithm").getAsString()
                : PARTY_SYNC_ALGORITHM;

        if (!PARTY_SYNC_ALGORITHM.equals(algorithm))
        {
            throw new IllegalStateException("Unsupported Party sync algorithm");
        }

        if (!available)
        {
            return new SyncedPartyCredential(
                    version,
                    false,
                    null,
                    null,
                    null
            );
        }

        if (!party.has("nonce")
                || !party.has("ciphertext")
                || !party.has("mac"))
        {
            throw new IllegalStateException("Incomplete Party credential");
        }

        return new SyncedPartyCredential(
                version,
                true,
                party.get("nonce").getAsString(),
                party.get("ciphertext").getAsString(),
                party.get("mac").getAsString()
        );
    }

    private List<String> parseSyncedPlayerArray(JsonArray players)
    {
        Map<String, String> namesByNormalized = new TreeMap<>();

        for (JsonElement playerElement : players)
        {
            if (playerElement == null || playerElement.isJsonNull())
            {
                continue;
            }

            String playerName = Text.toJagexName(playerElement.getAsString());
            String normalizedName = normalizeName(playerName);

            if (!normalizedName.isEmpty())
            {
                namesByNormalized.putIfAbsent(normalizedName, playerName);
            }
        }

        return new ArrayList<>(namesByNormalized.values());
    }

    private void applySyncedLists(SyncedPlayerLists syncedLists)
            throws GeneralSecurityException
    {
        applySyncedPartyCredential(syncedLists.partyCredential);

        configManager.setConfiguration(
                CONFIG_GROUP,
                BANNED_NAMES_KEY,
                String.join("\n", syncedLists.bannedNames)
        );

        configManager.setConfiguration(
                CONFIG_GROUP,
                IGNORED_NAMES_KEY,
                String.join("\n", syncedLists.ignoredNames)
        );

        lastSync = Instant.now();
        lastSyncError = null;

        clientThread.invokeLater(() ->
        {
            syncedListsChangedCallback.run();
            return true;
        });
    }

    private void applySyncedPartyCredential(SyncedPartyCredential credential)
            throws GeneralSecurityException
    {
        if (credential == null)
        {
            throw new GeneralSecurityException("Missing Party credential");
        }

        if (!credential.available)
        {
            configManager.setConfiguration(
                    CONFIG_GROUP,
                    PARTY_SYNC_VERSION_KEY,
                    Integer.toString(credential.version)
            );

            configManager.unsetConfiguration(CONFIG_GROUP, PARTY_SYNC_NONCE_KEY);
            configManager.unsetConfiguration(CONFIG_GROUP, PARTY_SYNC_CIPHERTEXT_KEY);
            configManager.unsetConfiguration(CONFIG_GROUP, PARTY_SYNC_MAC_KEY);
            return;
        }

        String decrypted = decryptPartyCredential(credential);
        if (decrypted.isEmpty())
        {
            throw new GeneralSecurityException(
                    "Party credential decrypted to an empty value"
            );
        }

        configManager.setConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_VERSION_KEY,
                Integer.toString(credential.version)
        );

        configManager.setConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_NONCE_KEY,
                credential.nonce
        );

        configManager.setConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_CIPHERTEXT_KEY,
                credential.ciphertext
        );

        configManager.setConfiguration(
                CONFIG_GROUP,
                PARTY_SYNC_MAC_KEY,
                credential.mac
        );
    }

    private String decryptPartyCredential(SyncedPartyCredential credential)
            throws GeneralSecurityException
    {
        byte[] rootKey = null;
        byte[] encryptionKey = null;
        byte[] authenticationKey = null;
        byte[] nonce = null;
        byte[] ciphertext = null;
        byte[] plaintext = null;

        try
        {
            rootKey = Base64.getDecoder().decode(PARTY_SYNC_ROOT_KEY_BASE64);
            nonce = Base64.getDecoder().decode(credential.nonce);
            ciphertext = Base64.getDecoder().decode(credential.ciphertext);
            byte[] suppliedMac = Base64.getDecoder().decode(credential.mac);

            encryptionKey = hmacSha256(
                    rootKey,
                    "party-sync-encryption".getBytes(StandardCharsets.UTF_8)
            );

            authenticationKey = hmacSha256(
                    rootKey,
                    "party-sync-authentication".getBytes(StandardCharsets.UTF_8)
            );

            String macMessage = credential.version
                    + "|"
                    + credential.nonce
                    + "|"
                    + credential.ciphertext;

            byte[] expectedMac = hmacSha256(
                    authenticationKey,
                    macMessage.getBytes(StandardCharsets.UTF_8)
            );

            if (!MessageDigest.isEqual(expectedMac, suppliedMac))
            {
                throw new GeneralSecurityException(
                        "Party credential authentication failed"
                );
            }

            plaintext = cryptPartyBytes(ciphertext, encryptionKey, nonce);
            return new String(plaintext, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException exception)
        {
            throw new GeneralSecurityException(
                    "Invalid Party credential encoding",
                    exception
            );
        }
        finally
        {
            wipe(rootKey);
            wipe(encryptionKey);
            wipe(authenticationKey);
            wipe(nonce);
            wipe(ciphertext);
            wipe(plaintext);
        }
    }

    private byte[] cryptPartyBytes(
            byte[] input,
            byte[] encryptionKey,
            byte[] nonce)
            throws GeneralSecurityException
    {
        byte[] output = new byte[input.length];
        int offset = 0;
        int counter = 1;

        while (offset < input.length)
        {
            byte[] blockInput = new byte[nonce.length + 4];
            System.arraycopy(nonce, 0, blockInput, 0, nonce.length);

            int counterOffset = nonce.length;
            blockInput[counterOffset] = (byte) (counter >>> 24);
            blockInput[counterOffset + 1] = (byte) (counter >>> 16);
            blockInput[counterOffset + 2] = (byte) (counter >>> 8);
            blockInput[counterOffset + 3] = (byte) counter;

            byte[] keystream = hmacSha256(encryptionKey, blockInput);

            try
            {
                for (int index = 0;
                     index < keystream.length && offset < input.length;
                     index++, offset++)
                {
                    output[offset] = (byte) (input[offset] ^ keystream[index]);
                }
            }
            finally
            {
                wipe(keystream);
                wipe(blockInput);
            }

            counter++;
        }

        return output;
    }

    private byte[] hmacSha256(byte[] key, byte[] data)
            throws GeneralSecurityException
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private void notifySyncStateChanged()
    {
        clientThread.invokeLater(() ->
        {
            syncStateChangedCallback.run();
            return true;
        });
    }

    private static void wipe(byte[] value)
    {
        if (value != null)
        {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static String normalizeName(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return Text.toJagexName(
                Text.removeTags(playerName)
        ).toLowerCase(Locale.ROOT);
    }

    private static class SyncedPlayerLists
    {
        private final List<String> bannedNames;
        private final List<String> ignoredNames;
        private final SyncedPartyCredential partyCredential;

        private SyncedPlayerLists(
                List<String> bannedNames,
                List<String> ignoredNames,
                SyncedPartyCredential partyCredential)
        {
            this.bannedNames = bannedNames;
            this.ignoredNames = ignoredNames;
            this.partyCredential = partyCredential;
        }
    }

    private static class SyncedPartyCredential
    {
        private final int version;
        private final boolean available;
        private final String nonce;
        private final String ciphertext;
        private final String mac;

        private SyncedPartyCredential(
                int version,
                boolean available,
                String nonce,
                String ciphertext,
                String mac)
        {
            this.version = version;
            this.available = available;
            this.nonce = nonce;
            this.ciphertext = ciphertext;
            this.mac = mac;
        }
    }
}
