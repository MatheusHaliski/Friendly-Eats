package com.friendlyeats.application.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Monta as URLs públicas: links compartilháveis do perfil e do post e mídia no S3/CDN. */
@Component
public class PublicLinks {

    private final String publicBaseUrl;
    private final String mediaBaseUrl;

    public PublicLinks(@Value("${friendly-eats.public-base-url}") String publicBaseUrl,
                       @Value("${friendly-eats.storage.public-url}") String mediaBaseUrl) {
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.mediaBaseUrl = stripTrailingSlash(mediaBaseUrl);
    }

    /** Link do perfil que vai para fora do app (WhatsApp, e-mail...): sem rastreamento de quem compartilhou. */
    public String profileShareUrl(long userId) {
        return publicBaseUrl + "/u/" + userId;
    }

    /** Link do post que vai para fora do app (RF12): também sem dizer quem compartilhou. */
    public String postShareUrl(java.util.UUID postId) {
        return publicBaseUrl + "/p/" + postId;
    }

    public String mediaUrl(String objectKey) {
        return objectKey == null ? null : mediaBaseUrl + "/" + objectKey;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
