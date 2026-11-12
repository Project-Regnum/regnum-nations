package dev.sakhno.regnum.nations.api.v1;

import java.util.Objects;
import java.util.Optional;

/** A failed lookup never contains a partial or stale persistence object. */
public record ApiResult<T>(Status status, Optional<T> value) {
    public enum Status { VALID, MISSING, UNAVAILABLE, UNAUTHORIZED, INVALID }
    public ApiResult {
        Objects.requireNonNull(status); Objects.requireNonNull(value);
        if ((status == Status.VALID) != value.isPresent()) throw new IllegalArgumentException("Result/value mismatch");
    }
    public boolean valid() { return status == Status.VALID; }
    public static <T> ApiResult<T> valid(T value) { return new ApiResult<>(Status.VALID, Optional.of(value)); }
    public static <T> ApiResult<T> failure(Status status) { return new ApiResult<>(status, Optional.empty()); }
}
