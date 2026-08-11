package com.tanigawa.rewardplatform.reward.service;

import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tanigawa.rewardplatform.exception.RewardDisabledException;
import com.tanigawa.rewardplatform.exception.RewardEventNotFoundException;
import com.tanigawa.rewardplatform.exception.UserNotFoundException;
import com.tanigawa.rewardplatform.exception.WalletConflictException;
import com.tanigawa.rewardplatform.reward.dto.request.RewardEventRequest;
import com.tanigawa.rewardplatform.reward.dto.request.RewardHistoryRequest;
import com.tanigawa.rewardplatform.reward.dto.response.RewardEventResponse;
import com.tanigawa.rewardplatform.reward.dto.response.RewardHistoryResponse;
import com.tanigawa.rewardplatform.reward.entity.RewardEvent;
import com.tanigawa.rewardplatform.reward.entity.RewardHistory;
import com.tanigawa.rewardplatform.reward.entity.RewardStatus;
import com.tanigawa.rewardplatform.reward.repository.RewardEventRepository;
import com.tanigawa.rewardplatform.reward.repository.RewardHistoryRepository;
import com.tanigawa.rewardplatform.user.entity.User;
import com.tanigawa.rewardplatform.user.repository.UserRepository;
import com.tanigawa.rewardplatform.wallet.entity.Wallet;
import com.tanigawa.rewardplatform.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RewardEventService {
    private final RewardEventRepository rewardEventRepository;
    private final RewardHistoryRepository rewardHistoryRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public List<RewardEventResponse> findAllEvents() {
        return rewardEventRepository.findAll()
                .stream()
                .map(RewardEventResponse::from)
                .toList();
    }

    @Transactional
    public RewardEventResponse createEvent(RewardEventRequest request) {
        RewardEvent event = new RewardEvent(
            request.name(),
            request.description(),
            request.rewardAmount(),
            request.enabled()
        );

        RewardEvent savedEvent = rewardEventRepository.save(event);

        return RewardEventResponse.from(savedEvent);
    }

    @Transactional
    public RewardHistoryResponse claimReward(
        Long userId, RewardHistoryRequest request
    ) {
        try {
            RewardHistory existing = rewardHistoryRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);

            if (existing != null) {
                return RewardHistoryResponse.from(existing);
            }

            User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
            RewardEvent event = rewardEventRepository.findById(request.rewardEventId()).orElseThrow(() -> new RewardEventNotFoundException("없는 이벤트입니다"));

            if(!event.getEnabled()) {
                throw new RewardDisabledException("Reward event is disabled");
            }

            Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();

            RewardHistory history = RewardHistory.builder()
                            .user(user)
                            .rewardEvent(event)
                            .points(event.getRewardAmount())
                            .idempotencyKey(request.idempotencyKey())
                            .status(RewardStatus.PENDING)
                            .build();

            history.approve();

            RewardHistory savedHistory = rewardHistoryRepository.save(history);

            wallet.increaseBalance(event.getRewardAmount());

            return RewardHistoryResponse.from(savedHistory);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new WalletConflictException("지갑 잔액이 동시에 변경되어 처리하지 못했습니다. 다시 시도해주세요.");
        }
    }
}