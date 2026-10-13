package dev.akinetri.osumania.game;

import net.minestom.server.entity.vehicle.PlayerInputs;

public enum InputKey {
    LEFT("key.left"),
    BACKWARD("key.back"),
    FORWARD("key.forward"),
    RIGHT("key.right"),
    JUMP("key.jump"),
    SNEAK("key.sneak"),
    SPRINT("key.sprint");

    private static final InputKey[][] LAYOUTS = {
            {},
            {JUMP},
            {LEFT, RIGHT},
            {LEFT, JUMP, RIGHT},
            {LEFT, BACKWARD, FORWARD, RIGHT},
            {LEFT, BACKWARD, JUMP, FORWARD, RIGHT},
            {SNEAK, LEFT, BACKWARD, FORWARD, RIGHT, SPRINT},
            {SNEAK, LEFT, BACKWARD, JUMP, FORWARD, RIGHT, SPRINT},
    };

    private final String keybind;

    InputKey(String keybind) {
        this.keybind = keybind;
    }

    public String keybind() {
        return keybind;
    }

    public boolean isDown(PlayerInputs inputs) {
        return switch (this) {
            case LEFT -> inputs.left();
            case BACKWARD -> inputs.backward();
            case FORWARD -> inputs.forward();
            case RIGHT -> inputs.right();
            case JUMP -> inputs.jump();
            case SNEAK -> inputs.shift();
            case SPRINT -> inputs.sprint();
        };
    }

    public static InputKey[] layout(int keys) {
        return LAYOUTS[keys];
    }
}
