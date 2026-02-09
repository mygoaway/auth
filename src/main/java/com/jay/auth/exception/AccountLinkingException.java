package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class AccountLinkingException extends BusinessException {

    private AccountLinkingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    public static AccountLinkingException alreadyLinkedToAnotherUser() {
        return new AccountLinkingException(
                "This social account is already linked to another user",
                "ACCOUNT_ALREADY_LINKED_TO_ANOTHER",
                HttpStatus.CONFLICT
        );
    }

    public static AccountLinkingException alreadyLinkedToCurrentUser() {
        return new AccountLinkingException(
                "This channel is already linked to your account",
                "CHANNEL_ALREADY_LINKED",
                HttpStatus.CONFLICT
        );
    }

    public static AccountLinkingException cannotUnlinkLastChannel() {
        return new AccountLinkingException(
                "최소 1개의 로그인 방법이 필요합니다. 마지막 채널은 해제할 수 없습니다.",
                "CANNOT_UNLINK_LAST_CHANNEL",
                HttpStatus.BAD_REQUEST
        );
    }

    public static AccountLinkingException cannotUnlinkEmailChannel() {
        return new AccountLinkingException(
                "이메일 채널은 해제할 수 없습니다.",
                "CANNOT_UNLINK_EMAIL_CHANNEL",
                HttpStatus.BAD_REQUEST
        );
    }

    public static AccountLinkingException channelNotFound() {
        return new AccountLinkingException(
                "Channel not found",
                "CHANNEL_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }

    public static AccountLinkingException emailMismatch() {
        return new AccountLinkingException(
                "Email does not match current user's email",
                "EMAIL_MISMATCH",
                HttpStatus.BAD_REQUEST
        );
    }

    public static AccountLinkingException emailAlreadyRegistered() {
        return new AccountLinkingException(
                "Email is already registered for password login",
                "EMAIL_ALREADY_REGISTERED",
                HttpStatus.CONFLICT
        );
    }
}
