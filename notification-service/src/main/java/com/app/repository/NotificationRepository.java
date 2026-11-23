package com.app.repository;

import com.app.entity.NotificationRecord;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<NotificationRecord, String> {
    Optional<NotificationRecord> findFirstByUserIdAndRecipientIsNotNullOrderByCreatedAtDesc(String userId);
    List<NotificationRecord> findByDeliveryDateLessThanEqualAndDeliveryStatusInOrderByDeliveryDateAscCreatedAtAsc(
            LocalDate date, List<String> statuses, org.springframework.data.domain.Pageable pageable);
}
