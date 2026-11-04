package org.mobilism.material.data

object DownloadHelp {
    const val TOPIC_ID = 1489943L
    const val URL = "https://forum.mobilism.org/viewtopic.php?f=399&t=1489943"
    fun parse(html: String): DiscussionTopic = ForumReleaseParser.topic(html, TOPIC_ID)
}
