package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSearchResponse {

    private List<AdminDashboardResponse.AdminUserInfo> users;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}
