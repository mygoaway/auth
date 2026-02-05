package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.request.UpdatePhoneRequest;
import com.jay.auth.dto.request.UpdateProfileRequest;
import com.jay.auth.dto.request.UpdateRecoveryEmailRequest;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private EncryptionService encryptionService;

    @Nested
    @DisplayName("프로필 조회")
    class GetProfile {

        @Test
        @DisplayName("프로필 조회가 성공해야 한다")
        void getProfileSuccess() {
            // given
            User user = createUserWithChannels();
            given(userRepository.findByIdWithChannels(1L)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@email.com");
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");
            given(encryptionService.decryptEmail("enc_ch_email")).willReturn("test@email.com");

            // when
            UserProfileResponse response = userService.getProfile(1L);

            // then
            assertThat(response.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(response.getEmail()).isEqualTo("test@email.com");
            assertThat(response.getNickname()).isEqualTo("테스트");
            assertThat(response.getStatus()).isEqualTo("ACTIVE");
            assertThat(response.getChannels()).hasSize(1);
            assertThat(response.getChannels().get(0).getChannelCode()).isEqualTo("EMAIL");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 실패해야 한다")
        void getProfileFailsWithUserNotFound() {
            // given
            given(userRepository.findByIdWithChannels(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getProfile(999L))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("닉네임 변경")
    class UpdateNickname {

        @Test
        @DisplayName("닉네임 변경이 성공해야 한다")
        void updateNicknameSuccess() {
            // given
            User user = createUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(encryptionService.encryptNickname("새닉네임")).willReturn("enc_new_nickname");

            UpdateProfileRequest request = new UpdateProfileRequest();
            setField(request, "nickname", "새닉네임");

            // when
            userService.updateNickname(1L, request);

            // then
            verify(encryptionService).encryptNickname("새닉네임");
            assertThat(user.getNicknameEnc()).isEqualTo("enc_new_nickname");
        }
    }

    @Nested
    @DisplayName("핸드폰 번호 변경")
    class UpdatePhone {

        @Test
        @DisplayName("핸드폰 번호 변경이 성공해야 한다")
        void updatePhoneSuccess() {
            // given
            User user = createUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(encryptionService.encryptPhone("010-1234-5678")).willReturn("enc_phone");

            UpdatePhoneRequest request = new UpdatePhoneRequest();
            setField(request, "phone", "010-1234-5678");

            // when
            userService.updatePhone(1L, request);

            // then
            verify(encryptionService).encryptPhone("010-1234-5678");
            assertThat(user.getPhoneEnc()).isEqualTo("enc_phone");
        }
    }

    @Nested
    @DisplayName("복구 이메일 변경")
    class UpdateRecoveryEmail {

        @Test
        @DisplayName("복구 이메일 변경이 성공해야 한다")
        void updateRecoveryEmailSuccess() {
            // given
            User user = createUser(1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(encryptionService.encryptEmail("recovery@email.com"))
                    .willReturn(new EncryptionService.EncryptedEmail("enc_recovery", "enc_recovery_lower"));

            UpdateRecoveryEmailRequest request = new UpdateRecoveryEmailRequest();
            setField(request, "recoveryEmail", "recovery@email.com");

            // when
            userService.updateRecoveryEmail(1L, request);

            // then
            verify(encryptionService).encryptEmail("recovery@email.com");
            assertThat(user.getRecoveryEmailEnc()).isEqualTo("enc_recovery");
            assertThat(user.getRecoveryEmailLowerEnc()).isEqualTo("enc_recovery_lower");
        }
    }

    // Helper methods
    private User createUser(Long id) {
        User user = User.builder()
                .emailEnc("enc_email")
                .emailLowerEnc("enc_email_lower")
                .nicknameEnc("enc_nickname")
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", id);
        setField(user, "userUuid", "uuid-1234");
        return user;
    }

    private User createUserWithChannels() {
        User user = createUser(1L);
        UserChannel channel = UserChannel.builder()
                .user(user)
                .channelCode(ChannelCode.EMAIL)
                .channelKey("1")
                .channelEmailEnc("enc_ch_email")
                .channelEmailLowerEnc("enc_ch_email_lower")
                .build();
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
