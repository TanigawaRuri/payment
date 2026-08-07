package com.tanigawa.rewardplatform.reward.controller;

import com.tanigawa.rewardplatform.reward.dto.response.RewardHistoryResponse;
import com.tanigawa.rewardplatform.reward.service.RewardHistoryService;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import lombok.RequiredArgsConstructor;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reward-histories")    
public class RewardHistoryController {
    
    private final RewardHistoryService rewardHistoryService;

    @GetMapping("/{id}")
    public RewardHistoryResponse getHistory(
        @PathVariable Long id
    ) {
        return rewardHistoryService.getHistory(id);
    }

    @GetMapping("/users")
    public List<RewardHistoryResponse> getUserHistories(
        @AuthenticationPrincipal Long userId
    ) {
        return rewardHistoryService.getUserHistories(userId);
    }
}