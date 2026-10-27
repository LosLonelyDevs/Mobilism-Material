package org.mobilism.material.data

data class HomeTopics(val category: DiscussionCategory, val items: List<DiscussionTopic>)

interface HomeRepository {
    suspend fun homeReleases(section: ContentSection, order: FeedOrder): List<Release>
    suspend fun homeCategories(section: ContentSection): List<ReleaseCategory>
    suspend fun homeReviews(section: ContentSection): HomeTopics?
    suspend fun homeDiscussions(section: ContentSection): HomeTopics
}

internal fun ContentSection.discussionArea() = when (this) {
    ContentSection.Apps -> DiscussionArea.Apps
    ContentSection.Games -> DiscussionArea.Games
    ContentSection.Books -> DiscussionArea.Books
}
