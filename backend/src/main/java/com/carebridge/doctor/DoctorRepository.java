package com.carebridge.doctor;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DoctorRepository
    extends JpaRepository<Doctor, UUID>, JpaSpecificationExecutor<Doctor> {
  Optional<Doctor> findByUserId(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Doctor d where d.id = :id")
  Optional<Doctor> lockById(UUID id);

  @Query(
      "select distinct d.specialization from Doctor d, UserAccount u where d.userId=u.id and"
          + " d.verified=true and u.enabled=true order by d.specialization")
  List<String> specializations();
}
