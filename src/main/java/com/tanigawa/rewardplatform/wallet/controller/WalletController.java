package com.tanigawa.rewardplatform.wallet.controller;

import com.tanigawa.rewardplatform.wallet.service.WalletService;
import com.tanigawa.rewardplatform.wallet.dto.response.WalletResponse;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wallet")
public class WalletController {
    private final WalletService walletService;

    @GetMapping
    public WalletResponse getWalletBalance(
        @AuthenticationPrincipal Long userId
    ) {
        return walletService.getWalletBalance(userId);
    }
}