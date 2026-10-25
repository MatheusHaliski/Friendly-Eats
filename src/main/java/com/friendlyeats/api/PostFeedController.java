package com.friendlyeats.api;

import com.friendlyeats.application.post.PostQueryService;
import com.friendlyeats.application.post.PostViews.FeedPageView;
import com.friendlyeats.application.post.PostViews.PostDetailView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RF14: feed de posts e de alertas com paginação por cursor, a página do post, os posts sobre um lugar e o ranking
 * "em alta". Tudo público; a regra de 18+ (401 sem login, fora do feed para menor) fica no serviço.
 */
@RestController
@RequestMapping("/api/v1")
public class PostFeedController {

    private final PostQueryService queries;
    private final ViewerResolver viewer;

    public PostFeedController(PostQueryService queries, ViewerResolver viewer) {
        this.queries = queries;
        this.viewer = viewer;
    }

    @GetMapping("/posts")
    public FeedPageView feed(@RequestParam(required = false) String feed,
                             @RequestParam(required = false) String kind,
                             @RequestParam(required = false) Long establishmentId,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(required = false) Integer size) {
        return queries.feed(feed, kind, establishmentId, cursor, size, viewer.viewerId());
    }

    @GetMapping("/posts/trending")
    public FeedPageView trending(@RequestParam(required = false) String feed,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) Integer size) {
        return queries.trending(feed, category, size, viewer.viewerId());
    }

    @GetMapping("/posts/{postId}")
    public PostDetailView get(@PathVariable UUID postId) {
        return queries.getPost(postId, viewer.viewerId());
    }

    @GetMapping("/establishments/{id}/posts")
    public FeedPageView establishmentPosts(@PathVariable long id,
                                           @RequestParam(required = false) String kind,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer size) {
        return queries.establishmentFeed(id, kind, cursor, size, viewer.viewerId());
    }
}
