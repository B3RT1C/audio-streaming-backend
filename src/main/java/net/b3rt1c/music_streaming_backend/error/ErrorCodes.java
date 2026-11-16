package net.b3rt1c.music_streaming_backend.error;

public final class ErrorCodes {
    public static final String FILE_REQUIRED = "FILE_REQUIRED";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String INVALID_NAME = "INVALID_NAME";
    public static final String INVALID_FILE = "INVALID_FILE";
    public static final String INVALID_BODY = "INVALID_BODY";
    public static final String INVALID_ORDER = "INVALID_ORDER";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String INTERNAL = "INTERNAL";
    public static final String IMPORT_NOT_CONFIGURED = "IMPORT_NOT_CONFIGURED";
    public static final String PATH_NOT_ALLOWED = "PATH_NOT_ALLOWED";
    public static final String RANGE_NOT_SATISFIABLE = "RANGE_NOT_SATISFIABLE";

    private ErrorCodes() {
    }
}
