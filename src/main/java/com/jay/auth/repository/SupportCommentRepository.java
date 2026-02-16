package com.jay.auth.repository;

import com.jay.auth.domain.entity.SupportComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportCommentRepository extends JpaRepository<SupportComment, Long> {

    List<SupportComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    void deleteByPostId(Long postId);
}
