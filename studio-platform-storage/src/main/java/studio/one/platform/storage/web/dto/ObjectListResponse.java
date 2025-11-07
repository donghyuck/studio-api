package studio.one.platform.storage.web.dto;

import java.util.List;

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
public class ObjectListResponse {
  private String bucket;
  private String prefix;
  private String delimiter;
  private List<String> commonPrefixes;
  private List<ObjectListItemDto> items;
  private String nextToken;
  private boolean truncated;
}
