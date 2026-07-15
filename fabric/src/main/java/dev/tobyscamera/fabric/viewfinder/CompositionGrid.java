package dev.tobyscamera.fabric.viewfinder;

public enum CompositionGrid {
    NONE,
    THIRDS,
    CROSSHAIR;

    public CompositionGrid next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
