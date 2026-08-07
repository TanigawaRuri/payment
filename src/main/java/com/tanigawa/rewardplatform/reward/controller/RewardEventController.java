package com.tanigawa.rewardplatform.reward.controller;

import com.tanigawa.rewardplatform.reward.service.RewardEventService;
import com.tanigawa.rewardplatform.reward.dto.response.RewardEventResponse;
import com.tanigawa.rewardplatform.reward.dto.request.RewardEventRequest;
import com.tanigawa.rewardplatform.reward.dto.request.RewardHistoryRequest;
import com.tanigawa.rewardplatform.reward.dto.response.RewardHistoryResponse;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reward-events")
public class RewardEventController {

    private final RewardEventService rewardEventService;

    @GetMapping
    public List<RewardEventResponse> findAllEvents() {
        return rewardEventService.findAllEvents();
    }
    
    @PostMapping
    public RewardEventResponse createEvent(
        @Valid @RequestBody RewardEventRequest request
    ) {
        return rewardEventService.createEvent(request);
    }

    @PostMapping("/claims")
    public RewardHistoryResponse claimReward(
        @AuthenticationPrincipal Long userId,
        @RequestBody RewardHistoryRequest request
    ) {
        return rewardEventService.claimReward(userId, request);
    }
}