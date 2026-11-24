package org.mobilism.material.data

data class HomeTopics(val category: DiscussionCategory, val items: List<DiscussionTopic>)

data class ReleaseFeedPolicy(val ttlMs: Long, val generation: Int = 0, val expireHome: Boolean = true,
    val expireOnReturn: Boolean = false)

interface ReleaseFeedPolicies {
    fun feedPolicy(order: FeedOrder): ReleaseFeedPolicy = if (order == FeedOrder.Latest)
        ReleaseFeedPolicy(ApiRequestLimits.Defaults.releaseLatestCacheTtlMs)
    else ReleaseFeedPolicy(ApiRequestLimits.Defaults.releaseTopCacheTtlMs, expireHome = false)
}

interface HomeRepository : ReleaseFeedPolicies {
    suspend fun homeReleases(section: ContentSection, order: FeedOrder, fresh: Boolean = false): List<Release>
    suspend fun homeReleasePage(section: ContentSection, order: FeedOrder, fresh: Boolean) =
        ReleasePage(homeReleases(section, order, fresh), canLoadMore = false)
    suspend fun homeCategories(section: ContentSection): List<ReleaseCategory>
    suspend fun homeReviews(section: ContentSection): HomeTopics?
    suspend fun homeDiscussions(section: ContentSection): HomeTopics
}

internal fun ContentSection.discussionArea() = when (this) {
    ContentSection.Apps -> DiscussionArea.Apps
    ContentSection.Games -> DiscussionArea.Games
    ContentSection.Books -> DiscussionArea.Books
}
