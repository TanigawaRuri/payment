package com.tanigawa.rewardplatform.reward.repository;

import com.tanigawa.rewardplatform.reward.entity.RewardEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RewardEventRepository extends JpaRepository<RewardEvent, Long> {
    Optional<RewardEvent> findByName(String name);
}
