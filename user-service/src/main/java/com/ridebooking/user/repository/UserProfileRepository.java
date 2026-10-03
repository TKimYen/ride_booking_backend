package com.ridebooking.user.repository;

import com.ridebooking.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Integer> {

    /** Tìm theo họ tên / email / SĐT, dùng cho trang Quản lý của Admin. */
    @Query("""
            SELECT p FROM UserProfile p
            WHERE LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR p.phone LIKE CONCAT('%', :keyword, '%')
            ORDER BY p.createdAt DESC
            """)
    Page<UserProfile> search(@Param("keyword") String keyword, Pageable pageable);
}