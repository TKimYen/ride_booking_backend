package com.ridebooking.booking.repository;

import com.ridebooking.booking.entity.Amenity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, Integer> {

	Optional<Amenity> findByCode(String code);
}
