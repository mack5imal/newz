package app.newz.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlSanitizerTest {
    @Test
    fun removesYoutubeIframeAndGoogleImagesButKeepsPublisherImage() {
        val html =
            """
            <p>Intro</p>
            <iframe width="560" src="https://www.youtube.com/embed/abc" allowfullscreen><p>fallback</p></iframe>
            <img src="https://lh3.googleusercontent.com/photo.jpg" alt="g">
            <img src="https://cdn.publisher.example/photo.jpg" alt="kept">
            <iframe src="//www.youtube-nocookie.com/embed/x"></iframe>
            <p>Outro</p>
            """.trimIndent()
        val out = HtmlSanitizer.sanitize(html)
        assertFalse("youtube" in out, out)
        assertFalse("googleusercontent" in out, out)
        assertFalse("fallback" in out, out)
        assertTrue("""<img src="https://cdn.publisher.example/photo.jpg" alt="kept">""" in out, out)
        assertTrue("<p>Intro</p>" in out && "<p>Outro</p>" in out, out)
    }

    @Test
    fun removesEveryScript() {
        val out =
            HtmlSanitizer.sanitize(
                "<p>a</p><script src=\"https://publisher.example/x.js\"></script>" +
                    "<SCRIPT>alert(1)</SCRIPT><p>b</p><script>unterminated",
            )
        assertEquals("<p>a</p><p>b</p>unterminated", out)
    }

    @Test
    fun removesBlockedSrcsetVideoAndEmbed() {
        val out =
            HtmlSanitizer.sanitize(
                "<img srcset=\"https://cdn.publisher.example/a.jpg 1x, https://i.ytimg.com/b.jpg 2x\">" +
                    "<video poster=\"https://i.ytimg.com/p.jpg\">" +
                    "<source src=\"https://cdn.publisher.example/v.mp4\"></video>" +
                    "<video><source src=\"https://r1.googlevideo.com/v.mp4\"><source src=\"/local.mp4\"></video>" +
                    "<embed src=\"https://www.youtube.com/v/x\"/><object data=\"https://www.youtube.com/v/y\"></object>",
            )
        assertEquals("<video><source src=\"/local.mp4\"></video>", out)
    }

    @Test
    fun keepsRelativeAndPublisherMedia() {
        val html = "<img src=\"/images/a.png\"><iframe src=\"https://player.vimeo.example/1\"></iframe>"
        assertEquals(html, HtmlSanitizer.sanitize(html))
    }
}
