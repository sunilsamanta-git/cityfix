package com.cityfix.repository;

import com.cityfix.model.Complaint;
import com.cityfix.model.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByComplaintOrderByIdAsc(Complaint complaint);
}
