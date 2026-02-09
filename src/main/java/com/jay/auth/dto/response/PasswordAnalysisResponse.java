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
public class PasswordAnalysisResponse {

    private int score;
    private String level;
    private boolean valid;
    private List<CheckItem> checks;
    private List<String> suggestions;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String name;
        private String description;
        private boolean passed;
    }
}
