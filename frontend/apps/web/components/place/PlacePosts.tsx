'use client';

import { PostList } from '@/components/post/PostList';
import { usePlacePosts } from '@/lib/post-queries';

/** "Posts deste lugar" (RF14): o feed filtrado pelo lugar, por cursor. */
export function PlacePosts({ placeId }: { placeId: string }) {
  const posts = usePlacePosts(placeId);
  return (
    <section aria-labelledby="titulo-posts-lugar" className="grid gap-4">
      <h2 id="titulo-posts-lugar" className="text-xl font-bold">
        Posts deste lugar
      </h2>
      <PostList query={posts} label="Posts deste lugar" testId="posts-lugar" empty={<p className="font-semibold">Ninguém publicou sobre este lugar ainda.</p>} />
    </section>
  );
}
