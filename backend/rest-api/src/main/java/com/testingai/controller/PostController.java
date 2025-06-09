package com.testingai.controller;

import com.testingai.entity.Post;
import com.testingai.service.PostService;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PostController {
  private final PostService postService;

  @Autowired
  public PostController(PostService postService) {
    this.postService = postService;
  }

  @GetMapping
  public List<Post> getAllPosts() {
    return postService.getAllPosts();
  }

  @GetMapping("/{id}")
  public ResponseEntity<Post> getPostById(@PathVariable Long id) {
    Optional<Post> post = postService.getPostById(id);
    return post.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping
  public Post createPost(@RequestBody Post post) {
    return postService.createPost(post);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Post> updatePost(@PathVariable Long id, @RequestBody Post postDetails) {
    Optional<Post> updated = postService.updatePost(id, postDetails);
    return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePost(@PathVariable Long id) {
    boolean deleted = postService.deletePost(id);
    return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
