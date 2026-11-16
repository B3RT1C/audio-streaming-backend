package net.b3rt1c.music_streaming_backend.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class RangeNotSatisfiableException extends ApiException {
    private final long resourceLength;

    public RangeNotSatisfiableException(long resourceLength) {
        super(
            HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
            ErrorCodes.RANGE_NOT_SATISFIABLE,
            "Requested range is not satisfiable"
        );
        this.resourceLength = resourceLength;
    }
}
