package com.schwab.assessment.notification.repository;

import com.schwab.assessment.notification.model.RecipientPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientPreferenceRepository extends JpaRepository<RecipientPreferenceEntity, String> {
}
