package studio.one.platform.ai.web.controller;

import java.util.List;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageSummaryDto;

public interface RagRetrievalPolicyUsageStore {

    RagRetrievalPolicyUsageDto save(RagRetrievalPolicyUsageDto usage);

    List<RagRetrievalPolicyUsageDto> list(String objectType, String objectId);

    RagRetrievalPolicyUsageSummaryDto summary(String objectType, String objectId);
}
