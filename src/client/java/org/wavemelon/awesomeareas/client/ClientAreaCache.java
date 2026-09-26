package org.wavemelon.awesomeareas.client;

import org.wavemelon.awesomeareas.core.storage.AreaManager;

/**
 * Client-side mirror of the area database for map rendering and UI inspection.
 */
public class ClientAreaCache {
    private static final ClientAreaCache INSTANCE = new ClientAreaCache();

    private final AreaManager clientManager = new AreaManager();

    private ClientAreaCache() {
    }

    public static ClientAreaCache getInstance() {
        return INSTANCE;
    }

    public AreaManager getManager() {
        return clientManager;
    }

    public void updateFromJson(String jsonData) {
        clientManager.loadFromJsonString(jsonData);
    }

    public void clear() {
        clientManager.clear();
    }
}
