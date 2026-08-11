package com.tanigawa.rewardplatform.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tanigawa.rewardplatform.exception.WalletConflictException;
import com.tanigawa.rewardplatform.reward.dto.request.RewardHistoryRequest;
import com.tanigawa.rewardplatform.reward.entity.RewardEvent;
import com.tanigawa.rewardplatform.reward.repository.RewardEventRepository;
import com.tanigawa.rewardplatform.wallet.entity.Wallet;
import com.tanigawa.rewardplatform.wallet.repository.WalletRepository;

@SpringBootTest
class IncreaseBalanceTwiceTest {
    @Autowired
    private RewardEventService rewardEventService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RewardEventRepository rewardEventRepository;

    @Test
    void concurrentClaimsShouldNotLoseUpdates() throws InterruptedException {
        Long userId = 2L;
        Long eventIdA = 2L;
        Long eventIdB = 3L;

        RewardHistoryRequest rewardHistoryRequestA = new RewardHistoryRequest(eventIdA, UUID.randomUUID());
        RewardHistoryRequest rewardHistoryRequestB = new RewardHistoryRequest(eventIdB, UUID.randomUUID());

        Wallet walletBefore = walletRepository.findByUserId(userId).orElseThrow();

        long initialBalance = walletBefore.getBalance();

        RewardEvent eventA = rewardEventRepository.findById(eventIdA).orElseThrow();
        RewardEvent eventB = rewardEventRepository.findById(eventIdB).orElseThrow();

        long rewardAmountA = eventA.getRewardAmount();
        long rewardAmountB = eventB.getRewardAmount();

        int threadCount = 2;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Runnable taskA = () -> {
            readyLatch.countDown();
            awaitStart(startLatch);
            try {
                rewardEventService.claimReward(userId, rewardHistoryRequestA);
                successCount.incrementAndGet();
            } catch (WalletConflictException e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable taskB = () -> {
            readyLatch.countDown();
            awaitStart(startLatch);
            try {
                rewardEventService.claimReward(userId, rewardHistoryRequestB);
                successCount.incrementAndGet();
            } catch (WalletConflictException e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        try {
            executor.submit(taskA);
            executor.submit(taskB);

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();  
        } finally {
            executor.shutdown();
        }

        Wallet walletAfter = walletRepository.findByUserId(userId).orElseThrow();

        long finalBalance = walletAfter.getBalance();
        long expectedBalance = initialBalance + rewardAmountA + rewardAmountB;

        if (successCount.get() == 2) {
            // both succeeded — this is fine IF the balance reflects both
            assertEquals(expectedBalance, finalBalance,
                "If both claims succeeded, balance must reflect both rewards (no lost update)");
        } else if (successCount.get() == 1 && conflictCount.get() == 1) {
            // one succeeded, one conflicted and was NOT retried — also fine
            boolean matchesA = finalBalance == initialBalance + rewardAmountA;
            boolean matchesB = finalBalance == initialBalance + rewardAmountB;
            assertTrue(matchesA || matchesB, "Balance should reflect exactly the one successful claim");
        } else {
            fail("Unexpected outcome: successCount=" + successCount.get() + ", conflictCount=" + conflictCount.get());
        }
        
    }

    private void awaitStart(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}