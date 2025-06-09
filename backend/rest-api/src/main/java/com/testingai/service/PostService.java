package com.testingai.service;

import com.testingai.entity.Post;
import com.testingai.repository.PostRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PostService {
  private final PostRepository postRepository;

  @Autowired
  public PostService(PostRepository postRepository) {
    this.postRepository = postRepository;
  }

  public List<Post> getAllPosts() {
    return postRepository.findAll();
  }

  public Optional<Post> getPostById(Long id) {
    return postRepository.findById(id);
  }

  public Post createPost(Post post) {
    return postRepository.save(post);
  }

  public Optional<Post> updatePost(Long id, Post postDetails) {
    return postRepository
        .findById(id)
        .map(
            post -> {
              post.setTitle(postDetails.getTitle());
              post.setContent(postDetails.getContent());
              return postRepository.save(post);
            });
  }

  public boolean deletePost(Long id) {
    return postRepository
        .findById(id)
        .map(
            post -> {
              postRepository.delete(post);
              return true;
            })
        .orElse(false);
  }
}
