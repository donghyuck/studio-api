package studio.echo.base.user.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class BatchResult {
    long requested;
    long inserted;
    long skipped;
    long deleted;
}
