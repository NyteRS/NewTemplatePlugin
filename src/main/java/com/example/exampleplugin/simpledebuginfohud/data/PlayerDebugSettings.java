package com.example.exampleplugin.simpledebuginfohud.data;

public class PlayerDebugSettings {
    private boolean debugEnabled = false;
    private boolean hudInitialized = false;

    public PlayerDebugSettings() {
    }

    public boolean isDebugEnabled() {
        return this.debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public boolean isHudInitialized() {
        return this.hudInitialized;
    }

    public void setHudInitialized(boolean hudInitialized) {
        this.hudInitialized = hudInitialized;
    }
}
