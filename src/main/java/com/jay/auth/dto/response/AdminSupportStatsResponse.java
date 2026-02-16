package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSupportStatsResponse {

    private long totalPosts;
    private long openPosts;
    private long inProgressPosts;
    private long resolvedPosts;
    private long closedPosts;
    private long todayPosts;
}
