package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IpRulePageResponse {

    private List<IpRuleResponse> rules;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}
