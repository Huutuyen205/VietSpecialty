package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, Integer> {

    Optional<Region> findBySlug(String slug);

    List<Region> findAllByOrderBySortOrderAsc();
}

