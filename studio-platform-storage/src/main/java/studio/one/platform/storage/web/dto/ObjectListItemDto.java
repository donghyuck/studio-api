package studio.one.platform.storage.web.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectListItemDto {
  private String key;
  private Long size;
  private String contentType;
  private String eTag;
  private Instant lastModified;
  private boolean folder;
}
