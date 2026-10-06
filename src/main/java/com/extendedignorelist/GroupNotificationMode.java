package com.extendedignorelist;

public enum GroupNotificationMode
{
    NOTIFICATION_AND_CHAT("Notification + chat"),
    CHAT_ONLY("Chat only"),
    NONE("None");

    private final String label;

    GroupNotificationMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
