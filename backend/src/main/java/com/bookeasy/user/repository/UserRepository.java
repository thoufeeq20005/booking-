package com.bookeasy.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("select count(u) > 0 from User u join u.roles r where r = :role")
    boolean existsWithRole(@Param("role") RoleCode role);

    long countByStatus(UserStatus status);

    long countByCreatedAtGreaterThanEqual(java.time.Instant since);

    @Query("select count(distinct u) from User u join u.roles r where r = :role")
    long countWithRole(@Param("role") RoleCode role);

    @Query(value = """
            select u from User u
            where (:q is null or lower(u.fullName) like lower(concat('%', :q, '%'))
                   or lower(u.email) like lower(concat('%', :q, '%'))
                   or u.phone like concat('%', :q, '%'))
              and (:status is null or u.status = :status)
              and (:role is null or :role member of u.roles)
            """)
    Page<User> search(@Param("q") String q, @Param("status") UserStatus status, @Param("role") RoleCode role,
                      Pageable pageable);
}
