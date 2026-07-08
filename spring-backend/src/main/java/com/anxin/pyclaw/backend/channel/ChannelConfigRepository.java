package com.anxin.pyclaw.backend.channel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelConfigRepository extends JpaRepository<ChannelConfigEntity, String> {
    List<ChannelConfigEntity> findByChannelTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc(String channelType);
}
