package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.Province;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Integer> {

    Optional<Province> findBySlug(String slug);

    @EntityGraph(attributePaths = {"region"})
    @Query("SELECT p FROM Province p WHERE p.slug = :slug")
    Optional<Province> findBySlugWithRegion(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"region"})
    List<Province> findByRegionIdOrderByNameAsc(Integer regionId);

    @EntityGraph(attributePaths = {"region"})
    List<Province> findAllByOrderByNameAsc();
}

