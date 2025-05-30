package com.grishin.apartment.checker.storage;


import com.grishin.apartment.checker.storage.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityRepository extends JpaRepository<Community, String> {
}
