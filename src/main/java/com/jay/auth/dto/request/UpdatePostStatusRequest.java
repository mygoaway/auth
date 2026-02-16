package com.jay.auth.dto.request;

import com.jay.auth.domain.enums.PostStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdatePostStatusRequest {

    @NotNull(message = "상태는 필수입니다")
    private PostStatus status;
}
