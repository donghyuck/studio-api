package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyDto;

public interface RagRetrievalPolicyStore {

    RagRetrievalPolicyDto save(RagRetrievalPolicyDto policy);

    Optional<RagRetrievalPolicyDto> find(String objectType, String objectId);

    List<RagRetrievalPolicyDto> list();
}
