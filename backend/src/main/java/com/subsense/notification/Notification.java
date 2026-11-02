package com.subsense.notification;

import java.util.List;

/** What any {@link NotificationSender} sends: a push with a title, text and a link to open. */
public record Notification(
    String title, String message, boolean urgent, String clickUrl, List<String> tags) {}
