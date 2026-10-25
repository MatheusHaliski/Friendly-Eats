package com.friendlyeats.api;

import com.friendlyeats.application.post.PostViews.FeedPageView;
import com.friendlyeats.application.profile.ProfileViews.ProfilePageView;
import com.friendlyeats.application.profile.ProfileViews.PublicProfileView;
import com.friendlyeats.application.profile.PublicProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF15: o feed de perfis e o destino do toque na foto ou no nome do autor (perfil público e os posts dele). */
@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final PublicProfileService profiles;
    private final ViewerResolver viewer;

    public UserProfileController(PublicProfileService profiles, ViewerResolver viewer) {
        this.profiles = profiles;
        this.viewer = viewer;
    }

    @GetMapping
    public ProfilePageView list(@RequestParam(required = false) String type,
                                @RequestParam(required = false) String city,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String cursor,
                                @RequestParam(required = false) Integer size) {
        return profiles.profiles(type, city, q, cursor, size);
    }

    @GetMapping("/{userId}/profile")
    public PublicProfileView profile(@PathVariable long userId) {
        return profiles.profile(userId, viewer.viewerId());
    }

    @GetMapping("/{userId}/posts")
    public FeedPageView posts(@PathVariable long userId,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(required = false) Integer size) {
        return profiles.posts(userId, cursor, size, viewer.viewerId());
    }
}
