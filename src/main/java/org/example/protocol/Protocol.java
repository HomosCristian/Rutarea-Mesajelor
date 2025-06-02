package org.example.protocol;

public final class Protocol {
    private Protocol() { }

    // ACTION
    public static final String ACTION_CHECK_RECIPIENT = "checkRecipient";
    public static final String ACTION_SEND_MESSAGE    = "sendMessage";

    // STATUS
    public static final String STATUS_FOUND      = "FOUND";
    public static final String STATUS_NOT_FOUND  = "NOT_FOUND";
    public static final String STATUS_DELIVERED  = "DELIVERED";
    public static final String STATUS_ROUTED     = "ROUTED";
    public static final String STATUS_ERROR      = "ERROR";

    // ERORI
    public static final String ERROR_UNKNOWN_ACTION = "UnknownAction";
    public static final String ERROR_MISSING_FIELD  = "MissingField";
    public static final String ERROR_BAD_REQUEST    = "BadRequest";
    public static final String ERROR_NO_ROUTE       = "NoRouteFound";
    public static final String ERROR_SAVE_FAILED    = "SaveFailed";
}

