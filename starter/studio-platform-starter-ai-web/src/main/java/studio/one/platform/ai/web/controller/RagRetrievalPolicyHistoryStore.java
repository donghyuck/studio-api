package studio.one.platform.ai.web.controller;

import java.util.List;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyHistoryDto;

public interface RagRetrievalPolicyHistoryStore {

    RagRetrievalPolicyHistoryDto save(RagRetrievalPolicyHistoryDto history);

    List<RagRetrievalPolicyHistoryDto> list(String objectType, String objectId);
}
