package com.jay.auth.dto.request;

import com.jay.auth.domain.enums.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateSupportPostRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    @NotNull(message = "카테고리는 필수입니다")
    private PostCategory category;

    @JsonProperty("isPrivate")
    private boolean privatePost;

    public boolean isPrivate() {
        return privatePost;
    }
}
