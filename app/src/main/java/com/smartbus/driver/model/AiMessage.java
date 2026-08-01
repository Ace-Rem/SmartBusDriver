package com.smartbus.driver.model;

public class AiMessage {

    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;

    private final int type;
    private final String text;

    public AiMessage(int type, String text) {
        this.type = type;
        this.text = text;
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return type == TYPE_USER;
    }
}
