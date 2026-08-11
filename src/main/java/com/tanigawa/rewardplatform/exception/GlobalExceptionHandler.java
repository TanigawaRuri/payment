package com.tanigawa.rewardplatform.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException e
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "USER_NOT_FOUND",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(RewardEventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRewardEventNotFound(
            RewardEventNotFoundException e
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "REWARD_EVENT_NOT_FOUND",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }


    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(
            WalletNotFoundException e
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "WALLET_NOT_FOUND",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }


    @ExceptionHandler(RewardDisabledException.class)
    public ResponseEntity<ErrorResponse> handleRewardDisabled(
            RewardDisabledException e
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "REWARD_DISABLED",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred",
                        LocalDateTime.now()
                ));
    }
    
    @ExceptionHandler(RewardHistoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRewardHistoryNotFound(
                RewardHistoryNotFoundException e
        ) {
                return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "REWARD_HISTORY_NOT_FOUND",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
        }

        @ExceptionHandler(WrongEmailOrPasswordException.class)
        public ResponseEntity<ErrorResponse> handleWrongEmailOrPassword(
                        WrongEmailOrPasswordException e
                ) {
                        return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(
                                "WRONG_EMAIL_OR_PASSWORD",
                                "email or password is wrong",
                                LocalDateTime.now()
                        ));
                }

        @ExceptionHandler(WalletConflictException.class)
        public ResponseEntity<ErrorResponse> handleWWalletConflict(
                        WalletConflictException e
                ) {
                        return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse(
                                "CONCURRENT_WORK",
                                "transaction is asked concurrently.",
                                LocalDateTime.now()
                        ));
                }
}