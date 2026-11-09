package com.smartbus.driver.model;

public class AiConversationMessage {
    private String role;
    private String content;

    public AiConversationMessage() { }
    public AiConversationMessage(String role, String content) { this.role = role; this.content = content; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
