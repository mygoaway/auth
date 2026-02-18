package com.jay.auth.repository;

import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserChannelRepository extends JpaRepository<UserChannel, Long> {

    Optional<UserChannel> findByChannelCodeAndChannelKey(ChannelCode channelCode, String channelKey);

    boolean existsByChannelCodeAndChannelKey(ChannelCode channelCode, String channelKey);

    List<UserChannel> findByUserId(Long userId);

    List<UserChannel> findByUserIdAndChannelCode(Long userId, ChannelCode channelCode);

    @Query("SELECT c FROM UserChannel c JOIN FETCH c.user WHERE c.channelCode = :channelCode AND c.channelKey = :channelKey")
    Optional<UserChannel> findByChannelCodeAndChannelKeyWithUser(@Param("channelCode") ChannelCode channelCode,
                                                                  @Param("channelKey") String channelKey);

    @Query("SELECT c FROM UserChannel c JOIN FETCH c.user u WHERE c.channelCode = :channelCode AND c.channelKey = :channelKey AND u.status = 'ACTIVE'")
    Optional<UserChannel> findActiveByChannelCodeAndChannelKey(@Param("channelCode") ChannelCode channelCode,
                                                                @Param("channelKey") String channelKey);

    void deleteByUserIdAndChannelCode(Long userId, ChannelCode channelCode);

    long countByUserId(Long userId);

    long countByUserIdAndChannelCodeNot(Long userId, ChannelCode channelCode);

    boolean existsByChannelEmailLowerEncAndChannelCodeNot(String channelEmailLowerEnc, ChannelCode channelCode);
}
